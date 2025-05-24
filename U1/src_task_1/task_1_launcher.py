import subprocess
import time
import argparse
import sys
import os
import csv # For CSV output
import re  # For parsing DATA_OUTPUT
import shutil # For deleting directories

def parse_data_output(log_content):
    # Regex to find the DATA_OUTPUT line and capture its values
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
    parser = argparse.ArgumentParser(description="Launch multiple token ring processes and collect experiment data.")
    parser.add_argument("n_values", type=str, help="Comma-separated list of n (number of processes) values, e.g., '2,4,8'")
    parser.add_argument("k", type=int, help="Rounds without fireworks to terminate")
    parser.add_argument("p_initial", type=float, help="Initial probability p")
    parser.add_argument("repetitions", type=int, help="Number of repetitions for each n value")
    parser.add_argument("output_csv", type=str, help="Path to the output CSV file")
    args = parser.parse_args()

    try:
        n_values_list = [int(n_str.strip()) for n_str in args.n_values.split(',')]
    except ValueError:
        print("Error: n_values must be a comma-separated list of integers.", file=sys.stderr)
        return

    if any(n <= 0 for n in n_values_list):
        print("Error: All n values must be positive.", file=sys.stderr)
        return
    if args.k <= 0:
        print("Error: k (rounds for termination) must be positive.", file=sys.stderr)
        return
    if not (0.0 <= args.p_initial <= 1.0):
        print("Error: Initial probability p must be between 0.0 and 1.0.", file=sys.stderr)
        return
    if args.repetitions <= 0:
        print("Error: Number of repetitions must be positive.", file=sys.stderr)
        return

    python_executable = sys.executable
    launcher_dir = os.path.dirname(os.path.abspath(__file__))
    task_1_script_path = os.path.join(launcher_dir, "task_1.py")
    u1_dir = os.path.dirname(launcher_dir)
    base_experiment_logs_dir = os.path.join(u1_dir, "experiment_run_logs")
    
    os.makedirs(base_experiment_logs_dir, exist_ok=True)

    csv_header = [
        "n_processes", "repetition_id", "k_val", "p_initial_val",
        "total_rounds_completed", "total_fireworks_launched",
        "min_round_time_ms", "avg_round_time_ms", "max_round_time_ms",
        "run_successful"
    ]
    
    csv_file_exists = os.path.isfile(args.output_csv)
    
    try: # Main try block for the entire experiment execution
        with open(args.output_csv, 'a', newline='', encoding='utf-8') as csvfile:
            csv_writer = csv.writer(csvfile)
            if not csv_file_exists:
                csv_writer.writerow(csv_header)

            for n_proc_val in n_values_list:
                print(f"\nRunning experiments for n = {n_proc_val}")
                for rep_id in range(1, args.repetitions + 1):
                    print(f"  Repetition {rep_id}/{args.repetitions} for n = {n_proc_val}")
                    
                    current_run_log_dir = os.path.join(base_experiment_logs_dir, f"n{n_proc_val}_k{args.k}_p{args.p_initial}_rep{rep_id}")
                    os.makedirs(current_run_log_dir, exist_ok=True)
                    
                    process_handles = []
                    process_stdout_log_paths = [] 

                    run_launch_failed = False
                    try: 
                        for i in range(n_proc_val):
                            cmd = [
                                python_executable, task_1_script_path,
                                str(i), str(n_proc_val), str(args.k), str(args.p_initial)
                            ]
                            stdout_log_path = os.path.join(current_run_log_dir, f"process_{i}_stdout.log")
                            stderr_log_path = os.path.join(current_run_log_dir, f"process_{i}_stderr.log")
                            process_stdout_log_paths.append(stdout_log_path)

                            try:
                                with open(stdout_log_path, 'wb') as stdout_f, open(stderr_log_path, 'wb') as stderr_f:
                                    process = subprocess.Popen(cmd, stdout=stdout_f, stderr=stderr_f)
                                    process_handles.append(process)
                            except Exception as e:
                                print(f"    ERROR: Failed to launch P{i} for n={n_proc_val}, rep={rep_id}: {e}", file=sys.stderr)
                                run_launch_failed = True
                                break 
                        
                        if run_launch_failed:
                            csv_writer.writerow([n_proc_val, rep_id, args.k, args.p_initial, 0,0,0,0,0, False])
                            for p_h in process_handles: 
                                if p_h.poll() is None: p_h.terminate()
                            continue 

                        all_processes_exited_ok = True
                        wait_timeout = max(60, 30 + n_proc_val * 15) 
                        for i, p_h in enumerate(process_handles):
                            try:
                                p_h.wait(timeout=wait_timeout)
                                if p_h.returncode != 0:
                                    print(f"    WARNING: P{i} (n={n_proc_val}, rep={rep_id}) exited with code {p_h.returncode}.", file=sys.stderr)
                            except subprocess.TimeoutExpired:
                                print(f"    ERROR: P{i} (n={n_proc_val}, rep={rep_id}) timed out. Killing.", file=sys.stderr)
                                p_h.kill()
                                all_processes_exited_ok = False 
                            except Exception as e:
                                print(f"    ERROR waiting for P{i} (n={n_proc_val}, rep={rep_id}): {e}", file=sys.stderr)
                                all_processes_exited_ok = False

                        if not all_processes_exited_ok:
                            csv_writer.writerow([n_proc_val, rep_id, args.k, args.p_initial, 0,0,0,0,0, False])
                            continue 

                        p0_log_content = ""
                        try:
                            with open(process_stdout_log_paths[0], 'r', encoding='utf-8') as f:
                                p0_log_content = f.read()
                        except Exception as e:
                            print(f"    ERROR reading P0 log for n={n_proc_val}, rep={rep_id}: {e}", file=sys.stderr)
                            csv_writer.writerow([n_proc_val, rep_id, args.k, args.p_initial, 0,0,0,0,0, False])
                            continue

                        parsed_stats = parse_data_output(p0_log_content)
                        if parsed_stats:
                            total_fireworks_this_run = 0
                            for log_p in process_stdout_log_paths:
                                total_fireworks_this_run += count_fireworks_in_log(log_p)
                            
                            csv_writer.writerow([
                                parsed_stats["n_processes"], rep_id, args.k, args.p_initial,
                                parsed_stats["total_rounds"], total_fireworks_this_run,
                                parsed_stats["min_round_time_ms"], parsed_stats["avg_round_time_ms"], parsed_stats["max_round_time_ms"],
                                True
                            ])
                            print(f"    Data logged: n={n_proc_val}, rep={rep_id}, Rounds={parsed_stats['total_rounds']}, Fireworks={total_fireworks_this_run}")
                        else:
                            print(f"    ERROR: Could not parse DATA_OUTPUT from P0 log for n={n_proc_val}, rep={rep_id}.", file=sys.stderr)
                            csv_writer.writerow([n_proc_val, rep_id, args.k, args.p_initial, 0,0,0,0,0, False])
                    except Exception as e_rep: # Catch any unexpected error during a repetition
                        print(f"    UNEXPECTED ERROR during repetition n={n_proc_val}, rep={rep_id}: {e_rep}", file=sys.stderr)
                        csv_writer.writerow([n_proc_val, rep_id, args.k, args.p_initial, 0,0,0,0,0, False])
                        # Ensure any running processes for this failed rep are terminated
                        for p_h in process_handles:
                            if p_h.poll() is None:
                                try:
                                    p_h.terminate()
                                    p_h.wait(timeout=5) # Give a moment to terminate
                                except:
                                    p_h.kill() # Force kill if terminate fails
                        continue # Move to the next repetition

        print(f"\nExperiments finished. Data saved to {args.output_csv}")

    finally: # This finally block is for the main try block covering all experiments
        if os.path.isdir(base_experiment_logs_dir):
            print(f"\nAttempting to clean up base log directory: {base_experiment_logs_dir}")
            time.sleep(10) # Add a 10-second delay for file handles to be released
            try:
                shutil.rmtree(base_experiment_logs_dir)
                print(f"Successfully cleaned up base log directory: {base_experiment_logs_dir}")
            except Exception as e:
                print(f"WARNING: Failed to delete base log directory {base_experiment_logs_dir} after delay: {e}", file=sys.stderr)

if __name__ == "__main__":
    main()