import subprocess
import time
import argparse
import sys
import os # Added for path manipulation

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
    
    # Determine the directory of the launcher script
    launcher_dir = os.path.dirname(os.path.abspath(__file__))
    # Construct the path to task_1.py (assuming it's in the same directory as the launcher)
    # If task_1.py is in a subdirectory like 'U1' relative to the project root,
    # and the launcher is also in 'U1', then task_1_script_path = os.path.join(launcher_dir, "task_1.py")
    # If launcher is in project root and task_1.py is in U1, then:
    # task_1_script_path = os.path.join(launcher_dir, "U1", "task_1.py")
    # Adjust this path as per your project structure. For your current setup:
    task_1_script_path = os.path.join(os.path.dirname(launcher_dir), "U1", "task_1.py")
    # A simpler way if launcher.py and task_1.py are in the same U1 folder:
    # task_1_script_path = os.path.join(launcher_dir, "task_1.py")
    # Given your command `python U1/task_1_launcher.py`, it implies launcher is in U1.
    # So, let's assume task_1.py is also in U1.
    task_1_script_path = os.path.join(launcher_dir, "task_1.py") # Corrected path assumption

    # Create a directory for logs if it doesn't exist
    logs_dir = os.path.join(launcher_dir, "process_logs")
    os.makedirs(logs_dir, exist_ok=True)

    print(f"Launching {args.n} processes for {task_1_script_path} with k={args.k}, p_initial={args.p_initial}")
    print(f"Logs will be stored in: {logs_dir}")

    for i in range(args.n):
        cmd = [
            python_executable,
            task_1_script_path,
            str(i),
            str(args.n),
            str(args.k),
            str(args.p_initial)
        ]
        
        # Define log files for stdout and stderr for each process
        stdout_log_path = os.path.join(logs_dir, f"process_{i}_stdout.log")
        stderr_log_path = os.path.join(logs_dir, f"process_{i}_stderr.log")

        try:
            with open(stdout_log_path, 'wb') as stdout_log, open(stderr_log_path, 'wb') as stderr_log:
                if sys.platform == "win32":
                    # CREATE_NEW_CONSOLE might interfere with stdout/stderr redirection if not handled carefully.
                    # For robust logging, it's often better to let the launcher manage output
                    # or ensure the child process correctly inherits/redirects handles.
                    # Simpler approach for now: don't use CREATE_NEW_CONSOLE if redirecting.
                    # If you still want separate windows AND logging, it's more complex.
                    process = subprocess.Popen(cmd, stdout=stdout_log, stderr=stderr_log)
                else:
                    process = subprocess.Popen(cmd, stdout=stdout_log, stderr=stderr_log)
            
            processes.append(process)
            print(f"Launched P{i} with PID {process.pid}. stdout: {stdout_log_path}, stderr: {stderr_log_path}")
        except Exception as e:
            print(f"!!!!!!!! FAILED TO LAUNCH P{i} !!!!!!!!!")
            print(f"Command: {' '.join(cmd)}")
            print(f"Error: {e}")
            continue # Continue launching other processes
        
        time.sleep(0.15) # Slightly increased delay

    print(f"\nAll {len(processes)} processes attempted to launch. They will run until termination condition is met.")
    print("Monitor their log files in the 'process_logs' directory.")
    print("To stop them prematurely, you would need to manually terminate each process (e.g., kill their PIDs).")

    # Optionally, wait for all processes to complete and print exit codes
    print("\nWaiting for processes to complete...")
    for i, p in enumerate(processes):
        try:
            p.wait() # Wait for the process to complete
            print(f"Process P{i} (PID {p.pid}) exited with code {p.returncode}")
        except Exception as e:
            print(f"Error waiting for P{i} (PID {p.pid}): {e}")
    print("All launched processes have terminated or an error occurred.")

if __name__ == "__main__":
    main()