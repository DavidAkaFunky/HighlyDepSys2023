#!/usr/bin/env python

import os
import json
import sys
import signal


# Debug flag for client process
debug = True

# Terminal Emulator used to spawn the processes
terminal = "kitty"

# Blockchain node configuration file name
server_configs = [
    "regular_config.json",
    "drop_config.json",
    "fake_leader_config.json",
    "bad_broadcast_config.json",
    "bad_consensus_config.json",
    "dictator_leader_config.json",
    "silent_leader_config.json",
    "landlord_leader_config.json",
    "handsy_leader_config.json",
    "corrupt_leader_config.json",
    "fake_weak_config.json",
    "force_consensus_read_config.json",
]

client_configs = [
    "client_config.json",
    "greedy_client_config.json"
]

server_config = server_configs[11]
client_config = client_configs[0]
block_size = 3


def quit_handler(*args):
    os.system(f"pkill -i {terminal}")
    sys.exit()


# Compile classes
os.system("mvn clean install")

# Spawn blockchain nodes
with open(f"Service/src/main/resources/{server_config}") as f:
    data = json.load(f)
    processes = list()
    for key in data:
        pid = os.fork()
        if pid == 0:
            os.system(
                f"{terminal} sh -c \"cd Service; mvn exec:java -Dexec.args='{key['id']} {server_config} {block_size}' ; sleep 500\"")
            sys.exit()

# Spawn blockchain clients
with open(f"Client/src/main/resources/{client_config}") as f:
    data = json.load(f)
    processes = list()
    for key in data:
        pid = os.fork()
        if pid == 0:
            debug_str = "-debug" if debug else ""
            os.system(
                f"{terminal} sh -c \"cd Client; mvn exec:java -Dexec.args='{key['id']} {server_config} {client_config} {debug_str}' ; sleep 500\"")
            sys.exit()

signal.signal(signal.SIGINT, quit_handler)

while True:
    print("Type quit to quit")
    command = input(">> ")
    if command.strip() == "quit":
        quit_handler()
