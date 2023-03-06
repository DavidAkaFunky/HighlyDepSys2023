import os

# Compile classes
os.system("mvn clean install")

# Spawn blockchain nodes
with open("Service/src/main/resources/config.txt") as f:
    lines = f.read().splitlines()
    for line in lines:
        os.system("cd Service && mvn exec:java -Dexec.args=\"" + line + "\" & echo $!")