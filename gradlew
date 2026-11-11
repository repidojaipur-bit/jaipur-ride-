#!/bin/sh

APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -f "$WRAPPER_JAR" ]; then
    if command -v java >/dev/null 2>&1; then
        exec java -Xmx2048m -jar "$WRAPPER_JAR" "$@"
    else
        echo "ERROR: JAVA_HOME / java executable not found in PATH." >&2
        exit 1
    fi
fi

GRADLE_BIN="$(command -v gradle 2>/dev/null || true)"
if [ -n "$GRADLE_BIN" ] && [ -x "$GRADLE_BIN" ] && [ ! -d "$GRADLE_BIN" ]; then
    exec "$GRADLE_BIN" "$@"
fi

echo "ERROR: Neither gradle/wrapper/gradle-wrapper.jar nor a system gradle binary is available." >&2
exit 1
