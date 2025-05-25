package org.oxoo2a.sim4da;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SimulatedProcessNode extends Node {

    private final int processId;
    private final int nProcesses;
    private final int kRoundsNoFirework;
    private double p; // Current probability
    private final double initialP;
    private final String nextNodeName;
    private boolean hasToken = false;
    private final AtomicBoolean terminateSignal = new AtomicBoolean(false);
    private final Random random = new Random();

    // P0 specific fields
    private int consecutiveRoundsWithoutFirework = 0;
    private boolean fireworkSeenInCurrentRound = false;
    private int totalRoundsCompleted = 0;
    private long roundStartTimeNanos = 0;
    private final List<Double> roundTimesMillis = Collections.synchronizedList(new ArrayList<>());

    public static volatile SimulatedProcessNode P0_INSTANCE = null;
    public static final AtomicInteger totalFireworksInSystem = new AtomicInteger(0);


    public SimulatedProcessNode(int processId, int nProcesses, int kRoundsNoFirework, double initialP) {
        super("Node_" + processId);
        this.processId = processId;
        this.nProcesses = nProcesses;
        this.kRoundsNoFirework = kRoundsNoFirework;
        this.initialP = initialP;
        this.p = initialP;
        this.nextNodeName = "Node_" + ((processId + 1) % nProcesses);

        if (processId == 0) {
            P0_INSTANCE = this;
            hasToken = true;
        }
    }

    @Override
    protected void engage() {
        getLogger().info("P{}: Engaging. n={}, k={}, p_initial={}. Next is {}.",
                processId, nProcesses, kRoundsNoFirework, initialP, nextNodeName);

        if (processId == 0 && hasToken) {
            getLogger().info("P{}: Starting with the token.", processId);
            roundStartTimeNanos = System.nanoTime();
            Message tokenMsg = new Message().add("type", "TOKEN");
            sendBlindly(tokenMsg, nextNodeName);
            hasToken = false;
        }

        while (!terminateSignal.get()) {
            Message receivedMsg = receive();

            if (receivedMsg == null && !Simulator.getInstance().isSimulating()) {
                getLogger().warn("P{}: Received null message and simulator is not running, likely shutdown. Terminating.", processId);
                break;
            }
            if (receivedMsg == null) {
                 getLogger().error("P{}: Received null message unexpectedly. Terminating.", processId);
                 break;
            }

            if (terminateSignal.get()) {
                break;
            }

            String msgType = receivedMsg.query("type");
            String senderName = receivedMsg.queryHeader("sender");

            getLogger().debug("P{}: Received '{}' from {}", processId, msgType, senderName);

            if ("TOKEN".equals(msgType)) {
                hasToken = true;
                processToken();
            } else if ("FIREWORK".equals(msgType)) {
                if (processId == 0) {
                    fireworkSeenInCurrentRound = true;
                    getLogger().debug("P0: Noted a FIREWORK broadcast during this round's observation window.");
                }
            } else if ("TERMINATE".equals(msgType)) {
                getLogger().info("P{}: Received TERMINATE from {}. Setting terminate flag and exiting.", processId, senderName);
                terminateSignal.set(true);
            } else {
                getLogger().warn("P{}: Received unknown message type '{}' from {}. Ignoring.", processId, msgType, senderName);
            }
        }
        getLogger().info("P{}: Exited engage loop. Terminated.", processId);
    }

    private void processToken() {
        if (terminateSignal.get()) return;

        getLogger().debug("P{}: Processing token. Current p={}", processId, p);

        if (processId == 0) {
            if (roundStartTimeNanos != 0) { 
                long roundEndTimeNanos = System.nanoTime();
                double durationMillis = (roundEndTimeNanos - roundStartTimeNanos) / 1_000_000.0;
                roundTimesMillis.add(durationMillis);
                totalRoundsCompleted++;
                getLogger().info("P0: Token returned. Round {} completed in {} ms.",
                        totalRoundsCompleted, durationMillis);

                // ---- TASK 4 MODIFICATION: Mechanism to potentially avoid inconsistency ----
                if (nProcesses > 1) {
                    final int SETTLING_TIME_MS = 5; 
                    getLogger().debug("P0 (Task 4): Pausing for {}ms to allow concurrent firework messages to be processed before round evaluation.", SETTLING_TIME_MS);
                    try {
                        Thread.sleep(SETTLING_TIME_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        getLogger().warn("P0 (Task 4): Sleep interrupted during settling time.");
                    }
                }
                // ---- END TASK 4 MODIFICATION ----

                if (!fireworkSeenInCurrentRound) {
                    consecutiveRoundsWithoutFirework++;
                    getLogger().info("P0: Round {} assessed as firework-free. Consecutive no-firework rounds: {}/{}",
                            totalRoundsCompleted, consecutiveRoundsWithoutFirework, kRoundsNoFirework);
                } else {
                    consecutiveRoundsWithoutFirework = 0;
                    getLogger().info("P0: Round {} had fireworks. Resetting no-firework count.", totalRoundsCompleted);
                }

                if (totalRoundsCompleted > 0 && consecutiveRoundsWithoutFirework >= kRoundsNoFirework) {
                    getLogger().info("P0: Termination condition met ({} consecutive rounds without firework). Broadcasting TERMINATE.", kRoundsNoFirework);
                    Message terminateMsg = new Message().add("type", "TERMINATE");
                    broadcast(terminateMsg);
                    terminateSignal.set(true);
                    return; 
                }
            }
            fireworkSeenInCurrentRound = false;
            roundStartTimeNanos = System.nanoTime(); 
        }

        if (random.nextDouble() < p) {
            launchFirework();
        }

        p /= 2.0;
        if (p < 1e-9) {
            p = 1e-9;
        }

        if (terminateSignal.get()) return; 

        Message tokenMsg = new Message().add("type", "TOKEN");
        getLogger().debug("P{}: Sending TOKEN to {}", processId, nextNodeName);
        sendBlindly(tokenMsg, nextNodeName);
        hasToken = false;
    }

    private void launchFirework() {
        getLogger().info("P{}: Launching FIREWORK! (current p={})", processId, p);
        totalFireworksInSystem.incrementAndGet();

        Message fireworkMsg = new Message().add("type", "FIREWORK");
        broadcast(fireworkMsg);

        if (processId == 0) {
            fireworkSeenInCurrentRound = true;
        }
    }

    public int getTotalRoundsCompleted() { return totalRoundsCompleted; }
    public List<Double> getRoundTimesMillis() { return new ArrayList<>(roundTimesMillis); }
}
