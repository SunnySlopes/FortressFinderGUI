#!/usr/bin/env sh
# Generate JNI C header from FortressFinderBridge.java
set -e
cd "$(dirname "$0")/.."
JAVA_SRC=java/sunnyslopes/fortressfinder/FortressFinderBridge.java
OUT_DIR=jni/generated
mkdir -p "$OUT_DIR"
javac -encoding UTF-8 -h "$OUT_DIR" "$JAVA_SRC"
echo "Generated: $OUT_DIR/sunnyslopes_fortressfinder_FortressFinderBridge.h"
