package org.oxoo2a.sim4da.dsm;

import org.oxoo2a.sim4da.Node;

/**
 * Factory for creating DSM implementations.
 */
public class DSMFactory {
    /**
     * DSM implementation variants
     */
    public enum DSMType {
        AP, // Availability & Partition Tolerance
        CP, // Consistency & Partition Tolerance
        CA  // Consistency & Availability
    }
    
    /**
     * Creates a DSM instance of the specified type.
     * 
     * @param type The type of DSM implementation to create
     * @param node The node using this DSM instance
     * @return A DSM instance
     */
    public static DSM createDSM(DSMType type, Node node) {
        switch (type) {
            case AP:
                return new APDistributedSharedMemory(node);
            case CP:
                return new CPDistributedSharedMemory(node);
            case CA:
                return new CADistributedSharedMemory(node);
            default:
                throw new IllegalArgumentException("Unknown DSM type: " + type);
        }
    }
}
