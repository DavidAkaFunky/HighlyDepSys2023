import os
import json

# Compile classes
os.system("mvn clean install")

# Spawn blockchain nodes
with open("Service/src/main/resources/config.txt") as f:
    data = json.load(f)
    for key in data:
        os.system("cd Service && mvn exec:java -Dexec.args=\"" + key["id"] + "\" & echo $!")