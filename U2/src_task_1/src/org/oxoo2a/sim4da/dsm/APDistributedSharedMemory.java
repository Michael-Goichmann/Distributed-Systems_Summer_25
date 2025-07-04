package org.oxoo2a.sim4da.dsm;

import org.oxoo2a.sim4da.Message;
import org.oxoo2a.sim4da.Network;
import org.oxoo2a.sim4da.Node;
import org.oxoo2a.sim4da.UnknownNodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AP (Availability & Partition Tolerance) implementation of DSM.
 * 
 * Each node has a local copy of the data. Write operations are performed locally and then
 * asynchronously propagated to other nodes. Read operations are always performed locally.
 * This approach ensures high availability but sacrifices consistency.
 */
public class APDistributedSharedMemory implements DSM {
    private final Map<String, ValueWithTimestamp> localStore = new ConcurrentHashMap<>();
    private String nodeName;
    private final Node node;
    private Logger logger;
    
    // A simple timestamp generator for implementing Last-Write-Wins
    private final AtomicLong timestampGenerator = new AtomicLong(0);
    
    public APDistributedSharedMemory(Node node) {
        this.node = node;
    }
    
    @Override
    public void initialize(String nodeName) {
        this.nodeName = nodeName;
        this.logger = LoggerFactory.getLogger(nodeName + "-AP-DSM");
        logger.info("AP DSM initialized for node {}", nodeName);
    }
    
    @Override
    public void write(String key, String value) {
        // Get current timestamp for Last-Write-Wins conflict resolution
        long timestamp = timestampGenerator.incrementAndGet();
        
        // Update local store
        localStore.put(key, new ValueWithTimestamp(value, timestamp));
        logger.debug("Node {} wrote {}={} locally with timestamp {}", nodeName, key, value, timestamp);
        
        // Asynchronously propagate to other nodes
        Message updateMsg = new Message()
                .add("type", "DSM_AP_UPDATE")
                .add("key", key)
                .add("value", value)
                .add("timestamp", String.valueOf(timestamp));
        
        // Use broadcastMessage method instead of direct broadcast
        try {
            broadcastMessage(updateMsg);
            logger.debug("Node {} broadcast update of {}={}", nodeName, key, value);
        } catch (Exception e) {
            logger.warn("Failed to broadcast update: {}", e.getMessage());
            // In AP model, we continue even if broadcasting fails
        }
    }
    
    @Override
    public String read(String key) {
        ValueWithTimestamp valueWithTimestamp = localStore.get(key);
        if (valueWithTimestamp == null) {
            logger.debug("Node {} read key {} (not found)", nodeName, key);
            return null;
        }
        
        logger.debug("Node {} read {}={} (timestamp: {})", 
                nodeName, key, valueWithTimestamp.value, valueWithTimestamp.timestamp);
        return valueWithTimestamp.value;
    }
    
    @Override
    public void shutdown() {
        localStore.clear();
        logger.info("AP DSM shut down for node {}", nodeName);
    }
    
    /**
     * Process an update message from another node
     */
    public void processUpdateMessage(Message message) {
        String key = message.query("key");
        String value = message.query("value");
        long receivedTimestamp = Long.parseLong(message.query("timestamp"));
        
        // Implement Last-Write-Wins conflict resolution
        ValueWithTimestamp currentValue = localStore.get(key);
        if (currentValue == null || receivedTimestamp > currentValue.timestamp) {
            localStore.put(key, new ValueWithTimestamp(value, receivedTimestamp));
            logger.debug("Node {} updated local store with remote value {}={} (timestamp: {})", 
                    nodeName, key, value, receivedTimestamp);
            
            // Update local timestamp generator if needed
            long currentMax = timestampGenerator.get();
            if (receivedTimestamp > currentMax) {
                timestampGenerator.compareAndSet(currentMax, receivedTimestamp);
            }
        } else {
            logger.debug("Node {} ignored outdated update for key {} (local ts: {}, received ts: {})", 
                    nodeName, key, currentValue.timestamp, receivedTimestamp);
        }
    }
    
    /**
     * Broadcasts a message to all nodes via the associated Node object.
     * This method delegates to the appropriate broadcasting method in Node.
     */
    private void broadcastMessage(Message message) {
        try {
            // Use sendAll method for broadcasting which the Node will expose
            ((DSMNode)node).sendDSMBroadcast(message);
        } catch (Exception e) {
            logger.warn("Error during broadcast: {}", e.getMessage());
        }
    }
    
    /**
     * Value class with timestamp for Last-Write-Wins conflict resolution
     */
    private static class ValueWithTimestamp {
        final String value;
        final long timestamp;
        
        ValueWithTimestamp(String value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }
}
