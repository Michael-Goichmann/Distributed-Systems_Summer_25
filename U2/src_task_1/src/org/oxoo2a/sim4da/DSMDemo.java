package org.oxoo2a.sim4da;

import org.oxoo2a.sim4da.dsm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demonstration of the Distributed Shared Memory (DSM) implementations.
 */
public class DSMDemo {
    private static final Logger logger = LoggerFactory.getLogger(DSMDemo.class);
    
    /**
     * Main method to run the DSM demonstration.
     */
    public static void main(String[] args) {
        logger.info("Starting DSM Demo");
        
        // Create a simulation with 5 nodes
        for (int i = 0; i < 5; i++) {
            new DSMDemoNode("Node_" + i);
        }
        
        // Run the simulation
        Simulator simulator = Simulator.getInstance();
        simulator.simulate(10); // Run for 10 seconds
        simulator.shutdown();
        
        logger.info("DSM Demo completed");
    }
    
    /**
     * Demo node that uses the different DSM implementations.
     */
    private static class DSMDemoNode extends Node {
        private final Logger logger;
        
        public DSMDemoNode(String name) {
            super(name);
            this.logger = LoggerFactory.getLogger(name);
        }
        
        @Override
        protected void engage() {
            logger.info("{} started", NodeName());
            
            try {
                // Test AP DSM (Availability & Partition Tolerance)
                testAPDsm();
                
                // Test CP DSM (Consistency & Partition Tolerance)
                testCPDsm();
                
                // Test CA DSM (Consistency & Availability)
                testCADsm();
                
            } catch (Exception e) {
                logger.error("Error in DSM demo: {}", e.getMessage(), e);
            }
            
            logger.info("{} completed DSM operations", NodeName());
        }
        
        private void testAPDsm() {
            logger.info("{} testing AP DSM...", NodeName());
            DSM apDsm = getDSM(DSMFactory.DSMType.AP);
            
            try {
                // Write a value
                String key = "ap_key_" + NodeName();
                String value = "ap_value_" + System.currentTimeMillis();
                apDsm.write(key, value);
                
                // Wait a bit for propagation
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                
                // Read back the value
                String readValue = apDsm.read(key);
                logger.info("{} AP DSM read {}={}", NodeName(), key, readValue);
                
                // Try reading a value written by another node (may be null if not propagated yet)
                String otherKey = "ap_key_Node_0";
                if (!NodeName().equals("Node_0")) {
                    String otherValue = apDsm.read(otherKey);
                    logger.info("{} AP DSM read {}={}", NodeName(), otherKey, otherValue);
                }
            } catch (DSMException e) {
                logger.warn("{} AP DSM operation failed: {}", NodeName(), e.getMessage());
            }
        }
        
        private void testCPDsm() {
            logger.info("{} testing CP DSM...", NodeName());
            DSM cpDsm = getDSM(DSMFactory.DSMType.CP);
            
            try {
                // Write a value
                String key = "cp_key_" + NodeName();
                String value = "cp_value_" + System.currentTimeMillis();
                cpDsm.write(key, value);
                
                // Read back the value
                String readValue = cpDsm.read(key);
                logger.info("{} CP DSM read {}={}", NodeName(), key, readValue);
                
                // Try reading a value written by another node
                String otherKey = "cp_key_Node_0";
                if (!NodeName().equals("Node_0")) {
                    String otherValue = cpDsm.read(otherKey);
                    logger.info("{} CP DSM read {}={}", NodeName(), otherKey, otherValue);
                }
            } catch (DSMException e) {
                logger.warn("{} CP DSM operation failed: {}", NodeName(), e.getMessage());
            }
        }
        
        private void testCADsm() {
            logger.info("{} testing CA DSM...", NodeName());
            DSM caDsm = getDSM(DSMFactory.DSMType.CA);
            
            try {
                // Write a value
                String key = "ca_key_" + NodeName();
                String value = "ca_value_" + System.currentTimeMillis();
                caDsm.write(key, value);
                
                // Read back the value
                String readValue = caDsm.read(key);
                logger.info("{} CA DSM read {}={}", NodeName(), key, readValue);
                
                // Try reading a value written by another node
                String otherKey = "ca_key_Node_0";
                if (!NodeName().equals("Node_0")) {
                    String otherValue = caDsm.read(otherKey);
                    logger.info("{} CA DSM read {}={}", NodeName(), otherKey, otherValue);
                }
            } catch (DSMException e) {
                logger.warn("{} CA DSM operation failed: {}", NodeName(), e.getMessage());
            }
        }
    }
}