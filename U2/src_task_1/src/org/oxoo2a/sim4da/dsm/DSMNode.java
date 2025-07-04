package org.oxoo2a.sim4da.dsm;

import org.oxoo2a.sim4da.Message;

/**
 * Interface that defines DSM-specific operations that a Node must support.
 * This allows DSM implementations to access protected Node methods indirectly.
 */
public interface DSMNode {
    
    /**
     * Broadcasts a DSM message to all nodes in the network.
     * This is a wrapper around the protected broadcast method in Node.
     * 
     * @param message The message to broadcast
     */
    void sendDSMBroadcast(Message message);
    
    /**
     * Sends a DSM message to a specific node.
     * This is a wrapper around the protected sendBlindly method in Node.
     * 
     * @param message The message to send
     * @param toNodeName The name of the target node
     */
    void sendDSMMessage(Message message, String toNodeName);
}
