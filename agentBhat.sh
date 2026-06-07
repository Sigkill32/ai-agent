#!/bin/bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

SCRIPT_DIR="$(dirname "$0")"
CALL_DIR="$PWD"

cd "$SCRIPT_DIR"
mvn clean package -q || exit 1

cd "$CALL_DIR"
java -jar "$SCRIPT_DIR/target/ai-agent-1.0-SNAPSHOT-jar-with-dependencies.jar"
