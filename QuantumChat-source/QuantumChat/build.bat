@echo off
REM Compile the whole project from source (needs a JDK, Java 8 or newer, not just a JRE).
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
dir /s /b quantumchat\*.java > sources.txt
javac -d out @sources.txt
if errorlevel 1 (del sources.txt & exit /b 1)
del sources.txt
echo Build OK -^> out
echo Run the app: java -cp out quantumchat.gui.QuantumChatGUI
