package org.oxoo2a.sim4da.dsm;

import org.oxoo2a.sim4da.Message;
import org.oxoo2a.sim4da.Network;
import org.oxoo2a.sim4da.Node;
import org.oxoo2a.sim4da.Simulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CP (Consistency & Partition Tolerance) implementation of DSM.
 * 
 * Uses a quorum-based approach where operations require acknowledgment from
 * a majority of nodes. Operations will block or fail if a quorum cannot be reached.
 * This approach prioritizes consistency over availability.
 */
public class CPDistributedSharedMemory implements DSM {
    private final Map<String, String> localStore = new ConcurrentHashMap<>();
    private String nodeName;
    private final Node node;
    private Logger logger;
    private final int timeoutMs = 5000; // Increased from 1000ms to 5000ms
    
    // For tracking responses to quorum requests
    private final Map<String, QuorumState> pendingQuorums = new ConcurrentHashMap<>();
    private final AtomicInteger requestIdCounter = new AtomicInteger(0);
    
    public CPDistributedSharedMemory(Node node) {
        this.node = node;
    }
    
    @Override
    public void initialize(String nodeName) {
        this.nodeName = nodeName;
        this.logger = LoggerFactory.getLogger(nodeName + "-CP-DSM");
        logger.info("CP DSM initialized for node {}", nodeName);
    }
    
    @Override
    public void write(String key, String value) throws DSMException {
        int totalNodes = Network.getInstance().numberOfNodes();
        int requiredQuorum = (totalNodes / 2) + 1; // Majority quorum
        
        String requestId = nodeName + "-write-" + requestIdCounter.incrementAndGet();
        QuorumState quorumState = new QuorumState(requiredQuorum);
        pendingQuorums.put(requestId, quorumState);
        
        logger.debug("Node {} attempting to write {}={} (request: {}, quorum needed: {})", 
                nodeName, key, value, requestId, requiredQuorum);
        
        try {
            // First, update local copy immediately
            localStore.put(key, value);
            
            // Then broadcast to other nodes
            Message writeRequestMsg = new Message()
                    .add("type", "DSM_CP_WRITE_REQUEST")
                    .add("requestId", requestId)
                    .add("key", key)
                    .add("value", value);
            
            broadcastMessage(writeRequestMsg);
            
            // Local node participates in quorum
            processWriteAck(requestId, nodeName);
            
            // Wait for quorum with better handling of concurrent requests
            boolean quorumReached = quorumState.latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            
            // Reduce the chance of quorum failures
            if (!quorumReached) {
                // Make a second attempt with a shorter timeout
                logger.debug("First quorum attempt failed, retrying with 1000ms timeout");
                quorumReached = quorumState.latch.await(1000, TimeUnit.MILLISECONDS);
            }
            
            if (!quorumReached) {
                int responsesReceived = quorumState.responses.get();
                throw new DSMException(String.format(
                        "Failed to reach write quorum for key %s (got %d of %d required responses)",
                        key, responsesReceived, requiredQuorum));
            }
            
            if (quorumState.error.get() != null) {
                throw new DSMException("Error during write quorum: " + quorumState.error.get());
            }
            
            logger.debug("Node {} successfully wrote {}={} with quorum", nodeName, key, value);
            
        } catch (DSMException e) {
            throw e;
        } catch (Exception e) {
            throw new DSMException("Error during write operation: " + e.getMessage(), e);
        } finally {
            pendingQuorums.remove(requestId);
        }
    }
    
    @Override
    public String read(String key) throws DSMException {
        int totalNodes = Network.getInstance().numberOfNodes();
        int requiredQuorum = (totalNodes / 2) + 1; // Majority quorum
        
        String requestId = nodeName + "-read-" + requestIdCounter.incrementAndGet();
        QuorumState quorumState = new QuorumState(requiredQuorum);
        pendingQuorums.put(requestId, quorumState);
        
        logger.debug("Node {} attempting to read {} (request: {}, quorum needed: {})", 
                nodeName, key, requestId, requiredQuorum);
        
        try {
            // First, query all nodes for their values
            Message readRequestMsg = new Message()
                    .add("type", "DSM_CP_READ_REQUEST")
                    .add("requestId", requestId)
                    .add("key", key);
            
            // Use a retry mechanism for broadcast
            int maxRetries = 3;
            for (int i = 0; i < maxRetries; i++) {
                try {
                    broadcastMessage(readRequestMsg);
                    break; // Success
                } catch (Exception e) {
                    logger.warn("Retry {}/{}: Error broadcasting read request: {}", 
                            i+1, maxRetries, e.getMessage());
                    if (i < maxRetries - 1) {
                        Thread.sleep(50); // Short delay before retry
                    }
                }
            }
            
            // Local node also responds
            String localValue = localStore.get(key);
            processReadResponse(requestId, nodeName, key, localValue);
            
            // Wait for quorum with periodic logging of progress
            long startTime = System.currentTimeMillis();
            long deadline = startTime + timeoutMs;
            boolean quorumReached = false;
            
            while (System.currentTimeMillis() < deadline && !quorumReached) {
                quorumReached = quorumState.latch.await(500, TimeUnit.MILLISECONDS);
                
                if (!quorumReached) {
                    // Log progress if still waiting
                    int currentResponses = quorumState.responses.get();
                    if (currentResponses > 0) {
                        logger.debug("Still waiting for quorum: have {} of {} required responses for request {}", 
                                currentResponses, requiredQuorum, requestId);
                    }
                }
            }
            
            if (!quorumReached) {
                int responsesReceived = quorumState.responses.get();
                throw new DSMException(String.format(
                        "Failed to reach read quorum for key %s (got %d of %d required responses)",
                        key, responsesReceived, requiredQuorum));
            }
            
            if (quorumState.error.get() != null) {
                throw new DSMException("Error during read quorum: " + quorumState.error.get());
            }
            
            String result = quorumState.value.get();
            logger.debug("Node {} successfully read {}={} with quorum", nodeName, key, result);
            return result;
            
        } catch (DSMException e) {
            throw e;
        } catch (Exception e) {
            throw new DSMException("Error during read operation: " + e.getMessage(), e);
        } finally {
            pendingQuorums.remove(requestId);
        }
    }
    
    @Override
    public void shutdown() {
        localStore.clear();
        pendingQuorums.clear();
        logger.info("CP DSM shut down for node {}", nodeName);
    }
    
    /**
     * Process a write request message from another node
     */
    public void processWriteRequest(Message message) {
        String requestId = message.query("requestId");
        String key = message.query("key");
        String value = message.query("value");
        String sender = message.queryHeader("sender");
        
        try {
            // Update local store
            localStore.put(key, value);
            logger.debug("Node {} processing write request for {}={} from {} (concurrent requests: {})", 
                    nodeName, key, value, sender, pendingQuorums.size());
            
            // If this node is busy with its own quorum operations, there might be delays
            if (pendingQuorums.size() > 1) {
                logger.warn("Node {} is busy with {} pending quorums while handling write request from {}", 
                        nodeName, pendingQuorums.size(), sender);
            }
            
            // Send acknowledgment
            Message ackMsg = new Message()
                    .add("type", "DSM_CP_WRITE_ACK")
                    .add("requestId", requestId);
            
            sendMessage(ackMsg, sender);
            
        } catch (Exception e) {
            logger.warn("Error processing write request: {}", e.getMessage());
            // In case of error, send negative acknowledgment
            Message nackMsg = new Message()
                    .add("type", "DSM_CP_WRITE_NACK")
                    .add("requestId", requestId)
                    .add("error", e.getMessage());
            
            sendMessage(nackMsg, sender);
        }
    }
    
    /**
     * Process a write acknowledgment from another node
     */
    public void processWriteAck(String requestId, String sender) {
        QuorumState state = pendingQuorums.get(requestId);
        if (state != null) {
            logger.debug("Node {} received write ACK for request {} from {}", nodeName, requestId, sender);
            int current = state.acknowledgeResponse();
            logger.debug("Request {} has {} responses out of {} required for quorum", 
                    requestId, current, state.requiredQuorum);
        }
    }
    
    /**
     * Process a write negative acknowledgment from another node
     */
    public void processWriteNack(Message message) {
        String requestId = message.query("requestId");
        String error = message.query("error");
        String sender = message.queryHeader("sender");
        
        QuorumState state = pendingQuorums.get(requestId);
        if (state != null) {
            logger.warn("Node {} received write NACK for request {} from {}: {}", 
                    nodeName, requestId, sender, error);
            state.error.set(error);
            state.latch.countDown(); // Allow early completion with error
        }
    }
    
    /**
     * Process a read request message from another node
     */
    public void processReadRequest(Message message) {
        String requestId = message.query("requestId");
        String key = message.query("key");
        String sender = message.queryHeader("sender");
        
        try {
            // Read from local store
            String value = localStore.get(key);
            logger.debug("Node {} processing read request for {} (value: {}) from {} (concurrent requests: {})", 
                    nodeName, key, value, sender, pendingQuorums.size());
            
            // If this node is busy with its own quorum operations, there might be delays
            if (pendingQuorums.size() > 1) {
                logger.warn("Node {} is busy with {} pending quorums while handling read request from {}", 
                        nodeName, pendingQuorums.size(), sender);
            }
            
            // Send response
            Message responseMsg = new Message()
                    .add("type", "DSM_CP_READ_RESPONSE")
                    .add("requestId", requestId)
                    .add("key", key)
                    .add("value", value != null ? value : "");
            
            sendMessage(responseMsg, sender);
            
        } catch (Exception e) {
            logger.warn("Error processing read request: {}", e.getMessage());
            // In case of error, send error response
            Message errorMsg = new Message()
                    .add("type", "DSM_CP_READ_ERROR")
                    .add("requestId", requestId)
                    .add("error", e.getMessage());
            
            sendMessage(errorMsg, sender);
        }
    }
    
    /**
     * Process a read response from another node
     */
    public void processReadResponse(String requestId, String sender, String key, String value) {
        QuorumState state = pendingQuorums.get(requestId);
        if (state != null) {
            logger.debug("Node {} received read response for {} (value: {}) from {}", 
                    nodeName, key, value, sender);
            
            // In a more advanced implementation, we could handle conflicting values
            // by using versioning, but for simplicity we just take any non-null value
            if (value != null) {
                state.value.set(value);
            }
            
            int current = state.acknowledgeResponse();
            logger.debug("Request {} has {} responses out of {} required for quorum", 
                    requestId, current, state.requiredQuorum);
        }
    }
    
    /**
     * Process a read error response from another node
     */
    public void processReadError(Message message) {
        String requestId = message.query("requestId");
        String error = message.query("error");
        String sender = message.queryHeader("sender");
        
        QuorumState state = pendingQuorums.get(requestId);
        if (state != null) {
            logger.warn("Node {} received read ERROR for request {} from {}: {}", 
                    nodeName, requestId, sender, error);
            state.error.set(error);
            state.latch.countDown(); // Allow early completion with error
        }
    }
    
    /**
     * Broadcasts a message to all nodes via the associated Node object.
     * This method delegates to the appropriate broadcasting method in Node.
     */
    private void broadcastMessage(Message message) {
        try {
            ((DSMNode)node).sendDSMBroadcast(message);
        } catch (Exception e) {
            logger.warn("Error during broadcast: {}", e.getMessage());
            throw new RuntimeException("Broadcast failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Sends a message to a specific node via the associated Node object.
     * This method delegates to the appropriate sending method in Node.
     */
    private void sendMessage(Message message, String toNodeName) {
        try {
            ((DSMNode)node).sendDSMMessage(message, toNodeName);
        } catch (Exception e) {
            logger.warn("Error sending message to {}: {}", toNodeName, e.getMessage());
        }
    }
    
    /**
     * Tracks the state of a quorum operation
     */
    private static class QuorumState {
        final CountDownLatch latch;
        final AtomicInteger responses = new AtomicInteger(0);
        final AtomicReference<String> value = new AtomicReference<>();
        final AtomicReference<String> error = new AtomicReference<>();
        final int requiredQuorum;
        
        QuorumState(int requiredQuorum) {
            this.requiredQuorum = requiredQuorum;
            this.latch = new CountDownLatch(1); // We count down once when the quorum is reached
        }
        
        /**
         * Acknowledges a response and returns the current number of responses.
         * If the required quorum is reached, it will release the latch.
         * 
         * @return the current number of responses
         */
        int acknowledgeResponse() {
            int current = responses.incrementAndGet();
            if (current >= requiredQuorum) {
                latch.countDown(); // Release the latch when quorum is reached
            }
            return current;
        }
    }
}
