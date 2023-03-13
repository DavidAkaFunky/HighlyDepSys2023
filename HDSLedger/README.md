# HDSLedger

## Always Remember Rito Theory

Maven Lifecycle has 8-stages

| Stage               | Command     |
| ------------------- | ----------- |
| Validation          | 😏          |
| Compilation         | mvn compile |
| Testing             | mvn test    |
| Packaging           | mvn package |
| Integration testing | 😏          |
| Verification        | 😏          |
| Installation        | 😏          |
| Deployment          | mvn deploy  |

## Public Key Infrastructure

### Compile generator
```
javac *.java
```
### Generating keys
```
java RSAKeyGenerator w ./<NODE_ID>.priv ./<NODE_ID>.pub
```
### Test keys

## Maven

### Instalation

Compile and install all modules

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

## To run this outside of Intellij (outdated)

```
java -classpath target/classes blockchain.Node
```

## Built With

- [Maven](https://maven.apache.org/) - Build and dependency management tool;
