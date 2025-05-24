import socket
import random
import time
import struct
import argparse
import threading
import sys

# Constants
MULTICAST_GROUP = '224.1.1.1'
MULTICAST_PORT = 5007
TOKEN_BASE_PORT = 6000 # Base port for token passing, each process i listens on TOKEN_BASE_PORT + i

# Message types (as bytes for sending over network)
TOKEN_MSG = b'TOKEN'
FIREWORK_MSG = b'FIREWORK'
TERMINATE_MSG = b'TERMINATE'

class Process:
    def __init__(self, process_id, n_processes, k_rounds_no_firework, initial_p, my_ip, next_ip):
        self.process_id = process_id
        self.n_processes = n_processes
        self.k_rounds_no_firework = k_rounds_no_firework
        self.p = initial_p
        self.my_ip = my_ip
        self.next_ip = next_ip

        self.listen_port = TOKEN_BASE_PORT + self.process_id
        self.token_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.token_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            self.token_socket.bind((self.my_ip, self.listen_port))
            print(f"P{self.process_id}: Token socket bound to {self.my_ip}:{self.listen_port}")
        except socket.error as e:
            print(f"P{self.process_id}: FATAL ERROR binding token socket to {self.my_ip}:{self.listen_port} - {e}", file=sys.stderr)
            sys.exit(1)


        self.next_process_id = (self.process_id + 1) % self.n_processes
        # The port for the next process is determined by its ID, which is standard.
        self.next_process_port = TOKEN_BASE_PORT + self.next_process_id

        # Multicast Send Socket
        self.multicast_send_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        self.multicast_send_socket.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 2) # Adjust TTL if needed across routers
        # Optional: Specify sending interface for multicast if needed, e.g. for multi-homed hosts
        # try:
        #    self.multicast_send_socket.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_IF, socket.inet_aton(self.my_ip))
        # except socket.error as e:
        #    print(f"P{self.process_id}: Warning setting multicast send interface: {e}", file=sys.stderr)


        # Multicast Receive Socket
        self.multicast_recv_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        self.multicast_recv_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            # Bind to '' to receive multicast on any interface that supports it, or self.my_ip for a specific one
            self.multicast_recv_socket.bind(('', MULTICAST_PORT))
            print(f"P{self.process_id}: Multicast recv socket bound to any interface on port {MULTICAST_PORT}")
        except socket.error as e:
            print(f"P{self.process_id}: FATAL ERROR binding multicast recv socket to port {MULTICAST_PORT} - {e}", file=sys.stderr)
            sys.exit(1)
        
        # Join multicast group
        group_bin = socket.inet_aton(MULTICAST_GROUP)
        # For joining, INADDR_ANY typically means "any interface", or specify self.my_ip
        mreq = struct.pack("4sl", group_bin, socket.INADDR_ANY)
        # If issues with INADDR_ANY and specific interface binding for recv socket:
        # mreq = group_bin + socket.inet_aton(self.my_ip) 
        try:
            self.multicast_recv_socket.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)
        except socket.error as e:
            print(f"P{self.process_id}: FATAL ERROR joining multicast group {MULTICAST_GROUP} - {e}", file=sys.stderr)
            # This can happen if the IP used for binding (if specific) doesn't support multicast or other network issues.
            sys.exit(1)


        self.has_token = False
        self.terminate_flag = threading.Event()

        if self.process_id == 0:
            self.consecutive_rounds_without_firework_count = 0
            self.firework_seen_in_current_round_flag = False
            self.is_first_token_processing_for_p0 = True 
            self.total_rounds_completed = 0
            self.round_start_time = None
            self.round_times_ms = []

    def send_token(self):
        print(f"P{self.process_id}: Sending token to P{self.next_process_id} ({self.next_ip}:{self.next_process_port})")
        sys.stdout.flush()
        self.token_socket.sendto(TOKEN_MSG, (self.next_ip, self.next_process_port))
        self.has_token = False

    def launch_firework(self):
        print(f"P{self.process_id}: Launching FIREWORK! (p={self.p:.4f})")
        sys.stdout.flush()
        self.multicast_send_socket.sendto(FIREWORK_MSG, (MULTICAST_GROUP, MULTICAST_PORT))
        if self.process_id == 0:
            self.firework_seen_in_current_round_flag = True

    def listen_for_multicast(self):
        print(f"P{self.process_id}: Listening for multicast on {MULTICAST_GROUP}:{MULTICAST_PORT}")
        sys.stdout.flush()
        while not self.terminate_flag.is_set():
            try:
                self.multicast_recv_socket.settimeout(0.5)
                data, addr = self.multicast_recv_socket.recvfrom(1024)
                if not data:
                    if not self.terminate_flag.is_set():
                        print(f"P{self.process_id}: Multicast socket recv gave no data.", file=sys.stderr)
                        sys.stderr.flush()
                    break
                message = data.strip()
                if message == FIREWORK_MSG:
                    print(f"P{self.process_id}: Saw a firework from {addr[0]}:{addr[1]}.")
                    sys.stdout.flush()
                    if self.process_id == 0:
                        self.firework_seen_in_current_round_flag = True
                elif message == TERMINATE_MSG:
                    print(f"P{self.process_id}: Received TERMINATE from {addr[0]}. Exiting.")
                    sys.stdout.flush()
                    self.terminate_flag.set()
                    break
            except socket.timeout:
                continue
            except OSError as e:
                if self.terminate_flag.is_set(): break
                if e.errno == 9 or (hasattr(e, 'winerror') and e.winerror == 10004): # Bad file descriptor or WSAEINTR (interrupted by close)
                    print(f"P{self.process_id}: Multicast socket closed or interrupted.", file=sys.stderr)
                else:
                    print(f"P{self.process_id}: Multicast recv error: {e}", file=sys.stderr)
                sys.stderr.flush()
                break
        print(f"P{self.process_id}: Multicast listener stopped.")
        sys.stdout.flush()

    def run(self):
        multicast_thread = threading.Thread(target=self.listen_for_multicast, daemon=True)
        multicast_thread.start()

        if self.process_id == 0:
            self.has_token = True
            print(f"P0: Starting with the token.")
            sys.stdout.flush()
            if self.n_processes > 1:
                # Adjust initial wait based on network conditions, might need more for distributed
                initial_wait_time = self.n_processes * 0.75 # Increased for potential network/startup delays
                print(f"P0: Initial wait for {initial_wait_time:.2f}s for other(s) to start listening...")
                sys.stdout.flush()
                time.sleep(initial_wait_time)

        try:
            while not self.terminate_flag.is_set():
                if self.has_token:
                    if self.process_id == 0:
                        if self.is_first_token_processing_for_p0:
                            print(f"P0: Processing token for the first time (initial possession).")
                            self.is_first_token_processing_for_p0 = False
                        else: 
                            if self.round_start_time is not None:
                                round_duration_ms = (time.time() - self.round_start_time) * 1000
                                self.round_times_ms.append(round_duration_ms)
                            
                            self.total_rounds_completed += 1
                            print(f"P0: Token returned. Round {self.total_rounds_completed} completed. Firework seen in last round? {self.firework_seen_in_current_round_flag}")
                            if not self.firework_seen_in_current_round_flag:
                                self.consecutive_rounds_without_firework_count += 1
                                print(f"P0: Round ended. No firework. Consecutive no-firework rounds: {self.consecutive_rounds_without_firework_count}/{self.k_rounds_no_firework}")
                            else:
                                print(f"P0: Round ended. Firework occurred. Resetting no-firework round count.")
                                self.consecutive_rounds_without_firework_count = 0
                            

                            if self.consecutive_rounds_without_firework_count >= self.k_rounds_no_firework:
                                print(f"P0: Termination condition met. Sending TERMINATE.")
                                sys.stdout.flush()
                                self.multicast_send_socket.sendto(TERMINATE_MSG, (MULTICAST_GROUP, MULTICAST_PORT))
                                self.terminate_flag.set() 
                                time.sleep(0.5) # Slightly longer for TERMINATE to propagate over network
                                break 
                        self.firework_seen_in_current_round_flag = False 

                    print(f"P{self.process_id}: Processing token. Current p = {self.p:.4f}")
                    sys.stdout.flush()
                    
                    if random.random() < self.p:
                        self.launch_firework()
                    
                    self.p /= 2.0 
                    if self.p < 1e-9: self.p = 1e-9

                    if self.process_id == 0: 
                        self.round_start_time = time.time()

                    time.sleep(0.1 + random.uniform(0, 0.2)) 
                    self.send_token()
                else: 
                    try:
                        self.token_socket.settimeout(0.5) 
                        data, addr = self.token_socket.recvfrom(1024)
                        if not data: 
                            if not self.terminate_flag.is_set():
                                print(f"P{self.process_id}: Token socket recv gave no data, possibly closed. Breaking.", file=sys.stderr)
                                sys.stderr.flush()
                            break
                        message = data.strip()
                        if message == TOKEN_MSG:
                            self.has_token = True
                        else:
                            print(f"P{self.process_id}: Received unexpected unicast: {message.decode()} from {addr}", file=sys.stderr)
                            sys.stderr.flush()
                    except socket.timeout:
                        continue 
                    except OSError as e: 
                        if self.terminate_flag.is_set(): break
                        if hasattr(e, 'winerror') and e.winerror == 10054: # WSAECONNRESET
                            print(f"P{self.process_id}: Token recv warning (WinError 10054). Continuing.", file=sys.stderr)
                            sys.stderr.flush()
                            time.sleep(0.1) 
                            continue 
                        elif e.errno == 9 or (hasattr(e, 'winerror') and e.winerror == 10004): # Bad file descriptor or interrupted
                             print(f"P{self.process_id}: Token socket closed or interrupted. Breaking.", file=sys.stderr)
                             sys.stderr.flush()
                             break
                        else:
                            print(f"P{self.process_id}: Token recv error: {e}. Breaking.", file=sys.stderr)
                            sys.stderr.flush()
                            break
        finally:
            print(f"P{self.process_id}: Run loop finished. Cleaning up...")
            sys.stdout.flush()
            self.terminate_flag.set() # Ensure flag is set for multicast thread

            if self.process_id == 0 and self.total_rounds_completed > 0 : # Only print if at least one round completed
                min_rt_ms, avg_rt_ms, max_rt_ms = 0.0, 0.0, 0.0
                if self.round_times_ms:
                    min_rt_ms = min(self.round_times_ms)
                    avg_rt_ms = sum(self.round_times_ms) / len(self.round_times_ms)
                    max_rt_ms = max(self.round_times_ms)
                print(f"DATA_OUTPUT:n_processes={self.n_processes},total_rounds={self.total_rounds_completed},min_round_time_ms={min_rt_ms:.2f},avg_round_time_ms={avg_rt_ms:.2f},max_round_time_ms={max_rt_ms:.2f}")
                sys.stdout.flush()

            # Close sockets
            if self.token_socket.fileno() != -1: self.token_socket.close()
            if self.multicast_send_socket.fileno() != -1: self.multicast_send_socket.close()
            
            if self.multicast_recv_socket.fileno() != -1:
                try:
                    # Drop multicast membership
                    group_bin = socket.inet_aton(MULTICAST_GROUP)
                    mreq_drop = struct.pack("4sl", group_bin, socket.INADDR_ANY) # Or use specific interface if joined with one
                    # mreq_drop = group_bin + socket.inet_aton(self.my_ip) # If joined with specific IP
                    self.multicast_recv_socket.setsockopt(socket.IPPROTO_IP, socket.IP_DROP_MEMBERSHIP, mreq_drop)
                except OSError as e: 
                    if not (hasattr(e, 'winerror') and e.winerror == 10038): # WSAENOTSOCK - socket already closed
                         print(f"P{self.process_id}: Warning dropping multicast membership: {e}", file=sys.stderr)
                self.multicast_recv_socket.close()

            if multicast_thread.is_alive():
                 multicast_thread.join(timeout=2.0) # Increased timeout for thread join
            print(f"P{self.process_id} terminated.")
            sys.stdout.flush()

def main():
    print(f"task_2.py: Process script starting with args: {sys.argv}", file=sys.stderr)
    sys.stderr.flush()

    parser = argparse.ArgumentParser(description="Distributed Token Ring Process (Task 2)")
    parser.add_argument("id", type=int, help="Process ID (0 to n-1)")
    parser.add_argument("n", type=int, help="Total number of processes")
    parser.add_argument("k", type=int, help="Rounds without fireworks to terminate")
    parser.add_argument("p_initial", type=float, help="Initial probability p")
    parser.add_argument("my_ip", type=str, help="IP address of this machine for token listening")
    parser.add_argument("next_ip", type=str, help="IP address of the next process in the ring for sending tokens")
    args = parser.parse_args()

    # Basic validation for IP addresses (very simple)
    if not args.my_ip or not args.next_ip:
        print("Error: my_ip and next_ip must be provided.", file=sys.stderr)
        sys.exit(1)
    
    # It's good practice to ensure n is at least 2 for a distributed setup with next_ip
    if args.n < 2 and args.my_ip == args.next_ip:
        print("Warning: n < 2. For a single process, next_ip would typically be its own IP.", file=sys.stderr)
    elif args.n >= 2 and args.my_ip == args.next_ip and args.id == (args.id + 1) % args.n : # Only one process
         pass # This is fine for n=1 case, effectively.
    elif args.n >=2 and args.my_ip == args.next_ip:
        print("Error: For n >= 2, my_ip and next_ip should generally be different unless it's the last process pointing to the first on the same machine (not the distributed case here).", file=sys.stderr)
        # For a 2-node distributed setup, my_ip and next_ip will always be different.

    process_id_for_log = args.id # Assign early
    try:
        # Staggering might be less critical if starting manually, but keep for consistency
        time.sleep(args.id * 0.2) # Slightly increased stagger

        print(f"P{args.id}: Initializing with n={args.n}, k={args.k}, p_initial={args.p_initial}, my_ip={args.my_ip}, next_ip={args.next_ip}") 
        sys.stdout.flush()
        print(f"P{args.id}: Initializing (stderr)...", file=sys.stderr)
        sys.stderr.flush()

        process = Process(args.id, args.n, args.k, args.p_initial, args.my_ip, args.next_ip)
        
        print(f"P{args.id}: Initialization complete. Starting run loop.") 
        sys.stdout.flush()
        print(f"P{args.id}: Initialization complete (stderr). Starting run loop.", file=sys.stderr)
        sys.stderr.flush()
        
        process.run()
    except SystemExit: # Catch sys.exit calls from __init__ on socket errors
        print(f"P{process_id_for_log}: Exiting due to fatal error during initialization.", file=sys.stderr)
    except Exception as e:
        current_pid_for_log = args.id if 'args' in locals() and hasattr(args, 'id') else "UNKNOWN"
        error_message = f"!!!!!!!!!!!!!!!!!!!!!!!!! ERROR IN P{current_pid_for_log} !!!!!!!!!!!!!!!!!!!!!!!\n"
        error_message += f"P{current_pid_for_log} failed with exception: {type(e).__name__} - {e}\n"
        import traceback
        error_message += traceback.format_exc()
        error_message += "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n"
        print(error_message, file=sys.stderr) 
        sys.stderr.flush()
        
if __name__ == "__main__":
    main()