#!/usr/bin/env bash
# Fast Gradle build with daemon + local cache. Waits for the log to settle
# instead of sleeping a fixed amount, then prints only the actionable lines.
set -uo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

LOG="${LOG:-/tmp/gradle-build.log}"
: > "$LOG"
# Gradle stays resident between invocations; --parallel + --build-cache reuse the local cache.
./gradlew "$@" --daemon --parallel --build-cache > "$LOG" 2>&1 &
PID=$!

# inotifywait returns once the file stops changing for 3s, so we do not guess a sleep length.
while kill -0 "$PID" 2>/dev/null; do
  inotifywait -qq -t 3 -e modify,close_write "$LOG" 2>/dev/null
done
wait "$PID"; STATUS=$?

echo "exit=$STATUS"
rg -N '^(e: |w: )|^FAILURE|^BUILD (SUCCESSFUL|FAILED)|^> Task .*FAILED|tests? completed' "$LOG" | tail -80
exit "$STATUS"
