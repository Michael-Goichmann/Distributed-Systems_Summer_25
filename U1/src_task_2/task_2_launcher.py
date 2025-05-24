import subprocess
import time
import argparse
import sys
import os
import csv
import re
import shutil

# Helper functions (can be shared or copied from task_1_launcher.py)
def parse_data_output(log_content):
    match = re.search(
        r"DATA_OUTPUT:n_processes=(\d+),total_rounds=(\d+),"
        r"min_round_time_ms=([\d\.]+),avg_round_time_ms=([\d\.]+),max_round_time_ms=([\d\.]+)",
        log_content
    )
    if match:
        return {
            "n_processes": int(match.group(1)),
            "total_rounds": int(match.group(2)),
            "min_round_time_ms": float(match.group(3)),
            "avg_round_time_ms": float(match.group(4)),
            "max_round_time_ms": float(match.group(5)),
        }
    return None

def count_fireworks_in_log(log_path):
    count = 0
    try:
        with open(log_path, 'r', encoding='utf-8') as f:
            for line in f:
                if "Launching FIREWORK!" in line:
                    count += 1
    except FileNotFoundError:
        print(f"Warning: Log file not found for counting fireworks: {log_path}", file=sys.stderr)
    except Exception as e:
        print(f"Warning: Error reading log file {log_path} for fireworks: {e}", file=sys.stderr)
    return count

def main():
    parser = argparse.ArgumentParser(description="Launch distributed token ring (n=2) and collect experiment data.")
    parser.add_argument("k", type=int, help="Rounds without fireworks to terminate")
    parser.add_argument("p_initial", type=float, help="Initial probability p")
    parser.add_argument("repetitions", type=int, help="Number of repetitions for each n value")
    parser.add_argument("ip_machine_a", type=str, help="IP address of Machine A (where P0 and this launcher run)")
    parser.add_argument("ip_machine_b", type=str, help="IP address of Machine B (where P1 runs)")
    parser.add_argument("output_csv", type=str, help="Path to the output CSV file")
    args = parser.parse_args()

    if args.k <= 0:
        print("Error: k (rounds for termination) must be positive.", file=sys.stderr)
        return
    if not (0.0 <= args.p_initial <= 1.0):
        print("Error: Initial probability p must be between 0.0 and 1.0.", file=sys.stderr)
        return
    if args.repetitions <= 0:
        print("Error: Number of repetitions must be positive.", file=sys.stderr)
        return
    if not args.ip_machine_a or not args.ip_machine_b:
        print("Error: IP addresses for both machines must be provided.", file=sys.stderr)
        return
    if args.ip_machine_a == args.ip_machine_b:
        print("Error: IP addresses for Machine A and Machine B must be different for a distributed setup.", file=sys.stderr)
        return

    n_processes = 2 # Fixed for 2-machine setup

    python_executable = sys.executable
    launcher_dir = os.path.dirname(os.path.abspath(__file__)) # U1/src_task_2
    task_2_script_path = os.path.join(launcher_dir, "task_2.py")
    
    u1_dir = os.path.dirname(launcher_dir) # U1
    base_experiment_logs_dir = os.path.join(u1_dir, "experiment_run_logs_task2") # Specific for task 2
    
    os.makedirs(base_experiment_logs_dir, exist_ok=True)

    csv_header = [
        "n_processes", "repetition_id", "k_val", "p_initial_val",
        "total_rounds_completed", "total_fireworks_launched",
        "min_round_time_ms", "avg_round_time_ms", "max_round_time_ms",
        "run_successful"
    ]
    
    csv_file_exists = os.path.isfile(args.output_csv)
    
    try:
        with open(args.output_csv, 'a', newline='', encoding='utf-8') as csvfile:
            csv_writer = csv.writer(csvfile)
            if not csv_file_exists:
                csv_writer.writerow(csv_header)

            print(f"Running experiments for n = {n_processes} (Machine A: {args.ip_machine_a}, Machine B: {args.ip_machine_b})")
            for rep_id in range(1, args.repetitions + 1):
                print(f"\n  Repetition {rep_id}/{args.repetitions}")
                
                current_run_log_dir = os.path.join(base_experiment_logs_dir, f"n{n_processes}_k{args.k}_p{args.p_initial}_rep{rep_id}")
                os.makedirs(current_run_log_dir, exist_ok=True)
                
                p0_stdout_log_path = os.path.join(current_run_log_dir, "process_0_stdout.log")
                p0_stderr_log_path = os.path.join(current_run_log_dir, "process_0_stderr.log")

                # --- P0 (Machine A - Local) ---
                cmd_p0 = [
                    python_executable, task_2_script_path,
                    "0", str(n_processes), str(args.k), str(args.p_initial),
                    args.ip_machine_a, args.ip_machine_b # P0's IP, P1's IP
                ]
                
                p0_handle = None
                p0_launched_ok = False
                try:
                    print(f"    Launching P0 on this machine (Machine A: {args.ip_machine_a})...")
                    print(f"    P0 Command: {' '.join(cmd_p0)}")
                    with open(p0_stdout_log_path, 'wb') as stdout_f, open(p0_stderr_log_path, 'wb') as stderr_f:
                        p0_handle = subprocess.Popen(cmd_p0, stdout=stdout_f, stderr=stderr_f)
                    p0_launched_ok = True
                    print(f"    P0 launched. Log: {p0_stdout_log_path}")
                except Exception as e:
                    print(f"    ERROR: Failed to launch P0: {e}", file=sys.stderr)
                    csv_writer.writerow([n_processes, rep_id, args.k, args.p_initial, 0,0,0,0,0, False])
                    continue # Next repetition

                # --- P1 (Machine B - Remote/Manual) ---
                cmd_p1_display = [
                    "python", os.path.basename(task_2_script_path), # Use relative name for display
                    "1", str(n_processes), str(args.k), str(args.p_initial),
                    args.ip_machine_b, args.ip_machine_a # P1's IP, P0's IP
                ]
                print("\n    -------------------------------------------------------------------------")
                print(f"    ACTION REQUIRED ON MACHINE B ({args.ip_machine_b}):")
                print(f"    1. Ensure '{os.path.basename(task_2_script_path)}' is on Machine B.")
                print(f"    2. Open a terminal on Machine B and run the following command:")
                print(f"       {' '.join(cmd_p1_display)}")
                print(f"    3. It's recommended to save P1's output to a log file, e.g.:")
                print(f"       {' '.join(cmd_p1_display)} > p1_stdout_rep{rep_id}.log 2>&1")
                print(f"    Start P1 now. This script will wait for P0 to complete.")
                print("    -------------------------------------------------------------------------")

                # Wait for P0 to complete
                p0_completed_ok = False
                # Timeout: 90s base + 30s per process (n=2 here), min 120s. Adjust as needed for network.
                wait_timeout = max(120, 90 + n_processes * 30) 
                if p0_handle:
                    try:
                        p0_handle.wait(timeout=wait_timeout)
                        if p0_handle.returncode == 0:
                            p0_completed_ok = True
                            print(f"    P0 completed with exit code 0.")
                        else:
                            print(f"    WARNING: P0 exited with code {p0_handle.returncode}.", file=sys.stderr)
                    except subprocess.TimeoutExpired:
                        print(f"    ERROR: P0 timed out. Killing.", file=sys.stderr)
                        p0_handle.kill()
                    except Exception as e:
                        print(f"    ERROR waiting for P0: {e}", file=sys.stderr)
                
                if not p0_completed_ok:
                    csv_writer.writerow([n_processes, rep_id, args.k, args.p_initial, 0,0,0,0,0, False])
                    continue # Next repetition

                # P0 completed, now get P1's log
                p1_log_path_input = input(f"    P0 has finished. Has P1 on Machine B also finished? \n    If yes, please provide the FULL PATH to P1's stdout log file (or drag & drop here),\n    or press Enter to skip P1 log processing for this run: ").strip()

                p0_log_content = ""
                try:
                    with open(p0_stdout_log_path, 'r', encoding='utf-8') as f:
                        p0_log_content = f.read()
                except Exception as e:
                    print(f"    ERROR reading P0 log: {e}", file=sys.stderr)
                    csv_writer.writerow([n_processes, rep_id, args.k, args.p_initial, 0,0,0,0,0, False])
                    continue

                parsed_stats = parse_data_output(p0_log_content)
                total_fireworks_this_run = count_fireworks_in_log(p0_stdout_log_path)

                if p1_log_path_input and os.path.isfile(p1_log_path_input):
                    print(f"    Processing P1 log: {p1_log_path_input}")
                    total_fireworks_this_run += count_fireworks_in_log(p1_log_path_input)
                elif p1_log_path_input:
                    print(f"    WARNING: P1 log path provided but not found: {p1_log_path_input}. Fireworks from P1 not counted.", file=sys.stderr)
                else:
                    print(f"    No P1 log path provided. Fireworks from P1 not counted.")


                if parsed_stats:
                    csv_writer.writerow([
                        parsed_stats["n_processes"], rep_id, args.k, args.p_initial,
                        parsed_stats["total_rounds"], total_fireworks_this_run,
                        parsed_stats["min_round_time_ms"], parsed_stats["avg_round_time_ms"], parsed_stats["max_round_time_ms"],
                        True # Mark as successful if P0 data parsed, even if P1 log missing
                    ])
                    print(f"    Data logged: n={n_processes}, rep={rep_id}, Rounds={parsed_stats['total_rounds']}, Total Fireworks (from available logs)={total_fireworks_this_run}")
                else:
                    print(f"    ERROR: Could not parse DATA_OUTPUT from P0 log.", file=sys.stderr)
                    csv_writer.writerow([n_processes, rep_id, args.k, args.p_initial, 0, total_fireworks_this_run ,0,0,0, False])
                
                # Individual run log cleanup (optional, can be done at the very end too)
                # For now, let's keep it to simplify, and delete base_experiment_logs_dir at the end.

            print(f"\nAll repetitions for n={n_processes} finished.")
        print(f"\nExperiments finished. Data saved to {args.output_csv}")

    finally:
        if os.path.isdir(base_experiment_logs_dir):
            print(f"\nAttempting to clean up base log directory: {base_experiment_logs_dir}")
            time.sleep(2) 
            try:
                shutil.rmtree(base_experiment_logs_dir)
                print(f"Successfully cleaned up base log directory: {base_experiment_logs_dir}")
            except Exception as e:
                print(f"WARNING: Failed to delete base log directory {base_experiment_logs_dir} after delay: {e}", file=sys.stderr)

if __name__ == "__main__":
    main()