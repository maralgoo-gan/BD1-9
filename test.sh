#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out src/*.java tests/*.java
java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -ea -cp out PoolTests
java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Djava.awt.headless=true -ea -cp out TimelineTests
