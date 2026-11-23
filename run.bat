@echo off
chcp 65001 > nul
cd /d "%~dp0"
where javac >nul 2>&1
if errorlevel 1 (
  echo JDK 17+ required: javac was not found.
  exit /b 1
)
if not exist out mkdir out
javac -encoding UTF-8 -d out src\*.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp out Main
