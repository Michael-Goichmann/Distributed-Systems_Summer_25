import subprocess
import time
import argparse
import sys

def main():
    parser = argparse.ArgumentParser(description="Launch multiple token ring processes.")
    parser.add_argument("n", type=int, help="Total number of processes")
    parser.add_argument("k", type=int, help="Rounds without fireworks to terminate")
    parser.add_argument("p_initial", type=float, help="Initial probability p")
    args = parser.parse_args()

    if args.n <= 0:
        print("Error: Number of processes (n) must be positive.")
        return
    if args.k <= 0:
        print("Error: k (rounds for termination) must be positive.")
        return
    if not (0.0 <= args.p_initial <= 1.0):
        print("Error: Initial probability p must be between 0.0 and 1.0.")
        return

    processes = []
    python_executable = sys.executable # Get the current Python interpreter

    print(f"Launching {args.n} processes for task_1.py with k={args.k}, p_initial={args.p_initial}")

    for i in range(args.n):
        cmd = [
            python_executable,
            "U1/task_1.py",
            str(i),
            str(args.n),
            str(args.k),
            str(args.p_initial)
        ]
        # On Windows, use CREATE_NEW_CONSOLE to open each process in a new window
        # On Linux/macOS, you might want to run them in the background (e.g., using Popen without CREATE_NEW_CONSOLE)
        # or manage their output differently.
        if sys.platform == "win32":
            process = subprocess.Popen(cmd, creationflags=subprocess.CREATE_NEW_CONSOLE)
        else:
            # For non-Windows, Popen will run them in the background.
            # Their output will be mixed in the terminal running launcher.py.
            # You might want to redirect output to files if needed.
            process = subprocess.Popen(cmd)
        processes.append(process)
        print(f"Launched P{i} with PID {process.pid}")
        # Small delay to help with orderly startup, especially socket binding.
        # The task_1.py script already has a small delay, but this adds another layer.
        time.sleep(0.1)

    print(f"\nAll {args.n} processes launched. They will run until termination condition is met.")
    print("You can monitor their individual consoles (if on Windows) or the output in this terminal (Linux/macOS).")
    print("To stop them prematurely, you would need to manually terminate each process (e.g., close their windows or kill their PIDs).")

    # Optionally, wait for all processes to complete if you want the launcher to block
    # for p in processes:
    #     p.wait()
    # print("All processes have terminated.")

if __name__ == "__main__":
    main()