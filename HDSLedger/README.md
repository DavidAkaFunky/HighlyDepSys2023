# HDSLedger

## Introduction

HDSLedger is a simplified permissioned (closed membership) blockchain system with high dependability
guarantees. It uses the Istanbul BFT consensus algorithm to ensure that all nodes run commands
in the same order, achieving State Machine Replication (SMR) and guarantees that all nodes
have the same state.

## Public Key Infrastructure

Both the nodes and the clients of the blockchain should use self-generated public/private keys which are
pre-distributed before the start of the system.

The steps to generate the keys are the following:

### Compile generator

```
cd PKI/
javac *.java
```

### Generating keys

```
java RSAKeyGenerator w ./<IDENTIFIER>.priv ./<IDENTIFIER>.pub
```

## Configuration Files

## Puppet Master

The puppet master is a python script `puppet-master.py` which is responsible for starting the nodes
and clients of the blockchain.
The script assume that `kitty` terminal emulator is installed and configuration files are correct.
To run the script you need to have python3 installed.

```
python3 puppet-master.py
```

## Maven

It's also possible to run the project manually by using Maven.

### Instalation

Compile and install all modules using:

```
mvn clean install
```

### Execution

Run without arguments

```
cd <module>/
mvn compile exec:java
```

or with arguments

```
cd <module>/
mvn compile exec:java -Dexec.args="..."
```

## Built With

- [Maven](https://maven.apache.org/) - Build and dependency management tool;
