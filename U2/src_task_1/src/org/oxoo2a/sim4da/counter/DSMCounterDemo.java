package org.oxoo2a.sim4da.counter;

import org.oxoo2a.sim4da.Node;
import org.oxoo2a.sim4da.Simulator;
import org.oxoo2a.sim4da.dsm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstration of inconsistencies in different DSM implementations using distributed counters.
 * Each node maintains its own counter and periodically reads other nodes' counters.
 * The demo checks for various inconsistencies such as:
 * - Value rollbacks (where a newer read has a lower value than an older read)
 * - Missing updates (where expected increments are not observed)
 * - Divergences (where nodes have significantly different views of the same counter)
 */
public class DSMCounterDemo {
    private static final Logger logger = LoggerFactory.getLogger(DSMCounterDemo.class);
    private static final int NUM_NODES = 5;
    private static final int SIMULATION_DURATION_SECONDS = 30;
    
    // Control parameters for the demo
    private static final int BASE_DELAY_MS = 500;      // Increased from 300ms to 500ms
    private static final int RANDOM_DELAY_MS = 300;    // Increased from 200ms to 300ms
    private static final int READ_ALL_INTERVAL = 5;    // Read all counters every N operations
    private static final int REPORT_INTERVAL = 10;     // Report status every N operations
    private static final int BETWEEN_DSM_DELAY_MS = 300; // Increased from previous suggestion
    
    // Tracking success/failure counts
    private static final AtomicInteger apOperations = new AtomicInteger(0);
    private static final AtomicInteger apFailures = new AtomicInteger(0);
    private static final AtomicInteger cpOperations = new AtomicInteger(0);
    private static final AtomicInteger cpFailures = new AtomicInteger(0);
    private static final AtomicInteger caOperations = new AtomicInteger(0);
    private static final AtomicInteger caFailures = new AtomicInteger(0);
    
    /**
     * Main method to run the counter demonstration.
     */
    public static void main(String[] args) {
        logger.info("Starting DSM Counter Demo with {} nodes", NUM_NODES);
        
        // Create the counter nodes
        for (int i = 0; i < NUM_NODES; i++) {
            new CounterNode("Node_" + i, i);
        }
        
        // Run the simulation
        Simulator simulator = Simulator.getInstance();
        logger.info("Running simulation for {} seconds", SIMULATION_DURATION_SECONDS);
        simulator.simulate(SIMULATION_DURATION_SECONDS);
        
        // Report final statistics
        logger.info("Demo completed. Final statistics:");
        logger.info("AP: {} operations, {} failures ({}% failure rate)", 
                apOperations.get(), apFailures.get(), 
                apOperations.get() > 0 ? (apFailures.get() * 100.0 / apOperations.get()) : 0);
        logger.info("CP: {} operations, {} failures ({}% failure rate)", 
                cpOperations.get(), cpFailures.get(), 
                cpOperations.get() > 0 ? (cpFailures.get() * 100.0 / cpOperations.get()) : 0);
        logger.info("CA: {} operations, {} failures ({}% failure rate)", 
                caOperations.get(), caFailures.get(), 
                caOperations.get() > 0 ? (caFailures.get() * 100.0 / caOperations.get()) : 0);
        
        // Add CAP theorem explanation
        logger.info("\u001B[33m===== CAP THEOREM DEMONSTRATION =====\u001B[0m");
        logger.info("AP (Availability+Partition Tolerance): Generated {} failures ({}%)", 
                apFailures.get(), apOperations.get() > 0 ? (apFailures.get() * 100.0 / apOperations.get()) : 0);
        logger.info("CP (Consistency+Partition Tolerance): Generated {} failures ({}%) - often quorum failures",
                cpFailures.get(), cpOperations.get() > 0 ? (cpFailures.get() * 100.0 / cpOperations.get()) : 0);
        logger.info("CA (Consistency+Availability): Generated {} failures ({}%) - would fail during partitions",
                caFailures.get(), caOperations.get() > 0 ? (caFailures.get() * 100.0 / caOperations.get()) : 0);
        logger.info("\u001B[33m====================================\u001B[0m");
        
        simulator.shutdown();
    }
    
    /**
     * Node that maintains and checks counters using different DSM implementations.
     */
    private static class CounterNode extends Node {
        private final Logger logger;
        private final int nodeId;
        
        // DSM instances
        private DSM apDsm;
        private DSM cpDsm;
        private DSM caDsm;
        
        // Counters for tracking the expected local value
        private int apCounter = 0;
        private int cpCounter = 0;
        private int caCounter = 0;
        
        // Maps to track the last seen value of each counter from each node
        private final Map<String, Integer> lastSeenApValues = new HashMap<>();
        private final Map<String, Integer> lastSeenCpValues = new HashMap<>();
        private final Map<String, Integer> lastSeenCaValues = new HashMap<>();
        
        public CounterNode(String name, int nodeId) {
            super(name);
            this.nodeId = nodeId;
            this.logger = LoggerFactory.getLogger(name);
        }
        
        @Override
        protected void engage() {
            logger.info("Counter node {} started", NodeName());
            
            // Initialize DSMs
            try {
                initializeDSMs();
                
                // Initialize counters
                for (int i = 0; i < NUM_NODES; i++) {
                    String counterKey = "counter_" + i;
                    initializeCounter(apDsm, counterKey);
                    initializeCounter(cpDsm, counterKey);
                    initializeCounter(caDsm, counterKey);
                }
                
                // Give time for initialization to propagate
                sleepWithRandomJitter(1000);
                
                // Run the counter operations
                runCounterOperations();
                
            } catch (Exception e) {
                logger.error("Error in counter node: {}", e.getMessage(), e);
            }
            
            logger.info("Counter node {} finished", NodeName());
        }
        
        private void initializeDSMs() {
            // Initialize coordinator first and wait for it to be ready
            if (nodeId == 0) {
                caDsm = getDSM(DSMFactory.DSMType.CA);
                logger.info("Coordinator CA DSM initialized");
                sleepWithRandomJitter(500);
            }
            
            // Wait a bit to ensure coordinator is ready before other nodes initialize
            if (nodeId != 0) {
                sleepWithRandomJitter(1000);
            }
            
            // Initialize all DSMs with delays between each initialization
            apDsm = getDSM(DSMFactory.DSMType.AP);
            sleepWithRandomJitter(200);
            
            cpDsm = getDSM(DSMFactory.DSMType.CP);
            sleepWithRandomJitter(200);
            
            // Initialize CA DSM for non-coordinator nodes
            if (nodeId != 0) {
                caDsm = getDSM(DSMFactory.DSMType.CA);
            }
            
            logger.info("All DSMs initialized");
            
            // Give each DSM time to fully initialize before starting operations
            sleepWithRandomJitter(1000);
        }
        
        private void initializeCounter(DSM dsm, String key) {
            try {
                String myKey = "counter_" + nodeId;
                if (key.equals(myKey)) {
                    // Initialize my own counter to 0
                    dsm.write(key, "0");
                }
            } catch (DSMException e) {
                logger.warn("Failed to initialize counter {}: {}", key, e.getMessage());
            }
        }
        
        private void runCounterOperations() {
            int opCount = 0;  // Initialize operation counter
            String myCounterKey = "counter_" + nodeId;
            
            while (Simulator.getInstance().isSimulating()) {
                opCount++;
                
                try {
                    // Increment own counter in all DSMs
                    incrementOwnCounter(myCounterKey);
                    
                    // Periodically read all counters
                    if (opCount % READ_ALL_INTERVAL == 0) {
                        readAllCounters();
                    }
                    
                    // Periodically report status
                    if (opCount % REPORT_INTERVAL == 0) {
                        reportStatus();
                    }
                    
                    // Add randomized delay to create more realistic patterns
                    sleepWithRandomJitter(BASE_DELAY_MS);
                    
                } catch (Exception e) {
                    logger.error("Error during counter operations: {}", e.getMessage());
                }
            }
        }
        
        private void incrementOwnCounter(String key) {
            // CA DSM increment - do this first to give it more time
            try {
                caCounter++;
                logger.info("CA: Attempting to increment {} to {}", key, caCounter);
                caDsm.write(key, String.valueOf(caCounter));
                caOperations.incrementAndGet();
                logger.info("CA: Successfully incremented {} to {}", key, caCounter);
            } catch (DSMException e) {
                caFailures.incrementAndGet();
                logger.warn("\u001B[31mCA FAILURE\u001B[0m: Failed to increment {}: {}", key, e.getMessage());
            }
            
            // Add a larger delay between operations to different DSMs
            sleepWithRandomJitter(BETWEEN_DSM_DELAY_MS);
            
            // AP DSM increment
            try {
                apCounter++;
                apDsm.write(key, String.valueOf(apCounter));
                apOperations.incrementAndGet();
                logger.debug("AP: Incremented {} to {}", key, apCounter);
            } catch (DSMException e) {
                apFailures.incrementAndGet();
                logger.warn("\u001B[31mAP FAILURE\u001B[0m: Failed to increment {}: {}", key, e.getMessage());
            }
            
            // Add a larger delay between operations to different DSMs
            sleepWithRandomJitter(BETWEEN_DSM_DELAY_MS);
            
            // CP DSM increment
            try {
                cpCounter++;
                cpDsm.write(key, String.valueOf(cpCounter));
                cpOperations.incrementAndGet();
                logger.debug("CP: Incremented {} to {}", key, cpCounter);
            } catch (DSMException e) {
                cpFailures.incrementAndGet();
                logger.warn("\u001B[31mCP FAILURE\u001B[0m: Failed to increment {}: {}", key, e.getMessage());
            }
            
            // Add a delay after all DSM operations to allow messages to propagate
            sleepWithRandomJitter(BETWEEN_DSM_DELAY_MS);
        }
        
        private void readAllCounters() {
            // Read all counters from all nodes in all DSMs
            for (int i = 0; i < NUM_NODES; i++) {
                String counterKey = "counter_" + i;
                
                // AP DSM read
                try {
                    String valueStr = apDsm.read(counterKey);
                    int value = valueStr != null ? Integer.parseInt(valueStr) : 0;
                    
                    // Check for inconsistencies
                    Integer lastValue = lastSeenApValues.get(counterKey);
                    if (lastValue != null && value < lastValue) {
                        logger.warn("\u001B[31mAP INCONSISTENCY\u001B[0m: Value rollback for {} - was {}, now {}", 
                                counterKey, lastValue, value);
                    }
                    
                    // If this is another node's counter, check for divergence from expected progress
                    if (i != nodeId && lastValue != null) {
                        if (value == lastValue) {
                            logger.warn("\u001B[33mAP POSSIBLE STALE\u001B[0m: No progress for {} since last check (still {})", 
                                    counterKey, value);
                        }
                    }
                    
                    lastSeenApValues.put(counterKey, value);
                } catch (DSMException e) {
                    apFailures.incrementAndGet(); // Track AP read failures
                    logger.warn("\u001B[31mAP FAILURE\u001B[0m: Failed to read {}: {}", counterKey, e.getMessage());
                }
                
                // CP DSM read
                try {
                    String valueStr = cpDsm.read(counterKey);
                    int value = valueStr != null ? Integer.parseInt(valueStr) : 0;
                    
                    // Check for inconsistencies
                    Integer lastValue = lastSeenCpValues.get(counterKey);
                    if (lastValue != null && value < lastValue) {
                        logger.warn("\u001B[31mCP INCONSISTENCY\u001B[0m: Value rollback for {} - was {}, now {}", 
                                counterKey, lastValue, value);
                    }
                    
                    lastSeenCpValues.put(counterKey, value);
                } catch (DSMException e) {
                    cpFailures.incrementAndGet(); // Track CP read failures
                    logger.warn("\u001B[31mCP FAILURE\u001B[0m: Failed to read {}: {}", 
                            counterKey, e.getMessage());
                    
                    // Add educational message about CAP theorem
                    if (e.getMessage().contains("Failed to reach") && e.getMessage().contains("quorum")) {
                        logger.info("\u001B[35mCAP INSIGHT\u001B[0m: CP prioritizes consistency over availability");
                    }
                }
                
                // CA DSM read
                try {
                    logger.info("CA: Attempting to read {}", counterKey);
                    String valueStr = caDsm.read(counterKey);
                    logger.info("CA: Successfully read {} = {}", counterKey, valueStr);
                    int value = valueStr != null ? Integer.parseInt(valueStr) : 0;
                    
                    // Check for inconsistencies
                    Integer lastValue = lastSeenCaValues.get(counterKey);
                    if (lastValue != null && value < lastValue) {
                        logger.warn("\u001B[31mCA INCONSISTENCY\u001B[0m: Value rollback for {} - was {}, now {}", 
                                counterKey, lastValue, value);
                    }
                    
                    lastSeenCaValues.put(counterKey, value);
                } catch (DSMException e) {
                    caFailures.incrementAndGet(); // Track CA read failures
                    logger.warn("\u001B[31mCA FAILURE\u001B[0m: Failed to read {}: {}", counterKey, e.getMessage());
                }
                
                // Add a delay between reading different counters to avoid overwhelming the coordinator
                sleepWithRandomJitter(100);
            }
            
            // Cross-check implementation divergence
            checkImplementationDivergence();
        }
        
        private void checkImplementationDivergence() {
            for (int i = 0; i < NUM_NODES; i++) {
                String counterKey = "counter_" + i;
                
                Integer apValue = lastSeenApValues.get(counterKey);
                Integer cpValue = lastSeenCpValues.get(counterKey);
                Integer caValue = lastSeenCaValues.get(counterKey);
                
                if (apValue != null && cpValue != null && Math.abs(apValue - cpValue) > 5) {
                    logger.info("\u001B[36mDIVERGENCE\u001B[0m: AP vs CP for {} - AP={}, CP={} (diff={})", 
                            counterKey, apValue, cpValue, Math.abs(apValue - cpValue));
                }
                
                if (apValue != null && caValue != null && Math.abs(apValue - caValue) > 5) {
                    logger.info("\u001B[36mDIVERGENCE\u001B[0m: AP vs CA for {} - AP={}, CA={} (diff={})", 
                            counterKey, apValue, caValue, Math.abs(apValue - caValue));
                }
                
                if (cpValue != null && caValue != null && Math.abs(cpValue - caValue) > 5) {
                    logger.info("\u001B[36mDIVERGENCE\u001B[0m: CP vs CA for {} - CP={}, CA={} (diff={})", 
                            counterKey, cpValue, caValue, Math.abs(cpValue - caValue));
                }
            }
        }
        
        private void reportStatus() {
            String myCounterKey = "counter_" + nodeId;
            
            // Report current counter values
            Integer apValue = lastSeenApValues.get(myCounterKey);
            Integer cpValue = lastSeenCpValues.get(myCounterKey);
            Integer caValue = lastSeenCaValues.get(myCounterKey);
            
            logger.info("STATUS: AP={}, CP={}, CA={}, Expected={}", 
                    apValue != null ? apValue : "N/A", 
                    cpValue != null ? cpValue : "N/A", 
                    caValue != null ? caValue : "N/A",
                    apCounter);
            
            // Check if my own counter readings match expected values
            if (apValue != null && apValue != apCounter) {
                logger.warn("\u001B[33mAP SELF-INCONSISTENCY\u001B[0m: Read {} but expected {}", 
                        apValue, apCounter);
            }
            
            if (cpValue != null && cpValue != cpCounter) {
                logger.warn("\u001B[33mCP SELF-INCONSISTENCY\u001B[0m: Read {} but expected {}", 
                        cpValue, cpCounter);
            }
            
            if (caValue != null && caValue != caCounter) {
                logger.warn("\u001B[33mCA SELF-INCONSISTENCY\u001B[0m: Read {} but expected {}", 
                        caValue, caCounter);
            }
        }
        
        private void sleepWithRandomJitter(int baseMs) {
            if (!Simulator.getInstance().isSimulating()) return;
            
            try {
                int jitter = ThreadLocalRandom.current().nextInt(RANDOM_DELAY_MS);
                Thread.sleep(baseMs + jitter);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
