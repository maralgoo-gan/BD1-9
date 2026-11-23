#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out src/*.java tests/*.java
java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -ea -cp out PoolTests
java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Djava.awt.headless=true -ea -cp out TimelineTests

java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Djava.awt.headless=true -ea -cp out BD2RegressionTests

# Linux graphical session: run the optional GUI smoke test automatically.
if [[ -n "${DISPLAY:-}" ]] && command -v xdpyinfo >/dev/null 2>&1 && xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
  java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -ea -cp out GuiSmokeTest
else
  echo "GUI smoke test-г DISPLAY орчинд тусад нь ажиллуулна (Mac: java -cp out GuiSmokeTest)."
fi
