package org.oxoo2a.sim4da;

import org.junit.jupiter.api.Test;

class TerminatingRingTest {
    public static class RingNode extends Node {
        public RingNode(String name, String next_node ) {
            super(name);
            this.next_node = next_node;
        }
        @Override
        protected void engage() {
            Message m;
            if (NodeName().equals("RingNode_0")) {
                m = new Message().add("token", 0);
                sendBlindly(m, next_node);
            }
            while (true) {
                m = receive();
                System.out.println(NodeName() + " received message from " + m.queryHeader("sender"));
                int v = m.queryInteger("token");
                System.out.println(NodeName() + " received token " + v);
                m = new Message().add("token", v + 1);
                sleep(100);
                sendBlindly(m, next_node);
                if (v > 100) {
                    break;
                }
            }
        }
        private final String next_node;
    }

    @Test
    void testTokenRingWithNode() {
        final int ringSize = 5;
        Simulator simulator = Simulator.getInstance();
        RingNode[] nodes = new RingNode[ringSize];
        for (int i = 0; i < ringSize; i++) {
            String node_name = "RingNode_" + i;
            String next_node = "RingNode_" + (i+1) % ringSize;
            nodes[i] = new RingNode(node_name, next_node);
        }
        simulator.simulate();
        simulator.shutdown();
    }
}
