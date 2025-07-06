package org.oxoo2a.sim4da.counter;

import org.oxoo2a.sim4da.Message;
import org.oxoo2a.sim4da.Network;
import org.oxoo2a.sim4da.Node;
import org.oxoo2a.sim4da.Simulator;
import org.oxoo2a.sim4da.dsm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A demo that simulates network partitions to demonstrate how different DSM 
 * implementations handle partitions according to the CAP theorem.
 */
public class PartitionSimulator {
    private static final Logger logger = LoggerFactory.getLogger(PartitionSimulator.class);
    private static final int NUM_NODES = 6;
    private static final int SIMULATION_DURATION_SECONDS = 60;
    
    // Partition simulation parameters
    private static final int PARTITION_START_DELAY_SEC = 10; // Wait before starting partitions
    private static final int PARTITION_DURATION_SEC = 5;    // How long partitions last
    private static final int PARTITION_INTERVAL_SEC = 15;   // Time between partitions
    
    // Track whether a partition is active
    private static final AtomicBoolean partitionActive = new AtomicBoolean(false);
    // Track which nodes are in which partition
    private static final Set<String> partition1 = ConcurrentHashMap.newKeySet();
    private static final Set<String> partition2 = ConcurrentHashMap.newKeySet();
    
    // Add statistics tracking
    private static final AtomicInteger apOperations = new AtomicInteger(0);
    private static final AtomicInteger apFailures = new AtomicInteger(0);
    private static final AtomicInteger cpOperations = new AtomicInteger(0);
    private static final AtomicInteger cpFailures = new AtomicInteger(0);
    private static final AtomicInteger caOperations = new AtomicInteger(0);
    private static final AtomicInteger caFailures = new AtomicInteger(0);
    
    // Add partition phase tracking for stats
    private static final AtomicInteger partitionPhases = new AtomicInteger(0);
    
    /**
     * Main method to run the partition simulation.
     */
    public static void main(String[] args) {
        logger.info("Starting Partition Simulator with {} nodes", NUM_NODES);
        
        // Create nodes
        for (int i = 0; i < NUM_NODES; i++) {
            new PartitionedNode("Node_" + i);
        }
        
        // Start partition controller
        new Thread(PartitionSimulator::runPartitionController).start();
        
        // Run the simulation
        Simulator simulator = Simulator.getInstance();
        logger.info("Running simulation for {} seconds", SIMULATION_DURATION_SECONDS);
        simulator.simulate(SIMULATION_DURATION_SECONDS);
        
        // Report final statistics
        logger.info("Partition simulation completed");
        
        // Add CAP theorem explanation and stats
        logger.info("\u001B[33m===== CAP THEOREM DEMONSTRATION =====\u001B[0m");
        logger.info("Simulation ran with {} partition phases", partitionPhases.get());
        logger.info("AP (Availability+Partition Tolerance): {} operations, {} failures ({}%)", 
                apOperations.get(), apFailures.get(), 
                apOperations.get() > 0 ? (apFailures.get() * 100.0 / apOperations.get()) : 0);
        logger.info("CP (Consistency+Partition Tolerance): {} operations, {} failures ({}%)", 
                cpOperations.get(), cpFailures.get(), 
                cpOperations.get() > 0 ? (cpFailures.get() * 100.0 / cpOperations.get()) : 0);
        logger.info("CA (Consistency+Availability): {} operations, {} failures ({}%)", 
                caOperations.get(), caFailures.get(), 
                caOperations.get() > 0 ? (caFailures.get() * 100.0 / caOperations.get()) : 0);
        
        logger.info("\u001B[33mKey CAP Theorem Insights:\u001B[0m");
        logger.info("- AP: Continues to work in both partitions ({}% success rate)", 
                apOperations.get() > 0 ? 100 - (apFailures.get() * 100.0 / apOperations.get()) : 0);
        logger.info("  * May create inconsistent views of data across partitions");
        logger.info("  * Sacrifices consistency to maintain availability and partition tolerance");
        
        logger.info("- CP: Fails operations when quorum cannot be reached ({}% failure rate)", 
                cpOperations.get() > 0 ? (cpFailures.get() * 100.0 / cpOperations.get()) : 0);
        logger.info("  * Refuses to perform operations that might lead to inconsistency");
        logger.info("  * Sacrifices availability to maintain consistency and partition tolerance");
        
        logger.info("- CA: Operations succeed within partitions but fail across partitions ({}% failure rate)", 
                caOperations.get() > 0 ? (caFailures.get() * 100.0 / caOperations.get()) : 0);
        logger.info("  * Requires coordinator contact for all operations");
        logger.info("  * Cannot handle network partitions - nodes in separate partition from coordinator fail");
        logger.info("  * Proves CAP theorem: Cannot simultaneously provide C, A, and P");
        
        logger.info("\u001B[33m====================================\u001B[0m");
        
        simulator.shutdown();
        
        logger.info("Partition simulation completed");
    }
    
    /**
     * Controls the creation and healing of network partitions.
     */
    private static void runPartitionController() {
        try {
            // Wait for initial delay
            Thread.sleep(PARTITION_START_DELAY_SEC * 1000);
            
            while (Simulator.getInstance().isSimulating()) {
                // Create a partition
                createPartition();
                
                // Wait for partition duration
                Thread.sleep(PARTITION_DURATION_SEC * 1000);
                
                // Heal the partition
                healPartition();
                
                // Wait before next partition
                Thread.sleep(PARTITION_INTERVAL_SEC * 1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("Partition controller interrupted");
        }
    }
    
    /**
     * Creates a network partition by dividing nodes into two groups.
     */
    private static void createPartition() {
        if (!Simulator.getInstance().isSimulating()) return;
        
        // Clear previous partition groups
        partition1.clear();
        partition2.clear();
        
        // Divide nodes into two partitions
        for (int i = 0; i < NUM_NODES; i++) {
            String nodeName = "Node_" + i;
            if (i < NUM_NODES / 2) {
                partition1.add(nodeName);
            } else {
                partition2.add(nodeName);
            }
        }
        
        partitionPhases.incrementAndGet();
        partitionActive.set(true);
        logger.info("\u001B[35m==== NETWORK PARTITION {} CREATED ====\u001B[0m", partitionPhases.get());
        logger.info("Partition 1: {}", partition1);
        logger.info("Partition 2: {}", partition2);
    }
    
    /**
     * Heals the network partition.
     */
    private static void healPartition() {
        partitionActive.set(false);
        logger.info("\u001B[35m==== NETWORK PARTITION HEALED ====\u001B[0m");
    }
    
    /**
     * Checks if a message can be delivered between two nodes based on the current partition.
     */
    public static boolean canDeliver(String fromNode, String toNode) {
        if (!partitionActive.get()) {
            return true; // No partition active, deliver all messages
        }
        
        // Check if both nodes are in the same partition
        return (partition1.contains(fromNode) && partition1.contains(toNode)) ||
               (partition2.contains(fromNode) && partition2.contains(toNode));
    }
    
    /**
     * Node that operates in a potentially partitioned network.
     */
    private static class PartitionedNode extends Node {
        private final Logger logger;
        private final String counterKey;
        private final Map<DSMFactory.DSMType, Integer> expectedValues = new EnumMap<>(DSMFactory.DSMType.class);
        private final Map<String, Map<DSMFactory.DSMType, Integer>> lastSeenValues = new HashMap<>();
        
        public PartitionedNode(String name) {
            super(name);
            this.logger = LoggerFactory.getLogger(name);
            this.counterKey = "counter_" + name;
            
            // Initialize expected values
            for (DSMFactory.DSMType type : DSMFactory.DSMType.values()) {
                expectedValues.put(type, 0);
            }
        }
        
        @Override
        protected void engage() {
            logger.info("Partitioned node {} started", NodeName());
            
            try {
                // Initialize counters
                for (DSMFactory.DSMType type : DSMFactory.DSMType.values()) {
                    try {
                        DSM dsm = getDSM(type);
                        dsm.write(counterKey, "0");
                    } catch (DSMException e) {
                        logger.warn("Failed to initialize counter for {}: {}", type, e.getMessage());
                    }
                }
                
                // Main operation loop
                while (Simulator.getInstance().isSimulating()) {
                    try {
                        // Increment own counters
                        incrementCounters();
                        
                        // Read other nodes' counters
                        readOtherCounters();
                        
                        // Add some randomness to the timing
                        Thread.sleep(ThreadLocalRandom.current().nextInt(500, 1500));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        // Don't treat CP failures during partitions as errors
                        boolean isCPQuorumFailure = e.getMessage() != null && 
                                e.getMessage().contains("Failed to reach") && 
                                e.getMessage().contains("quorum");
                        
                        if (isCPQuorumFailure) {
                            // This is expected CP behavior during partitions
                            logger.info("\u001B[33mExpected CP behavior\u001B[0m: {}", e.getMessage());
                        } else {
                            logger.error("Error in partitioned node operation: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Fatal error in partitioned node: {}", e.getMessage(), e);
            }
            
            logger.info("Partitioned node {} finished", NodeName());
        }
        
        private void incrementCounters() {
            for (DSMFactory.DSMType type : DSMFactory.DSMType.values()) {
                try {
                    DSM dsm = getDSM(type);
                    int newValue = expectedValues.get(type) + 1;
                    dsm.write(counterKey, String.valueOf(newValue));
                    expectedValues.put(type, newValue);
                    
                    // Track successful operations - explicitly use class reference
                    if (type == DSMFactory.DSMType.AP) {
                        PartitionSimulator.apOperations.incrementAndGet();
                        logger.debug("{}: Successfully incremented AP counter to {}", NodeName(), newValue);
                    } else if (type == DSMFactory.DSMType.CP) {
                        PartitionSimulator.cpOperations.incrementAndGet();
                        logger.debug("{}: Successfully incremented CP counter to {}", NodeName(), newValue);
                    } else if (type == DSMFactory.DSMType.CA) {
                        PartitionSimulator.caOperations.incrementAndGet();
                        logger.debug("{}: Successfully incremented CA counter to {}", NodeName(), newValue);
                    }
                    
                } catch (DSMException e) {
                    // Add educational messages about partitions
                    String partitionInfo = "";
                    if (partitionActive.get()) {
                        partitionInfo = partition1.contains(NodeName()) ? " [Partition 1]" : " [Partition 2]";
                        
                        if (type == DSMFactory.DSMType.CP && 
                                e.getMessage().contains("Failed to reach") && 
                                e.getMessage().contains("quorum")) {
                            logger.info("\u001B[33mCAP THEOREM\u001B[0m: CP DSM refusing operation during partition to maintain consistency");
                        }
                        
                        if (type == DSMFactory.DSMType.CA) {
                            logger.info("\u001B[33mCAP THEOREM\u001B[0m: CA DSM expected to fail during network partitions");
                        }
                    }
                    
                    // Track failures - explicitly use class reference
                    if (type == DSMFactory.DSMType.AP) {
                        PartitionSimulator.apFailures.incrementAndGet();
                        logger.warn("\u001B[31m{}: AP WRITE FAILURE{}\u001B[0m: {}", 
                                NodeName(), partitionInfo, e.getMessage());
                    } else if (type == DSMFactory.DSMType.CP) {
                        PartitionSimulator.cpFailures.incrementAndGet();
                        // For CP, we expect quorum failures during partitions, so log differently
                        if (e.getMessage().contains("Failed to reach") && e.getMessage().contains("quorum") && partitionActive.get()) {
                            logger.info("\u001B[33m{}: CP WRITE FAILURE{}\u001B[0m: {} (Expected during partitions - CP prioritizes consistency)", 
                                    NodeName(), partitionInfo, e.getMessage());
                        } else {
                            logger.warn("\u001B[31m{}: CP WRITE FAILURE{}\u001B[0m: {}", 
                                    NodeName(), partitionInfo, e.getMessage());
                        }
                    } else if (type == DSMFactory.DSMType.CA) {
                        PartitionSimulator.caFailures.incrementAndGet();
                        logger.warn("\u001B[31m{}: CA WRITE FAILURE{}\u001B[0m: {}", 
                                NodeName(), partitionInfo, e.getMessage());
                    }
                }
            }
        }
        
        private void readOtherCounters() {
            for (int i = 0; i < NUM_NODES; i++) {
                String nodeName = "Node_" + i;
                if (nodeName.equals(NodeName())) continue; // Skip own counter
                
                String nodeCounterKey = "counter_" + nodeName;
                
                // Ensure we have a map for this node
                lastSeenValues.computeIfAbsent(nodeCounterKey, k -> new EnumMap<>(DSMFactory.DSMType.class));
                
                for (DSMFactory.DSMType type : DSMFactory.DSMType.values()) {
                    try {
                        DSM dsm = getDSM(type);
                        String valueStr = dsm.read(nodeCounterKey);
                        
                        // Track successful operations - explicitly use class reference
                        if (type == DSMFactory.DSMType.AP) {
                            PartitionSimulator.apOperations.incrementAndGet();
                        } else if (type == DSMFactory.DSMType.CP) {
                            PartitionSimulator.cpOperations.incrementAndGet();
                        } else if (type == DSMFactory.DSMType.CA) {
                            PartitionSimulator.caOperations.incrementAndGet();
                        }
                        
                        if (valueStr != null) {
                            int value = Integer.parseInt(valueStr);
                            Integer lastValue = lastSeenValues.get(nodeCounterKey).get(type);
                            
                            // Check for inconsistencies
                            if (lastValue != null) {
                                if (value < lastValue) {
                                    logger.warn("\u001B[31m{}: {} INCONSISTENCY\u001B[0m: Value rollback for {} - was {}, now {}", 
                                            NodeName(), type, nodeCounterKey, lastValue, value);
                                } else if (value > lastValue) {
                                    logger.debug("{}: {} progress for {} - was {}, now {}", 
                                            NodeName(), type, nodeCounterKey, lastValue, value);
                                }
                            }
                            
                            lastSeenValues.get(nodeCounterKey).put(type, value);
                        }
                    } catch (DSMException e) {
                        // Track failures - explicitly use class reference
                        if (type == DSMFactory.DSMType.AP) {
                            PartitionSimulator.apFailures.incrementAndGet();
                        } else if (type == DSMFactory.DSMType.CP) {
                            PartitionSimulator.cpFailures.incrementAndGet();
                        } else if (type == DSMFactory.DSMType.CA) {
                            PartitionSimulator.caFailures.incrementAndGet();
                        }
                        
                        // Add partition info to the log
                        String partitionInfo = "";
                        if (partitionActive.get()) {
                            partitionInfo = partition1.contains(NodeName()) ? " [Partition 1]" : " [Partition 2]";
                            String targetPartitionInfo = partition1.contains(nodeName) ? " [Partition 1]" : " [Partition 2]";
                            
                            // Check if this is a cross-partition communication attempt
                            if (!partitionInfo.equals(targetPartitionInfo)) {
                                partitionInfo += " -> " + targetPartitionInfo + " (cross-partition)";
                            }
                        }
                        
                        logger.warn("\u001B[31m{}: {} READ FAILURE{}\u001B[0m: Could not read {}: {}", 
                                NodeName(), type, partitionInfo, nodeCounterKey, e.getMessage());
                    }
                }
            }
            
            // Log own counter status occasionally
            if (ThreadLocalRandom.current().nextInt(10) == 0) {
                logCounterStatus();
            }
        }
        
        private void logCounterStatus() {
            StringBuilder status = new StringBuilder();
            status.append(NodeName()).append(" counter status: ");
            
            for (DSMFactory.DSMType type : DSMFactory.DSMType.values()) {
                try {
                    DSM dsm = getDSM(type);
                    String valueStr = dsm.read(counterKey);
                    int actualValue = valueStr != null ? Integer.parseInt(valueStr) : -1;
                    int expectedValue = expectedValues.get(type);
                    
                    status.append(type).append("=").append(actualValue);
                    if (actualValue != expectedValue) {
                        status.append("(expected ").append(expectedValue).append(")");
                    }
                    status.append(" ");
                } catch (DSMException e) {
                    status.append(type).append("=ERROR ");
                }
            }
            
            if (partitionActive.get()) {
                String partition = partition1.contains(NodeName()) ? "1" : "2";
                status.append("- In partition ").append(partition);
            }
            
            logger.info(status.toString());
        }
        
        @Override
        public void sendDSMBroadcast(Message message) {
            // Override to simulate network partitions
            if (!partitionActive.get()) {
                // No partition, normal operation
                super.sendDSMBroadcast(message);
                return;
            }
            
            // Simulate partitioned broadcast by only sending to nodes in the same partition
            String senderName = NodeName();
            
            if (partition1.contains(senderName)) {
                // Only broadcast to nodes in partition 1
                for (String nodeName : partition1) {
                    if (!nodeName.equals(senderName)) {
                        super.sendDSMMessage(message, nodeName);
                    }
                }
            } else if (partition2.contains(senderName)) {
                // Only broadcast to nodes in partition 2
                for (String nodeName : partition2) {
                    if (!nodeName.equals(senderName)) {
                        super.sendDSMMessage(message, nodeName);
                    }
                }
            }
        }
        
        @Override
        public void sendDSMMessage(Message message, String toNodeName) {
            // Override to simulate network partitions
            if (!partitionActive.get() || canDeliver(NodeName(), toNodeName)) {
                super.sendDSMMessage(message, toNodeName);
            } else {
                // Message is dropped due to partition
                logger.debug("Message from {} to {} dropped due to partition", NodeName(), toNodeName);
            }
        }
    }
}
