import os

# Compile classes
os.system("mvn compile")

# Spawn blockchain nodes
with open("config.txt") as f:
    lines = f.read().splitlines()
    for line in lines:
        os.system("java -classpath target/classes blockchain.Node " + line + " & echo $!")