package org.oxoo2a.sim4da.dsm;

import org.oxoo2a.sim4da.Message;
import org.oxoo2a.sim4da.Network;
import org.oxoo2a.sim4da.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CA (Consistency & Availability) implementation of DSM.
 * 
 * Uses a centralized coordinator approach where all operations are forwarded to a central
 * coordinator node that manages the authoritative copy of the data. All operations are
 * synchronous and block until completed.
 * 
 * This approach provides strong consistency and availability but doesn't handle partitions.
 */
public class CADistributedSharedMemory implements DSM {
    private final ConcurrentHashMap<String, String> localStore = new ConcurrentHashMap<>();
    private String nodeName;
    private final Node node;
    private Logger logger;
    
    // The name of the coordinator node (using Node_0 as coordinator)
    private static final String COORDINATOR_NODE = "Node_0";
    
    // For tracking responses to operations
    private final ConcurrentHashMap<String, OperationState> pendingOperations = new ConcurrentHashMap<>();
    private final AtomicInteger operationIdCounter = new AtomicInteger(0);
    
    // Timeout for operations (ms)
    private static final int OPERATION_TIMEOUT = 5000; // Increased from 2000ms to 5000ms
    
    // Add a throttling mechanism for coordinator
    private static final Object coordinatorLock = new Object();
    private static final long COORDINATOR_PROCESSING_TIME_MS = 10; // Small delay for coordinator processing
    
    public CADistributedSharedMemory(Node node) {
        this.node = node;
    }
    
    @Override
    public void initialize(String nodeName) {
        this.nodeName = nodeName;
        this.logger = LoggerFactory.getLogger(nodeName + "-CA-DSM");
        logger.info("CA DSM initialized for node {}", nodeName);
    }
    
    @Override
    public void write(String key, String value) throws DSMException {
        if (isCoordinator()) {
            // Coordinator writes directly to local store
            localStore.put(key, value);
            logger.debug("Coordinator wrote {}={} to central store", key, value);
            
            // Notify all nodes of the update
            Message updateMsg = new Message()
                    .add("type", "DSM_CA_UPDATE")
                    .add("key", key)
                    .add("value", value);
            
            broadcastMessage(updateMsg);
            return;
        }
        
        // Non-coordinator nodes need to send write request to coordinator
        String operationId = nodeName + "-write-" + operationIdCounter.incrementAndGet();
        OperationState opState = new OperationState();
        pendingOperations.put(operationId, opState);
        
        try {
            logger.debug("Node {} sending write request for {}={} to coordinator", nodeName, key, value);
            
            Message writeRequestMsg = new Message()
                    .add("type", "DSM_CA_WRITE_REQUEST")
                    .add("operationId", operationId)
                    .add("key", key)
                    .add("value", value);
            
            sendMessage(writeRequestMsg, COORDINATOR_NODE);
            
            // Wait for acknowledgment from coordinator
            boolean completed = opState.latch.await(OPERATION_TIMEOUT, TimeUnit.MILLISECONDS);
            
            if (!completed) {
                throw new DSMException("Write operation timed out for key " + key);
            }
            
            if (opState.error.get() != null) {
                throw new DSMException("Write operation failed: " + opState.error.get());
            }
            
            // Update local cache upon successful write
            localStore.put(key, value);
            logger.debug("Node {} write operation for {}={} completed successfully", nodeName, key, value);
            
        } catch (DSMException e) {
            throw e;
        } catch (Exception e) {
            throw new DSMException("Error during write operation: " + e.getMessage(), e);
        } finally {
            pendingOperations.remove(operationId);
        }
    }
    
    @Override
    public String read(String key) throws DSMException {
        if (isCoordinator()) {
            // Coordinator reads directly from its store
            String value = localStore.get(key);
            logger.debug("Coordinator read {}={} from central store", key, value);
            return value;
        }
        
        // First check local cache
        String cachedValue = localStore.get(key);
        if (cachedValue != null) {
            logger.debug("Node {} read {}={} from local cache", nodeName, key, cachedValue);
            return cachedValue;
        }
        
        // If not in cache, request from coordinator
        String operationId = nodeName + "-read-" + operationIdCounter.incrementAndGet();
        OperationState opState = new OperationState();
        pendingOperations.put(operationId, opState);
        
        try {
            logger.debug("Node {} sending read request for {} to coordinator", nodeName, key);
            
            Message readRequestMsg = new Message()
                    .add("type", "DSM_CA_READ_REQUEST")
                    .add("operationId", operationId)
                    .add("key", key);
            
            // Send with retry logic
            boolean sent = false;
            int maxRetries = 3;
            for (int i = 0; i < maxRetries && !sent; i++) {
                try {
                    sendMessage(readRequestMsg, COORDINATOR_NODE);
                    sent = true;
                } catch (Exception e) {
                    logger.warn("Retry {}/{}: Error sending read request to coordinator: {}", 
                            i+1, maxRetries, e.getMessage());
                    if (i < maxRetries - 1) {
                        Thread.sleep(50); // Short delay before retry
                    }
                }
            }
            
            if (!sent) {
                logger.info("CAP THEOREM INSIGHT: CA system cannot reach coordinator - this demonstrates why CA systems cannot be partition tolerant");
                throw new DSMException("Failed to send read request to coordinator after multiple attempts");
            }
            
            // Wait for response from coordinator
            boolean completed = opState.latch.await(OPERATION_TIMEOUT, TimeUnit.MILLISECONDS);
            
            if (!completed) {
                logger.info("CAP THEOREM INSIGHT: Read timed out waiting for coordinator - in a network partition, CA systems cannot maintain both consistency and availability");
                throw new DSMException("Read operation timed out for key " + key);
            }
            
            if (opState.error.get() != null) {
                throw new DSMException("Read operation failed: " + opState.error.get());
            }
            
            String value = opState.value.get();
            if (value != null) {
                // Update local cache
                localStore.put(key, value);
            }
            
            logger.debug("Node {} read operation for {} returned {}", nodeName, key, value);
            return value;
            
        } catch (DSMException e) {
            throw e;
        } catch (Exception e) {
            throw new DSMException("Error during read operation: " + e.getMessage(), e);
        } finally {
            pendingOperations.remove(operationId);
        }
    }
    
    @Override
    public void shutdown() {
        localStore.clear();
        pendingOperations.clear();
        logger.info("CA DSM shut down for node {}", nodeName);
    }
    
    /**
     * Process a write request from another node (coordinator only)
     */
    public void processWriteRequest(Message message) {
        if (!isCoordinator()) {
            logger.warn("Non-coordinator node received write request, ignoring");
            return;
        }
        
        String operationId = message.query("operationId");
        String key = message.query("key");
        String value = message.query("value");
        String sender = message.queryHeader("sender");
        
        try {
            // Throttle coordinator processing to prevent overload
            synchronized (coordinatorLock) {
                // Update the central store
                localStore.put(key, value);
                logger.debug("Coordinator processed write request for {}={} from {}", key, value, sender);
                
                // Simulate some processing time
                Thread.sleep(COORDINATOR_PROCESSING_TIME_MS);
                
                // Send direct acknowledgment to the sender first to prevent timeout
                Message ackMsg = new Message()
                        .add("type", "DSM_CA_UPDATE")
                        .add("key", key)
                        .add("value", value)
                        .add("operationId", operationId);
                
                // First send directly to requester to ensure they get a fast response
                sendMessage(ackMsg, sender);
            }
            
            // Then broadcast to everyone else in the background
            new Thread(() -> {
                try {
                    Message broadcastMsg = new Message()
                            .add("type", "DSM_CA_UPDATE")
                            .add("key", key)
                            .add("value", value);
                    
                    // Broadcast to everyone except the original sender
                    broadcastMessageExcept(broadcastMsg, sender);
                } catch (Exception e) {
                    logger.warn("Error in background broadcast: {}", e.getMessage());
                }
            }).start();
            
        } catch (Exception e) {
            logger.error("Error processing write request: {}", e.getMessage());
            
            // Send error response to sender
            Message errorMsg = new Message()
                    .add("type", "DSM_CA_ERROR")
                    .add("operationId", operationId)
                    .add("error", e.getMessage());
            
            sendMessage(errorMsg, sender);
        }
    }
    
    /**
     * Process a read request from another node (coordinator only)
     */
    public void processReadRequest(Message message) {
        if (!isCoordinator()) {
            logger.warn("Non-coordinator node received read request, ignoring");
            return;
        }
        
        String operationId = message.query("operationId");
        String key = message.query("key");
        String sender = message.queryHeader("sender");
        
        try {
            // Throttle coordinator processing to prevent overload
            synchronized (coordinatorLock) {
                // Read from the central store
                String value = localStore.get(key);
                logger.debug("Coordinator processed read request for {} (value: {}) from {}", key, value, sender);
                
                // Simulate some processing time
                Thread.sleep(COORDINATOR_PROCESSING_TIME_MS);
                
                // Send response to sender
                Message responseMsg = new Message()
                        .add("type", "DSM_CA_READ_RESPONSE")
                        .add("operationId", operationId)
                        .add("key", key)
                        .add("value", value != null ? value : "");
                
                // Make sure the response is sent directly to the requester and not lost
                sendMessage(responseMsg, sender);
            }
        } catch (Exception e) {
            logger.error("Error processing read request: {}", e.getMessage());
            
            // Send error response to sender
            Message errorMsg = new Message()
                    .add("type", "DSM_CA_ERROR")
                    .add("operationId", operationId)
                    .add("error", e.getMessage());
            
            sendMessage(errorMsg, sender);
        }
    }
    
    /**
     * Process an update message from the coordinator
     */
    public void processUpdate(Message message) {
        String key = message.query("key");
        String value = message.query("value");
        String operationId = message.query("operationId");
        String sender = message.queryHeader("sender");
        
        // Update local cache
        localStore.put(key, value);
        logger.debug("Node {} received update for {}={} from {}", nodeName, key, value, sender);
        
        // If this was in response to our operation, complete it
        if (operationId != null && pendingOperations.containsKey(operationId)) {
            OperationState opState = pendingOperations.get(operationId);
            if (opState != null) {
                opState.value.set(value);
                opState.latch.countDown();
                logger.debug("Node {} completed operation {} in response to update", nodeName, operationId);
            }
        }
    }
    
    /**
     * Process a read response from the coordinator
     */
    public void processReadResponse(Message message) {
        String operationId = message.query("operationId");
        String key = message.query("key");
        String value = message.query("value");
        
        logger.debug("Node {} received read response for {} (value: {})", nodeName, key, value);
        
        OperationState opState = pendingOperations.get(operationId);
        if (opState != null) {
            opState.value.set(value.isEmpty() ? null : value);
            opState.latch.countDown();
        }
    }
    
    /**
     * Process an error message from the coordinator
     */
    public void processError(Message message) {
        String operationId = message.query("operationId");
        String error = message.query("error");
        
        logger.warn("Node {} received error for operation {}: {}", nodeName, operationId, error);
        
        OperationState opState = pendingOperations.get(operationId);
        if (opState != null) {
            opState.error.set(error);
            opState.latch.countDown();
        }
    }
    
    /**
     * Check if this node is the coordinator
     */
    private boolean isCoordinator() {
        return nodeName.equals(COORDINATOR_NODE);
    }
    
    /**
     * Tracks the state of an operation
     */
    private static class OperationState {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> value = new AtomicReference<>();
        final AtomicReference<String> error = new AtomicReference<>();
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
     * Broadcasts a message to all nodes except one specific node.
     * This is used to avoid duplicate messages to the original sender.
     */
    private void broadcastMessageExcept(Message message, String exceptNodeName) {
        try {
            // Since we can't exclude a node from broadcast, we'll handle it manually
            for (int i = 0; i < Network.getInstance().numberOfNodes(); i++) {
                String targetNode = "Node_" + i;
                if (!targetNode.equals(exceptNodeName) && !targetNode.equals(nodeName)) {
                    sendMessage(message, targetNode);
                }
            }
        } catch (Exception e) {
            logger.warn("Error during selective broadcast: {}", e.getMessage());
        }
    }
}


