import socket
import random
import time
import struct
import argparse
import threading

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
        self.p = initial_p  # Initial probability of launching a firework

        self.host = '127.0.0.1'  # All processes run on localhost

        # UDP socket for receiving the token
        self.listen_port = TOKEN_BASE_PORT + self.process_id
        self.token_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.token_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.token_socket.bind((self.host, self.listen_port))

        # Determine next process in the ring for token passing
        self.next_process_id = (self.process_id + 1) % self.n_processes
        self.next_process_port = TOKEN_BASE_PORT + self.next_process_id

        # UDP socket for sending multicast messages (fireworks, termination)
        self.multicast_send_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        self.multicast_send_socket.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 2) # TTL for multicast

        # UDP socket for receiving multicast messages
        self.multicast_recv_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        self.multicast_recv_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.multicast_recv_socket.bind(('', MULTICAST_PORT)) # Listen on all interfaces for multicast
        # Join multicast group
        mreq = struct.pack("4sl", socket.inet_aton(MULTICAST_GROUP), socket.INADDR_ANY)
        self.multicast_recv_socket.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)

        self.has_token = False
        self.terminate_flag = threading.Event() # Event to signal termination

        # Process 0 (P0) specific state for termination logic
        if self.process_id == 0:
            self.consecutive_rounds_without_firework_count = 0
            self.firework_seen_in_current_round_flag = False

    def send_token(self):
        print(f"P{self.process_id}: Sending token to P{self.next_process_id} (port {self.next_process_port})")
        self.token_socket.sendto(TOKEN_MSG, (self.host, self.next_process_port))
        self.has_token = False

    def launch_firework(self):
        print(f"P{self.process_id}: Launching FIREWORK! (p={self.p:.4f})")
        self.multicast_send_socket.sendto(FIREWORK_MSG, (MULTICAST_GROUP, MULTICAST_PORT))
        # If P0 itself launches a firework, it notes it for termination logic
        if self.process_id == 0:
            self.firework_seen_in_current_round_flag = True

    def listen_for_multicast(self):
        print(f"P{self.process_id}: Listening for multicast on {MULTICAST_GROUP}:{MULTICAST_PORT}")
        while not self.terminate_flag.is_set():
            try:
                self.multicast_recv_socket.settimeout(0.5) # Timeout to check terminate_flag
                data, addr = self.multicast_recv_socket.recvfrom(1024)
                if not data: # Socket might be closed
                    break
                message = data.strip()
                # print(f"P{self.process_id}: Received multicast '{message.decode()}' from {addr}")
                if message == FIREWORK_MSG:
                    print(f"P{self.process_id}: Saw a firework from {addr[0]}:{addr[1]}.")
                    if self.process_id == 0: # Only P0 tracks this for termination
                        self.firework_seen_in_current_round_flag = True
                elif message == TERMINATE_MSG:
                    print(f"P{self.process_id}: Received TERMINATE. Exiting.")
                    self.terminate_flag.set()
                    break
            except socket.timeout:
                continue
            except OSError as e: # Handle socket closed error
                if e.errno == 9: # Bad file descriptor (socket closed)
                    print(f"P{self.process_id}: Multicast socket closed.")
                elif not self.terminate_flag.is_set():
                     print(f"P{self.process_id}: Multicast recv error: {e}")
                break
        print(f"P{self.process_id}: Multicast listener stopped.")

    def run(self):
        multicast_thread = threading.Thread(target=self.listen_for_multicast, daemon=True)
        multicast_thread.start()

        if self.process_id == 0: # P0 starts with the token
            self.has_token = True
            print(f"P0: Starting with the token.")

        try:
            while not self.terminate_flag.is_set():
                if self.has_token:
                    # P0's round tracking and termination logic
                    if self.process_id == 0:
                        # This block executes when P0 receives the token, signifying end of a round
                        # (except for the very first time P0 has the token)
                        # print(f"P0: Token returned. Firework seen in last round? {self.firework_seen_in_current_round_flag}")
                        if not self.firework_seen_in_current_round_flag:
                            self.consecutive_rounds_without_firework_count += 1
                            print(f"P0: Round ended. No firework. Consecutive no-firework rounds: {self.consecutive_rounds_without_firework_count}/{self.k_rounds_no_firework}")
                        else:
                            print(f"P0: Round ended. Firework occurred. Resetting no-firework round count.")
                            self.consecutive_rounds_without_firework_count = 0
                        
                        self.firework_seen_in_current_round_flag = False # Reset for the new round

                        if self.consecutive_rounds_without_firework_count >= self.k_rounds_no_firework:
                            print(f"P0: Termination condition met. Sending TERMINATE.")
                            self.multicast_send_socket.sendto(TERMINATE_MSG, (MULTICAST_GROUP, MULTICAST_PORT))
                            self.terminate_flag.set() # Signal self and multicast listener to terminate
                            time.sleep(0.2) # Give time for TERMINATE msg to propagate
                            break 

                    # Common token handling for all processes
                    print(f"P{self.process_id}: Processing token. Current p = {self.p:.4f}")
                    
                    if random.random() < self.p:
                        self.launch_firework()
                    
                    self.p /= 2.0 # Reduce probability for next time
                    if self.p < 1e-9: # Prevent p from becoming excessively small or zero
                        self.p = 1e-9

                    time.sleep(0.1 + random.uniform(0, 0.2)) # Simulate work & add slight delay
                    self.send_token()

                else: # Wait for token
                    try:
                        self.token_socket.settimeout(0.5) # Timeout to check terminate_flag
                        data, addr = self.token_socket.recvfrom(1024)
                        if not data: # Socket might be closed
                            break
                        message = data.strip()
                        if message == TOKEN_MSG:
                            # print(f"P{self.process_id}: Received token from P{(self.process_id - 1 + self.n_processes) % self.n_processes}")
                            self.has_token = True
                        else:
                            print(f"P{self.process_id}: Received unexpected unicast: {message.decode()} from {addr}")
                    except socket.timeout:
                        continue 
                    except OSError as e: # Handle socket closed error
                        if e.errno == 9: # Bad file descriptor (socket closed)
                             print(f"P{self.process_id}: Token socket closed.")
                        elif not self.terminate_flag.is_set():
                            print(f"P{self.process_id}: Token recv error: {e}")
                        break
        finally:
            print(f"P{self.process_id}: Run loop finished. Cleaning up...")
            self.terminate_flag.set() # Ensure flag is set for multicast thread if loop exited otherwise

            # Close sockets
            self.token_socket.close()
            self.multicast_send_socket.close()
            # Request multicast socket to unregister and close
            if self.multicast_recv_socket.fileno() != -1:
                try:
                    mreq = struct.pack("4sl", socket.inet_aton(MULTICAST_GROUP), socket.INADDR_ANY)
                    self.multicast_recv_socket.setsockopt(socket.IPPROTO_IP, socket.IP_DROP_MEMBERSHIP, mreq)
                except OSError as e:
                    # print(f"P{self.process_id}: Error dropping multicast membership: {e}")
                    pass # May fail if socket already effectively closed
                self.multicast_recv_socket.close()

            if multicast_thread.is_alive():
                 multicast_thread.join(timeout=1.0)
            print(f"P{self.process_id} terminated.")


def main():
    parser = argparse.ArgumentParser(description="Token Ring Process")
    parser.add_argument("id", type=int, help="Process ID (0 to n-1)")
    parser.add_argument("n", type=int, help="Total number of processes")
    parser.add_argument("k", type=int, help="Rounds without fireworks to terminate")
    parser.add_argument("p_initial", type=float, help="Initial probability p")
    args = parser.parse_args()

    if not (0 <= args.id < args.n):
        print("Error: Process ID must be between 0 and n-1.")
        return
    if args.n <= 0:
        print("Error: Number of processes (n) must be positive.")
        return
    if args.k <= 0:
        print("Error: k (rounds for termination) must be positive.")
        return
    if not (0.0 <= args.p_initial <= 1.0):
        print("Error: Initial probability p must be between 0.0 and 1.0.")
        return

    # Stagger process startup slightly to help ensure sockets are bound in order,
    # especially for P0 which starts with the token.
    time.sleep(args.id * 0.05)

    process = Process(args.id, args.n, args.k, args.p_initial)
    process.run()

if __name__ == "__main__":
    main()