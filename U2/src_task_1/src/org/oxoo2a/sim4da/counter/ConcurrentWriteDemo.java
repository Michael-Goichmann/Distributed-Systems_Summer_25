package org.oxoo2a.sim4da.counter;

import org.oxoo2a.sim4da.Node;
import org.oxoo2a.sim4da.Simulator;
import org.oxoo2a.sim4da.dsm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates concurrent write conflicts in different DSM implementations.
 * Multiple nodes write to the same keys simultaneously to trigger conflict resolution mechanisms.
 */
public class ConcurrentWriteDemo {
    private static final Logger logger = LoggerFactory.getLogger(ConcurrentWriteDemo.class);
    private static final int NUM_NODES = 16;
    // private static final int NUM_NODES = 64;
    // private static final int NUM_NODES = 128;
    private static final int SIMULATION_DURATION_SECONDS = 60; // Increased from 30 to 60
    private static final int NUM_SHARED_COUNTERS = 3;
    private static final int BASE_DELAY_MS = 500; // New base delay between operations
    private static final int INIT_DELAY_MS = 2000; // New delay after initialization
    
    // Track write conflicts and failures
    private static final AtomicInteger apConflicts = new AtomicInteger(0);
    private static final AtomicInteger apFailures = new AtomicInteger(0);
    private static final AtomicInteger cpConflicts = new AtomicInteger(0);
    private static final AtomicInteger cpFailures = new AtomicInteger(0);
    private static final AtomicInteger caConflicts = new AtomicInteger(0);
    private static final AtomicInteger caFailures = new AtomicInteger(0);
    
    /**
     * Main method to run the concurrent write demonstration.
     */
    public static void main(String[] args) {
        logger.info("Starting Concurrent Write Demo with {} nodes and {} shared counters", 
                NUM_NODES, NUM_SHARED_COUNTERS);
        
        // Create nodes
        for (int i = 0; i < NUM_NODES; i++) {
            new ConcurrentWriteNode("Node_" + i, i);
        }
        
        // Run the simulation
        Simulator simulator = Simulator.getInstance();
        logger.info("Running simulation for {} seconds", SIMULATION_DURATION_SECONDS);
        simulator.simulate(SIMULATION_DURATION_SECONDS);
        
        // Report final statistics
        logger.info("Demo completed. Final statistics:");
        logger.info("AP: {} conflicts, {} operation failures", apConflicts.get(), apFailures.get());
        logger.info("CP: {} conflicts, {} operation failures", cpConflicts.get(), cpFailures.get());
        logger.info("CA: {} conflicts, {} operation failures", caConflicts.get(), caFailures.get());
        
        // Add CAP theorem explanation
        logger.info("\u001B[33m===== CAP THEOREM DEMONSTRATION =====\u001B[0m");
        logger.info("AP (Availability+Partition Tolerance): {} conflicts, {} failures", 
                apConflicts.get(), apFailures.get());
        if (apConflicts.get() > 0) {
            logger.info("  ✓ AP showing expected behavior: conflicts due to eventual consistency");
        } else {
            logger.info("  ✗ AP not showing expected conflicts - eventual consistency propagation too fast");
        }
        
        logger.info("CP (Consistency+Partition Tolerance): {} conflicts, {} failures",
                cpConflicts.get(), cpFailures.get());
        if (cpFailures.get() > cpConflicts.get()) {
            logger.info("  ✓ CP showing expected behavior: fewer conflicts but some failures under contention");
        } else {
            logger.info("  ✗ CP not behaving as expected");
        }
        
        logger.info("CA (Consistency+Availability): {} conflicts, {} failures",
                caConflicts.get(), caFailures.get());
        if (caFailures.get() < cpFailures.get()) {
            logger.info("  ✓ CA showing expected behavior: fewer failures than CP");
        } else {
            logger.info("  ✗ CA showing unexpected behavior: too many failures for non-partitioned network");
        }
        logger.info("\u001B[33m====================================\u001B[0m");
        
        simulator.shutdown();
    }
    
    /**
     * Node that concurrently writes to shared counters to demonstrate conflicts.
     */
    private static class ConcurrentWriteNode extends Node {
        private final Logger logger;
        private final int nodeId;
        
        // Store local expectation of counter values based on the last successful write
        private final Map<String, Integer> apLastWrittenValue = new HashMap<>();
        private final Map<String, Integer> cpLastWrittenValue = new HashMap<>();
        private final Map<String, Integer> caLastWrittenValue = new HashMap<>();
        
        public ConcurrentWriteNode(String name, int nodeId) {
            super(name);
            this.nodeId = nodeId;
            this.logger = LoggerFactory.getLogger(name);
        }
        
        @Override
        protected void engage() {
            logger.info("Concurrent write node {} started", NodeName());
            
            try {
                // Initialize counters
                initializeCounters();
                
                // Wait for all nodes to initialize
                logger.info("Node {} waiting for all nodes to initialize...", NodeName());
                Thread.sleep(INIT_DELAY_MS + nodeId * 200); // Staggered startup
                
                // Run the main operation loop
                while (Simulator.getInstance().isSimulating()) {
                    // Randomly select a counter to update
                    int counterIndex = ThreadLocalRandom.current().nextInt(NUM_SHARED_COUNTERS);
                    String counterKey = "shared_counter_" + counterIndex;
                    
                    // Try to increment the counter in each DSM with delays between
                    incrementCounter(counterKey);
                    
                    // Occasionally check counter values
                    if (ThreadLocalRandom.current().nextInt(5) == 0) {
                        checkCounters();
                    }
                    
                    // Add randomized delay between iterations
                    Thread.sleep(BASE_DELAY_MS + ThreadLocalRandom.current().nextInt(500));
                }
            } catch (Exception e) {
                logger.error("Error in concurrent write node: {}", e.getMessage(), e);
            }
            
            logger.info("Concurrent write node {} finished", NodeName());
        }
        
        private void initializeCounters() {
            // Initialize CA DSM first if coordinator
            if (nodeId == 0) {
                try {
                    logger.info("Coordinator initializing CA DSM...");
                    DSM caDsm = getDSM(DSMFactory.DSMType.CA);
                    // Give time for CA initialization
                    Thread.sleep(500);
                } catch (Exception e) {
                    logger.error("Error initializing CA DSM: {}", e.getMessage());
                }
            }
            
            // Other nodes wait for coordinator to initialize
            if (nodeId > 0) {
                try {
                    Thread.sleep(1000); // Wait for coordinator
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Initialize DSMs with delays between
            try {
                Thread.sleep(nodeId * 100); // Staggered initialization
                
                for (int i = 0; i < NUM_SHARED_COUNTERS; i++) {
                    String counterKey = "shared_counter_" + i;
                    
                    // Initialize our expectations
                    apLastWrittenValue.put(counterKey, 0);
                    cpLastWrittenValue.put(counterKey, 0);
                    caLastWrittenValue.put(counterKey, 0);
                    
                    // Node 0 initializes all counters to 0
                    if (nodeId == 0) {
                        try {
                            getDSM(DSMFactory.DSMType.AP).write(counterKey, "0");
                            Thread.sleep(100);
                            getDSM(DSMFactory.DSMType.CP).write(counterKey, "0");
                            Thread.sleep(100);
                            getDSM(DSMFactory.DSMType.CA).write(counterKey, "0");
                            logger.info("Initialized counter {}", counterKey);
                            Thread.sleep(200); // Extra delay between counters
                        } catch (DSMException e) {
                            logger.warn("Failed to initialize {}: {}", counterKey, e.getMessage());
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        private void incrementCounter(String counterKey) {
            // Make all increments happen more closely together in time
            // to increase likelihood of concurrent conflicts
            try {
                // Do CA increments together in a batch
                incrementCA(counterKey);
                
                // Do AP increments at nearly the same time
                // (no delay between them to maximize conflicting updates)
                incrementAP(counterKey);
                
                // Do CP increments
                incrementCP(counterKey);
                
                // Short delay between different counters
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        private void incrementAP(String counterKey) {
            try {
                String currentValueStr = getDSM(DSMFactory.DSMType.AP).read(counterKey);
                int currentValue = currentValueStr != null ? Integer.parseInt(currentValueStr) : 0;
                
                // A conflict occurs if the value we read is not what we last wrote.
                // This means another node has updated the value since our last write.
                int lastWritten = apLastWrittenValue.getOrDefault(counterKey, 0);
                if (currentValue != lastWritten) {
                    logger.warn("\u001B[33mAP CONFLICT\u001B[0m: Read {} for {} but expected our last write of {} (EXPECTED in AP model)",
                            currentValue, counterKey, lastWritten);
                    ConcurrentWriteDemo.apConflicts.incrementAndGet();
                }
                
                // Increment based on the value we just read
                int newValue = currentValue + 1;
                getDSM(DSMFactory.DSMType.AP).write(counterKey, String.valueOf(newValue));
                apLastWrittenValue.put(counterKey, newValue); // Update our last written value
                
                logger.debug("AP: Incremented {} from {} to {}", counterKey, currentValue, newValue);
            } catch (DSMException e) {
                logger.warn("AP: Failed to increment {}: {}", counterKey, e.getMessage());
                ConcurrentWriteDemo.apFailures.incrementAndGet();
            }
        }
        
        private void incrementCP(String counterKey) {
            try {
                String currentValueStr = getDSM(DSMFactory.DSMType.CP).read(counterKey);
                int currentValue = currentValueStr != null ? Integer.parseInt(currentValueStr) : 0;

                // With CP, we expect to read the value we last wrote due to strong consistency.
                int lastWritten = cpLastWrittenValue.getOrDefault(counterKey, 0);
                if (currentValue != lastWritten) {
                    logger.warn("\u001B[33mCP CONFLICT\u001B[0m: Read {} for {} but expected our last write of {} (UNUSUAL in CP model)",
                            currentValue, counterKey, lastWritten);
                    ConcurrentWriteDemo.cpConflicts.incrementAndGet();
                }

                // Increment based on the value we just read
                int newValue = currentValue + 1;
                getDSM(DSMFactory.DSMType.CP).write(counterKey, String.valueOf(newValue));
                cpLastWrittenValue.put(counterKey, newValue); // Update our last written value

                logger.debug("CP: Incremented {} from {} to {}", counterKey, currentValue, newValue);
            } catch (DSMException e) {
                // CP may occasionally fail to reach quorum under heavy concurrency
                logger.warn("CP: Failed to increment {}: {}", counterKey, e.getMessage());
                ConcurrentWriteDemo.cpFailures.incrementAndGet();
            }
        }
        
        private void incrementCA(String counterKey) {
            try {
                String currentValueStr = getDSM(DSMFactory.DSMType.CA).read(counterKey);
                int currentValue = currentValueStr != null ? Integer.parseInt(currentValueStr) : 0;

                // With CA, we expect to read the value we last wrote due to strong consistency.
                int lastWritten = caLastWrittenValue.getOrDefault(counterKey, 0);
                if (currentValue != lastWritten) {
                    logger.warn("\u001B[33mCA CONFLICT\u001B[0m: Read {} for {} but expected our last write of {} (UNUSUAL in CA model)",
                            currentValue, counterKey, lastWritten);
                    ConcurrentWriteDemo.caConflicts.incrementAndGet();
                }

                // Increment based on the value we just read
                int newValue = currentValue + 1;
                getDSM(DSMFactory.DSMType.CA).write(counterKey, String.valueOf(newValue));
                caLastWrittenValue.put(counterKey, newValue); // Update our last written value

                logger.debug("CA: Incremented {} from {} to {}", counterKey, currentValue, newValue);
            } catch (DSMException e) {
                // CA might have occasional failures under high load but should generally work
                logger.warn("CA: Failed to increment {}: {}", counterKey, e.getMessage());
                ConcurrentWriteDemo.caFailures.incrementAndGet();
            }
        }
        
        private void checkCounters() {
            StringBuilder report = new StringBuilder();
            report.append(NodeName()).append(" counter report:");
            
            for (int i = 0; i < NUM_SHARED_COUNTERS; i++) {
                String counterKey = "shared_counter_" + i;
                report.append("\n  ").append(counterKey).append(": ");
                
                try {
                    String apValue = getDSM(DSMFactory.DSMType.AP).read(counterKey);
                    apValue = apValue != null ? apValue : "N/A";
                    report.append("AP=").append(apValue);
                } catch (DSMException e) {
                    report.append("AP=ERROR");
                    apFailures.incrementAndGet(); // Track read failures too
                }
                
                try {
                    String cpValue = getDSM(DSMFactory.DSMType.CP).read(counterKey);
                    cpValue = cpValue != null ? cpValue : "N/A";
                    report.append(", CP=").append(cpValue);
                } catch (DSMException e) {
                    report.append(", CP=ERROR");
                    cpFailures.incrementAndGet(); // Track read failures too
                    
                    // Add educational message about CAP theorem
                    if (e.getMessage().contains("Failed to reach") && e.getMessage().contains("quorum")) {
                        report.append(" \u001B[35m(CP prioritizing consistency over availability)\u001B[0m");
                    }
                }
                
                try {
                    String caValue = getDSM(DSMFactory.DSMType.CA).read(counterKey);
                    caValue = caValue != null ? caValue : "N/A";
                    report.append(", CA=").append(caValue);
                } catch (DSMException e) {
                    report.append(", CA=ERROR");
                    caFailures.incrementAndGet(); // Track read failures too
                }
                
                // Check for divergence between implementations
                try {
                    String apValue = getDSM(DSMFactory.DSMType.AP).read(counterKey);
                    String cpValue = getDSM(DSMFactory.DSMType.CP).read(counterKey);
                    String caValue = getDSM(DSMFactory.DSMType.CA).read(counterKey);
                    
                    if (apValue != null && cpValue != null && caValue != null) {
                        int apVal = Integer.parseInt(apValue);
                        int cpVal = Integer.parseInt(cpValue);
                        int caVal = Integer.parseInt(caValue);
                        
                        int maxDiff = Math.max(Math.abs(apVal - cpVal), 
                                 Math.max(Math.abs(apVal - caVal), 
                                          Math.abs(cpVal - caVal)));
                        
                        if (maxDiff > 5) {
                            report.append(" \u001B[36m(DIVERGED by ").append(maxDiff).append(")\u001B[0m");
                        }
                        
                        // Compare operation rates across DSM types to show CAP trade-offs
                        if (apVal > cpVal + 10) {
                            report.append(" \u001B[32m(AP faster than CP - prioritizing availability)\u001B[0m");
                        }
                        if (caVal > cpVal + 10) {
                            report.append(" \u001B[32m(CA faster than CP - CP quorums limiting throughput)\u001B[0m");
                        }
                    }
                } catch (DSMException e) {
                    // Ignore divergence check if there are errors reading values
                }
            }
            
            logger.info(report.toString());
        }
    }
}
