# U1 - Distributed Systems Exercises

This folder contains implementations for programming exercises related to distributed systems concepts, focusing on token ring algorithms, inter-process communication, and consistency.

## General Prerequisites

*   **Python 3.x**: Required for Task 1 and Task 2. Standard libraries are used.
*   **Java (JDK 8 or higher)**: Required for Task 3 & 4.
*   **Apache Maven**: Required for building and running Task 3 & 4.

## Task 0: Getting started

This project uses the uv package manager. If you do not have uv installed, please
install it first with:

macOS / Linux
``` 
curl -LsSf https://astral.sh/uv/install.sh | sh
```

Windows
```
powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"
```
To install dependencies as defined in `uv.lock`
```
uv sync
```

## Task 1: Local Token Ring Simulation (Python)

Simulates a token ring algorithm locally using UDP sockets for communication.

**Location:** `U1/src_task_1/`

**To Run:**

Navigate to the `U1/src_task_1/` directory. The `task_1_launcher.py` script is used to run experiments.

```bash
# Example: Run for n=2,4,8 processes, k=3, p_initial=0.5, 1 repetition, output to results.csv
python task_1_launcher.py "2,4,8" 3 0.5 1 experiment_results_task1.csv
```

**Command Arguments for `task_1_launcher.py`:**
1.  `n_values`: Comma-separated string of process counts (e.g., "2,4,8,16").
2.  `k`: Integer, number of rounds without fireworks to terminate.
3.  `p_initial`: Float, initial probability for launching a firework.
4.  `repetitions`: Integer, number of times to repeat the experiment for each `n` value.
5.  `output_csv`: String, path to the output CSV file for results.

## Task 2: Distributed Token Ring (Python)

Extends Task 1 to run on two separate machines (P0 on Machine A, P1 on Machine B).

**Location:** `U1/src_task_2/`

**To Run:**

1.  **On Machine A (where P0 and the launcher will run):**
    Navigate to the `U1/src_task_2/` directory.
    ```bash
    # Example: k=3, p_initial=0.5, 1 repetition, IP_A, IP_B, output to results_task2.csv
    python task_2_launcher.py 3 0.5 1 <IP_MACHINE_A> <IP_MACHINE_B> experiment_results_task2.csv
    ```
    The launcher will start P0 and then provide the command to run P1 on Machine B.

2.  **On Machine B (where P1 will run):**
    Ensure `task_2.py` is present on Machine B. Execute the command provided by the launcher on Machine A. It will look similar to this:
    ```bash
    python task_2.py 1 2 3 0.5 <IP_MACHINE_B> <IP_MACHINE_A>
    ```
    (The exact parameters will be given by the launcher).

**Command Arguments for `task_2_launcher.py`:**
1.  `k`: Integer, rounds without fireworks to terminate.
2.  `p_initial`: Float, initial probability.
3.  `repetitions`: Integer, number of repetitions.
4.  `ip_machine_a`: String, IP address of Machine A.
5.  `ip_machine_b`: String, IP address of Machine B.
6.  `output_csv`: String, path to the output CSV file.

## Task 3 & 4: Token Ring Simulation (Java with sim4da) & Consistency

Re-implements the token ring simulation using the `sim4da` Java framework and includes mechanisms to address potential consistency issues.

**Location:** `U1/src_task_3_4/`

**To Build:**

Navigate to the `U1/src_task_3_4/` directory.
```bash
mvn clean compile test-compile
```

**To Run Experiments (Task 3 & 4 logic is integrated):**

The experiments are run via a JUnit test.
```bash
# From the U1/src_task_3_4/ directory
mvn test -Dtest=OneRingToRuleThemAll#testTask1SimulationExperiment
```
Results will be saved to `experiment_results_task3_junit_larger.csv` (or as configured in `OneRingToRuleThemAll.java`). The test parameters (like `n` values, `k`, `p_initial`) can be modified directly within the `OneRingToRuleThemAll.java` test file.
