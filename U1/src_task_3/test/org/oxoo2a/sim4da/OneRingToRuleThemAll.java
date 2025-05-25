package org.oxoo2a.sim4da;

import org.junit.jupiter.api.Test;

public class OneRingToRuleThemAll {

    class Coordinator {
        private final int waitTime;

        public Coordinator( int waitTime) {
            this.waitTime = waitTime;
        }

        public void engage () {
            nc.engage(this::start);
        }

        private void start() {
            Message m = new Message().add("token", 0);
            nc.sendBlindly(m, "0");
            try {
                Thread.sleep(waitTime);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
            m = new Message().add("token", "end");
            nc.sendBlindly(m, "0");
        }

        private final NetworkConnection nc = new NetworkConnection("Coordinator");
    }

    class RingSegment extends Node {
        public RingSegment(int id, int next_id) {
            super(String.valueOf(id));
            this.id = String.valueOf(id);
            this.next_id = String.valueOf(next_id);
        }

        @Override
        public void engage() {
            Message m;
            while (true) {
                m = receive();
                // System.out.printf("Ring segment %s received message from %s\n", NodeName(), m.queryHeader("sender"));
                String value = m.query("token");
                if (value.equals("end")) {
                    // System.out.printf("Ring segment %s received end message; terminating.\n", NodeName());
                    sendBlindly(m, next_id);
                    break;
                }
                // sleep(500);
                int v = m.queryInteger("token");
                // System.out.printf("Ring segment %s received token %d\n", NodeName(), v);
                m = new Message().add("token", v + 1);
                sendBlindly(m, next_id);
            }
        }

        private final String id;
        private final String next_id;
    }

    @Test
    void testOneRingToRuleThemAll() {
        final int ringSize = 20;
        Simulator simulator = Simulator.getInstance();
        RingSegment[] segments = new RingSegment[ringSize];
        for (int i = 0; i < ringSize; i++) {
            segments[i] = new RingSegment(i, (i+1) % ringSize);
        }
        Coordinator coordinator = new Coordinator(5000);
        coordinator.engage();

        simulator.simulate();
        simulator.shutdown();
    }
}
