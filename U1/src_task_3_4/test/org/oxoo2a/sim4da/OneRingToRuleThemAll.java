package org.oxoo2a.sim4da;

import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class OneRingToRuleThemAll {

    @Test
    void testTask1SimulationExperiment() {
        String nValuesStr = "16";
        int kRoundsNoFirework = 3;
        double pInitial = 0.5;
        String outputCsvPath = "experiment_results_task3_junit_larger.csv";
        int repetitions = 1;

        System.out.println("Starting Task 1 Simulation Experiment via JUnit Test.");
        System.out.printf("Parameters: n_values=%s, k=%d, p_initial=%.4f, repetitions=%d, output_csv=%s%n",
                nValuesStr, kRoundsNoFirework, pInitial, repetitions, outputCsvPath);

        List<Integer> nValues;
        try {
            nValues = Arrays.stream(nValuesStr.split(","))
                    .map(s -> s.trim())
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            System.err.println("Error parsing n_values: " + e.getMessage());
            org.junit.jupiter.api.Assertions.fail("Error parsing n_values: " + e.getMessage());
            return;
        }

        java.io.File csvFile = new java.io.File(outputCsvPath);
        if (csvFile.getParentFile() != null) {
            csvFile.getParentFile().mkdirs();
        }

        try (PrintWriter csvWriter = new PrintWriter(new FileWriter(csvFile, false))) {
            csvWriter.println("n_processes,repetition_id,k_val,p_initial_val,total_rounds_completed,total_fireworks_launched,min_round_time_ms,avg_round_time_ms,max_round_time_ms,run_successful");

            for (int n : nValues) {
                for (int repId = 1; repId <= repetitions; repId++) {
                    System.out.printf("%nRunning experiment for n = %d, Repetition %d/%d%n", n, repId, repetitions);

                    SimulatedProcessNode.P0_INSTANCE = null;
                    SimulatedProcessNode.totalFireworksInSystem.set(0);

                    Simulator simulator = Simulator.getInstance();

                    for (int i = 0; i < n; i++) {
                        new SimulatedProcessNode(i, n, kRoundsNoFirework, pInitial);
                    }

                    long wallClockStartTime = System.currentTimeMillis();
                    simulator.simulate();
                    long wallClockEndTime = System.currentTimeMillis();
                    System.out.printf("    Simulation for n=%d, rep=%d finished in %.3f seconds (wall clock).%n",
                            n, repId, (wallClockEndTime - wallClockStartTime) / 1000.0);

                    SimulatedProcessNode p0 = SimulatedProcessNode.P0_INSTANCE;
                    boolean success = false;
                    int totalRounds = 0;
                    int totalFireworks = 0;
                    double minTime = 0, avgTime = 0, maxTime = 0;

                    if (p0 != null) {
                        success = true;
                        totalRounds = p0.getTotalRoundsCompleted();
                        totalFireworks = SimulatedProcessNode.totalFireworksInSystem.get();
                        List<Double> roundTimes = p0.getRoundTimesMillis();

                        if (roundTimes != null && !roundTimes.isEmpty()) {
                            minTime = Collections.min(roundTimes);
                            maxTime = Collections.max(roundTimes);
                            avgTime = roundTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                        } else if (totalRounds > 0) {
                            System.out.printf("    Warning: P0 reported %d rounds but no round times for n=%d, rep=%d.%n",
                                    totalRounds, n, repId);
                        }
                        System.out.printf("    P0 Results: Rounds=%d, Fireworks=%d, MinT=%.2fms, AvgT=%.2fms, MaxT=%.2fms%n",
                                totalRounds, totalFireworks, minTime, avgTime, maxTime);
                    } else {
                        System.err.printf("    ERROR: P0 instance not found after simulation for n=%d, rep=%d.%n", n, repId);
                        success = false;
                        // In a JUnit test, you might want to fail the test here if P0 is crucial
                        // org.junit.jupiter.api.Assertions.fail("P0 instance was null for n=" + n + ", rep=" + repId);
                    }

                    // Format the string using Locale.US to ensure '.' as decimal separator
                    String csvLine = String.format(Locale.US, "%d,%d,%d,%.4f,%d,%d,%.2f,%.2f,%.2f,%b",
                            n, repId, kRoundsNoFirework, pInitial,
                            totalRounds, totalFireworks,
                            minTime, avgTime, maxTime,
                            success);
                    csvWriter.println(csvLine); // Use println to write the formatted string
                    csvWriter.flush();

                    // Crucially, shutdown and reset the simulator for the next n value in the loop
                    simulator.shutdown();
                    System.out.printf("    Simulator shutdown complete for n=%d, rep=%d.%n", n, repId);
                }
            }
            System.out.println("\nAll experiments finished. Data saved to " + outputCsvPath);

        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
            e.printStackTrace();
            org.junit.jupiter.api.Assertions.fail("IOException during CSV writing: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("An unexpected error occurred during the test: " + e.getMessage());
            e.printStackTrace();
            org.junit.jupiter.api.Assertions.fail("Unexpected exception during test: " + e.getMessage());
        }
    }
}
