#!/usr/bin/env bash
mvn compile
java -classpath target/classes blockchain.Node 1 L localhost 3000 & echo $!
java -classpath target/classes blockchain.Node 2 S localhost 3001 & echo $!