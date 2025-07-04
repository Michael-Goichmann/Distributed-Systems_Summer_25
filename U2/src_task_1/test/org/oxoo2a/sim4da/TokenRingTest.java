package org.oxoo2a.sim4da;

import org.junit.jupiter.api.Test;

class TokenRingTest {
    public static class TokenRingNode {
        public TokenRingNode( int id, int next_id ) {
            this.id = id;
            this.name = "TokenRingNode" + id;
            this.next_id = next_id;
            nc = new NetworkConnection(name);
        }

        public void engage () {
            nc.engage(this::start);
        }

        private void start() {
            Message m;
            if (id == 0) {
                m = new Message().add("token", 0);
                nc.sendBlindly(m, "TokenRingNode" + next_id);
            }
            int loop_counter = 0;
            while (true) {
                m = nc.receive();
                System.out.println("TokenRingNode" + id + " received message from " + m.queryHeader("sender"));
                loop_counter++;
                String value = m.query("token");
                if (value.equals("end")) {
                    nc.sendBlindly(m, "TokenRingNode" + next_id);
                    break;
                }
                if (loop_counter > 10) {
                    m = new Message().add("token", "end");
                    nc.sendBlindly(m, "TokenRingNode" + next_id);
                    break;
                }
                int v = m.queryInteger("token");
                System.out.println(name + " received token " + value);
                m = new Message().add("token", v + 1);
                nc.sendBlindly(m, "TokenRingNode" + next_id);
            }
        }
        private final NetworkConnection nc;
        private final String name;
        private final int id;
        private final int next_id;
    }

    @Test
    void testTokenRing() {
        final int ringSize = 5;
        Simulator simulator = Simulator.getInstance();
        TokenRingNode[] nodes = new TokenRingNode[ringSize];
        for (int i = 0; i < ringSize; i++) {
            nodes[i] = new TokenRingNode(i, (i+1) % ringSize);
        }
        for (TokenRingNode n : nodes) {
            n.engage();
        }
        simulator.simulate(2);
        simulator.shutdown();
    }
}
