import os
import json

# Compile classes
os.system("mvn clean install")

# Spawn blockchain nodes
with open("Service/src/main/resources/config.txt") as f:
    data = json.load(f)
    processes = list()
    for key in data:
        pid = os.fork()
        if pid == 0:
            os.system(f"/usr/bin/kitty sh -c \"cd Service; mvn exec:java -Dexec.args='{key['id']}'\" && sleep 1000")