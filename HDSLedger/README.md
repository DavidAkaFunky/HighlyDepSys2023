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
The script assumes that `kitty` terminal emulator is installed and configuration files are correct.
To run the script you need to have python3 installed.
```
python3 puppet-master.py
```
The script has two arguments which can be modified:
- `terminal` - the terminal emulator used by the script
- `debug` - if set to "True" the client process will print logs about the
perfect link and library operations
- `server_config` - a string from the array `server_configs` which contains
the possible configurations for the blockchain nodes

## Maven

It's also possible to run the project manually by using Maven.

### Instalation

Install Google's Gson library manually by running:
```
./install_deps.sh
```

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
