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
    def __init__(self, process_id, n_processes, k_rounds_no_firework, initial_p):
        self.process_id = process_id
        self.n_processes = n_processes
        self.k_rounds_no_firework = k_rounds_no_firework
        self.p = initial_p

        self.host = '127.0.0.1'

        self.listen_port = TOKEN_BASE_PORT + self.process_id
        self.token_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.token_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.token_socket.bind((self.host, self.listen_port))

        self.next_process_id = (self.process_id + 1) % self.n_processes
        self.next_process_port = TOKEN_BASE_PORT + self.next_process_id

        self.multicast_send_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        self.multicast_send_socket.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 2)

        self.multicast_recv_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        self.multicast_recv_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.multicast_recv_socket.bind(('', MULTICAST_PORT))
        mreq = struct.pack("4sl", socket.inet_aton(MULTICAST_GROUP), socket.INADDR_ANY)
        self.multicast_recv_socket.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)

        self.has_token = False # Initialize to False for all
        self.terminate_flag = threading.Event()

        if self.process_id == 0:
            self.consecutive_rounds_without_firework_count = 0
            self.firework_seen_in_current_round_flag = False
            self.is_first_token_processing_for_p0 = True # New flag for P0

    def send_token(self):
        print(f"P{self.process_id}: Sending token to P{self.next_process_id} (port {self.next_process_port})")
        sys.stdout.flush() # Ensure print is visible
        self.token_socket.sendto(TOKEN_MSG, (self.host, self.next_process_port))
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
                    print(f"P{self.process_id}: Received TERMINATE. Exiting.")
                    sys.stdout.flush()
                    self.terminate_flag.set()
                    break
            except socket.timeout:
                continue
            except OSError as e:
                if self.terminate_flag.is_set(): break
                if e.errno == 9:
                    print(f"P{self.process_id}: Multicast socket closed.", file=sys.stderr)
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
            # Add a delay here specifically for P0's first round
            # to give other processes a chance to set up their listeners.
            if self.n_processes > 1:
                initial_wait_time = self.n_processes * 0.35 # Adjusted delay factor
                print(f"P0: Initial wait for {initial_wait_time:.2f}s for others to start listening...")
                sys.stdout.flush()
                time.sleep(initial_wait_time)

        try:
            while not self.terminate_flag.is_set():
                if self.has_token:
                    if self.process_id == 0:
                        if self.is_first_token_processing_for_p0:
                            print(f"P0: Processing token for the first time (initial possession).")
                            # Don't update consecutive_rounds_without_firework_count yet.
                            self.is_first_token_processing_for_p0 = False
                        else: # Token has completed a round and returned to P0
                            print(f"P0: Token returned. Firework seen in last round? {self.firework_seen_in_current_round_flag}")
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
                                time.sleep(0.2) # Give time for TERMINATE msg to propagate
                                break 
                        
                        self.firework_seen_in_current_round_flag = False # Reset for the new round (or after first processing)

                    # Common token handling for all processes
                    print(f"P{self.process_id}: Processing token. Current p = {self.p:.4f}")
                    sys.stdout.flush()
                    
                    if random.random() < self.p:
                        self.launch_firework()
                    
                    self.p /= 2.0 
                    if self.p < 1e-9: 
                        self.p = 1e-9

                    time.sleep(0.1 + random.uniform(0, 0.2)) 
                    self.send_token()

                else: # Wait for token
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
                            # print(f"P{self.process_id}: Received token from P{(self.process_id - 1 + self.n_processes) % self.n_processes}")
                            self.has_token = True
                        else:
                            print(f"P{self.process_id}: Received unexpected unicast: {message.decode()} from {addr}", file=sys.stderr)
                            sys.stderr.flush()
                    except socket.timeout:
                        continue 
                    except OSError as e: 
                        if self.terminate_flag.is_set(): break
                        if hasattr(e, 'winerror') and e.winerror == 10054: # WSAECONNRESET
                            print(f"P{self.process_id}: Token recv warning (WinError 10054), likely ICMP Port Unreachable from previous send. Continuing to listen.", file=sys.stderr)
                            sys.stderr.flush()
                            time.sleep(0.1) 
                            continue 
                        elif e.errno == 9: 
                             print(f"P{self.process_id}: Token socket closed. Breaking.", file=sys.stderr)
                             sys.stderr.flush()
                             break
                        else:
                            print(f"P{self.process_id}: Token recv error: {e}. Breaking.", file=sys.stderr)
                            sys.stderr.flush()
                            break
        finally:
            print(f"P{self.process_id}: Run loop finished. Cleaning up...")
            sys.stdout.flush()
            self.terminate_flag.set()

            if self.token_socket.fileno() != -1: self.token_socket.close()
            if self.multicast_send_socket.fileno() != -1: self.multicast_send_socket.close()
            
            if self.multicast_recv_socket.fileno() != -1:
                try:
                    mreq_drop = struct.pack("4sl", socket.inet_aton(MULTICAST_GROUP), socket.INADDR_ANY)
                    self.multicast_recv_socket.setsockopt(socket.IPPROTO_IP, socket.IP_DROP_MEMBERSHIP, mreq_drop)
                except OSError: pass 
                self.multicast_recv_socket.close()

            if multicast_thread.is_alive():
                 multicast_thread.join(timeout=1.0)
            print(f"P{self.process_id} terminated.")
            sys.stdout.flush()


def main():
    # Very early diagnostic print to stderr
    print(f"task_1.py: Process script starting with args: {sys.argv}", file=sys.stderr)
    sys.stderr.flush() # Ensure it's written immediately

    parser = argparse.ArgumentParser(description="Token Ring Process")
    parser.add_argument("id", type=int, help="Process ID (0 to n-1)")
    parser.add_argument("n", type=int, help="Total number of processes")
    parser.add_argument("k", type=int, help="Rounds without fireworks to terminate")
    parser.add_argument("p_initial", type=float, help="Initial probability p")
    args = parser.parse_args()

    process_id_for_log = -1 
    try:
        process_id_for_log = args.id 
        # Stagger process startup slightly more within the process itself
        time.sleep(args.id * 0.1) # This staggering is still useful

        print(f"P{args.id}: Initializing with n={args.n}, k={args.k}, p_initial={args.p_initial}") 
        sys.stdout.flush()
        print(f"P{args.id}: Initializing (stderr)...", file=sys.stderr)
        sys.stderr.flush()

        process = Process(args.id, args.n, args.k, args.p_initial)
        
        print(f"P{args.id}: Initialization complete. Starting run loop.") 
        sys.stdout.flush()
        print(f"P{args.id}: Initialization complete (stderr). Starting run loop.", file=sys.stderr)
        sys.stderr.flush()
        
        process.run()
    except Exception as e:
        current_pid_for_log = "UNKNOWN (pre-argparse)"
        if 'args' in locals() and hasattr(args, 'id'):
            current_pid_for_log = args.id
        elif process_id_for_log != -1: 
             current_pid_for_log = process_id_for_log

        error_message = f"!!!!!!!!!!!!!!!!!!!!!!!!! ERROR IN P{current_pid_for_log} !!!!!!!!!!!!!!!!!!!!!!!\n"
        error_message += f"P{current_pid_for_log} failed with exception: {type(e).__name__} - {e}\n"
        import traceback
        error_message += traceback.format_exc()
        error_message += "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n"
        
        print(error_message, file=sys.stderr) 
        sys.stderr.flush()
        
if __name__ == "__main__":
    main()