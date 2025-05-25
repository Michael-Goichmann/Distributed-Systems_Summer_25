package org.oxoo2a.sim4da;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class Simulator {
    private final String version = "sim4da Summer 2025";
    private  Simulator () {
        System.setProperty("PID", String.valueOf(ProcessHandle.current().pid())); 
        logger = LoggerFactory.getLogger(sim4da.class); 
        System.out.println(version);
        logger.info(version + " - Simulation instance created.");
    }

    public static Simulator getInstance() {
        if (instance == null) {
            synchronized (Simulator.class) {
                if (instance == null) {
                    instance = new Simulator();
                }
            }
        }
        return instance;
    }

    private void prepareForSimulation() {
        if (startSignal == null || startSignal.getCount() == 0) {
            startSignal = new CountDownLatch(1);
        }
        simulating = true;
        logger.info("Simulator prepared for new simulation run.");
    }

    public void simulate ( long duration_in_seconds ) {
        prepareForSimulation();
        logger.info("Starting timed simulation for " + duration_in_seconds + " seconds.");
        startSignal.countDown(); 
        try {
            Thread.sleep(duration_in_seconds * 1000);
        } catch (InterruptedException e) {
            logger.warn("Simulation sleep interrupted, possibly due to early termination request or external interrupt.");
            Thread.currentThread().interrupt(); 
        } finally {
            simulating = false;
            logger.info("Timed simulation duration ended.");
        }
    }

    public void simulate () {
        prepareForSimulation();
        logger.info("Starting simulation. Waiting for all nodes to complete.");
        startSignal.countDown(); 

        List<NetworkConnection> ncs = Network.getInstance().getAllNetworkConnections();
        if (ncs.isEmpty()) {
            logger.warn("Simulate called with no registered network connections (nodes). Simulation will end immediately.");
            simulating = false;
            return;
        }

        for (NetworkConnection nc : ncs) {
            nc.join(); 
        }
        simulating = false; 
        logger.info("All node threads have completed.");
    }

    public void shutdown () {
        logger.info("Shutting down simulation environment.");
        simulating = false;

        List<NetworkConnection> ncs = Network.getInstance().getAllNetworkConnections();
        if (!ncs.isEmpty()) {
            logger.info("Interrupting " + ncs.size() + " node threads.");
            for (NetworkConnection nc : ncs) {
                nc.interrupt();
            }
            logger.info("Joining node threads with timeout.");
            for (NetworkConnection nc : ncs) {
                nc.join();
            }
        } else {
            logger.info("No active node threads to interrupt or join.");
        }
        
        Network.getInstance().shutdown(); 
        
        startSignal = new CountDownLatch(1);
        logger.info(version + " - Simulation environment shut down and reset.");
    }

    public boolean isSimulating() {
        return simulating;
    }
    private static Simulator instance = null;
    private final Logger logger;
    private boolean simulating = false;
    private CountDownLatch startSignal = new CountDownLatch(1);

    public void awaitSimulationStart() {
        if (simulating && startSignal != null && startSignal.getCount() == 0) {
            return;
        }
        try {
            if (startSignal != null) {
                logger.trace("Thread " + Thread.currentThread().getName() + " awaiting simulation start.");
                startSignal.await();
                logger.trace("Thread " + Thread.currentThread().getName() + " released from awaitSimulationStart.");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Thread " + Thread.currentThread().getName() + " interrupted while awaiting simulation start.");
        }
    }
}