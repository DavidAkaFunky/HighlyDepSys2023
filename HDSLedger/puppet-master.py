import os
import json
import sys

# Debug flag for client process
debug = True

# Blockchain node configuration file name
server_configs = [
    "regular_config.json",
    "drop_config.json",
    "fake_leader_config.json",
    "fake_value_config.json",
    "bad_broadcast_config.json",
    "bad_consensus_config.json",
]
server_config = server_configs[5]

# Compile classes
os.system("mvn clean install")

# Spawn blockchain nodes
with open("Service/src/main/resources/" + server_config) as f:
    data = json.load(f)
    processes = list()
    for key in data:
        pid = os.fork()
        if pid == 0:
            os.system(
                f"/usr/bin/kitty sh -c \"cd Service; mvn exec:java -Dexec.args='{key['id']} {server_config}' ; sleep 1000\"")
            sys.exit()

# Spawn blockchain clients
with open("Client/src/main/resources/client_config.json") as f:
    data = json.load(f)
    processes = list()
    for key in data:
        pid = os.fork()
        if pid == 0:
            debug_str = "-debug" if debug else ""
            os.system(
                f"/usr/bin/kitty sh -c \"cd Client; mvn exec:java -Dexec.args='{key['id']} {server_config} {debug_str}' ; sleep 1000\"")
            sys.exit()
