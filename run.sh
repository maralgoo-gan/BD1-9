#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v javac >/dev/null 2>&1; then
  echo "Java JDK олдсонгүй. JDK 17 эсвэл 21 суулгаад java -version, javac -version командаар шалгана уу." >&2
  exit 1
fi
mkdir -p out
javac -encoding UTF-8 -d out src/*.java
java -Dfile.encoding=UTF-8 -cp out Main
