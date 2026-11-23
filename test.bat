@echo off
chcp 65001 > nul
cd /d "%~dp0"
where javac >nul 2>&1
if errorlevel 1 (
  echo JDK 17+ required: javac was not found.
  exit /b 1
)
if not exist out mkdir out
javac -encoding UTF-8 -d out src\*.java tests\*.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -ea -cp out PoolTests
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -ea -cp out TimelineTests
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -ea -cp out BD2RegressionTests
if errorlevel 1 exit /b 1
