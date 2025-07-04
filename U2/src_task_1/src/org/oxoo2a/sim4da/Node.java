package org.oxoo2a.sim4da;

import org.oxoo2a.sim4da.dsm.*;
import org.slf4j.Logger;

public abstract class Node implements DSMNode {
    private final NetworkConnection nc;
    private final String name;
    
    // DSM instances for each implementation type
    private APDistributedSharedMemory apDsm;
    private CPDistributedSharedMemory cpDsm;
    private CADistributedSharedMemory caDsm;

    public Node(String name) {
        this.name = name;
        this.nc = new NetworkConnection(name); // NetworkConnection is created here
        // The engage method, which you implement in SimulatedProcessNode,
        // will be called by NetworkConnection after the simulation starts.
        this.nc.engage(this::engage);
    }

    public String NodeName() {
        return this.name;
    }

    /**
     * This method is implemented by the user's specific node class (e.g., SimulatedProcessNode).
     * It contains the main logic of the node.
     */
    protected abstract void engage();

    /**
     * Retrieves the logger for this node.
     * @return Logger instance for this node.
     */
    protected Logger getLogger() {
        return this.nc.getLogger(); // Delegates to NetworkConnection's logger
    }

    /**
     * Receives a message. This is a blocking call.
     * @return The received message, or null if interrupted or simulation ends.
     */
    protected Message receive() {
        Message message = this.nc.receive();
        
        // Process DSM-related messages if applicable
        if (message != null) {
            String type = message.query("type");
            
            // Handle AP DSM messages
            if (apDsm != null && "DSM_AP_UPDATE".equals(type)) {
                apDsm.processUpdateMessage(message);
            }
            
            // Handle CP DSM messages
            if (cpDsm != null) {
                switch (type) {
                    case "DSM_CP_WRITE_REQUEST":
                        cpDsm.processWriteRequest(message);
                        break;
                    case "DSM_CP_WRITE_ACK":
                        String writeAckRequestId = message.query("requestId");
                        String writeAckSender = message.queryHeader("sender");
                        cpDsm.processWriteAck(writeAckRequestId, writeAckSender);
                        break;
                    case "DSM_CP_WRITE_NACK":
                        cpDsm.processWriteNack(message);
                        break;
                    case "DSM_CP_READ_REQUEST":
                        cpDsm.processReadRequest(message);
                        break;
                    case "DSM_CP_READ_RESPONSE":
                        String readResponseRequestId = message.query("requestId");
                        String readResponseSender = message.queryHeader("sender");
                        String readResponseKey = message.query("key");
                        String readResponseValue = message.query("value");
                        if (readResponseValue.isEmpty()) readResponseValue = null;
                        cpDsm.processReadResponse(readResponseRequestId, readResponseSender, readResponseKey, readResponseValue);
                        break;
                    case "DSM_CP_READ_ERROR":
                        cpDsm.processReadError(message);
                        break;
                }
            }
            
            // Handle CA DSM messages
            if (caDsm != null) {
                switch (type) {
                    case "DSM_CA_WRITE_REQUEST":
                        caDsm.processWriteRequest(message);
                        break;
                    case "DSM_CA_READ_REQUEST":
                        caDsm.processReadRequest(message);
                        break;
                    case "DSM_CA_UPDATE":
                        caDsm.processUpdate(message);
                        break;
                    case "DSM_CA_READ_RESPONSE":
                        caDsm.processReadResponse(message);
                        break;
                    case "DSM_CA_ERROR":
                        caDsm.processError(message);
                        break;
                }
            }
        }
        
        return message;
    }

    /**
     * Sends a message to a specific node.
     * @param message The message to send.
     * @param to_node_name The name of the recipient node.
     * @throws UnknownNodeException If the recipient node name is not registered in the network.
     */
    protected void send(Message message, String to_node_name) throws UnknownNodeException {
        this.nc.send(message, to_node_name);
    }

    /**
     * Sends a message to a specific node, ignoring any exceptions (like UnknownNodeException).
     * This is often used when the sender is sure the recipient exists or doesn't want to handle the exception.
     * @param message The message to send.
     * @param to_node_name The name of the recipient node.
     */
    protected void sendBlindly(Message message, String to_node_name) {
        this.nc.sendBlindly(message, to_node_name);
    }

    /**
     * Broadcasts a message to all other nodes in the network.
     * The message is not sent to the sender itself.
     * @param message The message to broadcast.
     */
    protected void broadcast(Message message) {
        this.nc.send(message); // NetworkConnection's send(Message) is broadcast
    }
    
    /**
     * Gets a DSM instance of the specified type.
     * 
     * @param type The type of DSM implementation to use
     * @return A DSM instance
     */
    protected DSM getDSM(DSMFactory.DSMType type) {
        switch (type) {
            case AP:
                if (apDsm == null) {
                    apDsm = (APDistributedSharedMemory) DSMFactory.createDSM(DSMFactory.DSMType.AP, this);
                    apDsm.initialize(this.name);
                }
                return apDsm;
            case CP:
                if (cpDsm == null) {
                    cpDsm = (CPDistributedSharedMemory) DSMFactory.createDSM(DSMFactory.DSMType.CP, this);
                    cpDsm.initialize(this.name);
                }
                return cpDsm;
            case CA:
                if (caDsm == null) {
                    caDsm = (CADistributedSharedMemory) DSMFactory.createDSM(DSMFactory.DSMType.CA, this);
                    caDsm.initialize(this.name);
                }
                return caDsm;
            default:
                throw new IllegalArgumentException("Unknown DSM type: " + type);
        }
    }
    
    /**
     * Shuts down all DSM instances for this node.
     */
    protected void shutdownDSM() {
        if (apDsm != null) {
            apDsm.shutdown();
            apDsm = null;
        }
        if (cpDsm != null) {
            cpDsm.shutdown();
            cpDsm = null;
        }
        if (caDsm != null) {
            caDsm.shutdown();
            caDsm = null;
        }
    }
    
    // DSMNode interface implementation
    
    @Override
    public void sendDSMBroadcast(Message message) {
        broadcast(message);
    }
    
    @Override
    public void sendDSMMessage(Message message, String toNodeName) {
        sendBlindly(message, toNodeName);
    }
}
