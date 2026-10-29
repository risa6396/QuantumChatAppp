#!/bin/sh
# Compile the whole project from source (needs a JDK, Java 8 or newer, not just a JRE).
#   ./build.sh         compile into ./out
#   ./build.sh test    compile, then run the headless test suites
set -e
cd "$(dirname "$0")"
rm -rf out
mkdir out
javac -d out $(find quantumchat -name "*.java")
echo "Build OK -> ./out"
echo "Run the app:   java -cp out quantumchat.gui.QuantumChatGUI"
if [ "$1" = "test" ]; then
  javac -cp out -d out test/FrameworkSmokeTest.java test/SocketSmokeTest.java test/KeyGateTest.java test/FileTransferTest.java
  java -cp out FrameworkSmokeTest
  java -cp out SocketSmokeTest
  java -cp out KeyGateTest
  java -cp out FileTransferTest
fi
