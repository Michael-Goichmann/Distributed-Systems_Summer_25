package org.oxoo2a.sim4da;

import org.slf4j.Logger;

public abstract class Node {
    private final NetworkConnection nc;
    private final String name;

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
        return this.nc.receive();
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
}
