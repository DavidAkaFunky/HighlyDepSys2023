# HDSLedger

## Introduction

HDSLedger is a simplified permissioned (closed membership) blockchain system with high dependability
guarantees. It uses the Istanbul BFT consensus algorithm to ensure that all nodes run commands
in the same order, achieving State Machine Replication (SMR) and guarantees that all nodes
have the same state.

## Requirements

- [Java 17](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html) - Programming language;

- [Maven 3.8](https://maven.apache.org/) - Build and dependency management tool;

- [Python 3](https://www.python.org/downloads/) - Programming language;

## Public Key Infrastructure

Both the nodes and the clients of the blockchain should use self-generated public/private keys which are
pre-distributed before the start of the system.

The steps to generate the keys are the following:

### Compile generator

```
cd PKI/
javac *.java
```

### Generate keys

```
java RSAKeyGenerator w ./<IDENTIFIER>.priv ./<IDENTIFIER>.pub
```

## Configuration Files

### Client configuration

Can be found inside the `resources/` folder of the `Client` module.

```json
{
    "id": <CLIENT_ID>,
    "hostname": "localhost",
    "port": <CLIENT_PORT>,
    "publicKeyPath": "../PKI/client<CLIENT_ID>.pub",
    "privateKeyPath": "../PKI/client<CLIENT_ID>.priv",
    "byzantineBehavior": <BYZANTINE_BEHAVIOR>,
}
```

### Node configuration

Can be found inside the `resources/` folder of the `Service` module.

```json
{
    "id": <NODE_ID>,
    "isLeader": <IS_LEADER>,
    "hostname": "localhost",
    "port": <NODE_PORT>,
    "clientPort": <CLIENT_PORT>,
    "publicKeyPath": "../PKI/node<CLIENT_ID>.pub",
    "privateKeyPath": "../PKI/node<CLIENT_ID>.priv",
    "byzantineBehavior": <BYZANTINE_BEHAVIOR>,
}
```

Note: clientPort is the port where the client will connect to the node.

## Dependencies

To install the necessary dependencies run the following command:

```bash
./install_deps.sh
```

This should install the following dependencies:

- [Google's Gson](https://github.com/google/gson) - A Java library that can be used to convert Java Objects into their JSON representation.

## Puppet Master

The puppet master is a python script `puppet-master.py` which is responsible for starting the nodes
and clients of the blockchain.
The script runs with `kitty` terminal emulator by default since it's installed on the RNL labs.

To run the script you need to have `python3` installed.
The script has arguments which can be modified:

- `terminal` - the terminal emulator used by the script
- `debug` - if set to "True" the client process will print logs about the
  perfect link and library operations
- `server_config` - a string from the array `server_configs` which contains the possible configurations for the blockchain nodes

Run the script with the following command:

```bash
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

Run with arguments

```
cd <module>/
mvn compile exec:java -Dexec.args="..."
```
