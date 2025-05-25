package org.oxoo2a.sim4da;

import org.junit.jupiter.api.Test;

class TokenRingWithNodeTest {
    public static class TokenRingNode extends Node {
        public TokenRingNode( String name, String next_node ) {
            super(name);
            this.next_node = next_node;
        }
        @Override
        protected void engage() {
            Message m;
            if (NodeName().equals("TokenRingNode_0")) {
                m = new Message().add("token", 0);
                sendBlindly(m, next_node);
            }
            int loop_counter = 0;
            while (true) {
                m = receive();
                System.out.println(NodeName() + " received message from " + m.queryHeader("sender"));
                loop_counter++;
                String value = m.query("token");
                if (value.equals("end")) {
                    sendBlindly(m, next_node);
                    break;
                }
                if (loop_counter > 10) {
                    m = new Message().add("token", "end");
                    sendBlindly(m, next_node);
                    break;
                }
                int v = m.queryInteger("token");
                System.out.println(NodeName() + " received token " + value);
                m = new Message().add("token", v + 1);
                sleep(100);
                sendBlindly(m, next_node);
            }
        }
        private final String next_node;
    }

    @Test
    void testTokenRingWithNode() {
        final int ringSize = 5;
        Simulator simulator = Simulator.getInstance();
        TokenRingNode[] nodes = new TokenRingNode[ringSize];
        for (int i = 0; i < ringSize; i++) {
            String node_name = "TokenRingNode_" + i;
            String next_node = "TokenRingNode_" + (i+1) % ringSize;
            nodes[i] = new TokenRingNode(node_name, next_node);
            nodes[i].sleep(1000);
        }
        simulator.simulate(2);
        simulator.shutdown();
    }
}
