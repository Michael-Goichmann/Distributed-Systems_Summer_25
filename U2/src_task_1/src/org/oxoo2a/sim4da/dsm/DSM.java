package org.oxoo2a.sim4da.dsm;

/**
 * Interface for Distributed Shared Memory (DSM) implementations.
 * Defines the basic operations that any DSM implementation must support.
 */
public interface DSM {
    /**
     * Writes a value to the specified key in the distributed shared memory.
     * 
     * @param key The key to write to
     * @param value The value to store
     * @throws DSMException If the operation cannot be completed due to implementation-specific constraints
     */
    void write(String key, String value) throws DSMException;
    
    /**
     * Reads a value for the specified key from the distributed shared memory.
     * 
     * @param key The key to read
     * @return The value associated with the key, or null if the key doesn't exist
     * @throws DSMException If the operation cannot be completed due to implementation-specific constraints
     */
    String read(String key) throws DSMException;
    
    /**
     * Initializes this DSM instance for a specific node.
     * 
     * @param nodeName The name of the node using this DSM instance
     */
    void initialize(String nodeName);
    
    /**
     * Shuts down this DSM instance, releasing any resources.
     */
    void shutdown();
}
