# U1 - Distributed Systems Exercises

This folder contains implementations for programming exercises related to distributed systems concepts, focusing on the CAP Theorem and distributed shared memory approaches.

## General Prerequisites

*   **Java (JDK 8 or higher)**: Required for Task 1 & 2.
*   **Apache Maven**: Required for building and running Task 1 & 2.

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

Task 1 & 2: DSM Implementations and Inconsistency checks

Uses multiple distributed shared memory implementations which extend the sim4da java implementation and evaluates the code by using individual mini applications.

**Location:** `U2/src_task_2`

**To run the applications to test the CAP Variants:**

Navigate to the `U2/src_task_1/` directory.

Use one of the following commands in a terminal of your choice:
```bash
mvn clean compile exec:java -P counter-demo
```
```bash
mvn clean compile exec:java -P concurrent-write-demo
```
```bash
mvn clean compile exec:java -P partition-simulator-demo
```