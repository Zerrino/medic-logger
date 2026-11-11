#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
fi
mkdir -p .tools/core-tests artifacts/core-tests
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -encoding UTF-8 -d .tools/core-tests \
  app/src/main/java/fr/mediclogger/app/Models.java \
  app/src/main/java/fr/mediclogger/app/ExportWriter.java \
  app/src/main/java/fr/mediclogger/app/ReminderRule.java \
  app/src/test/java/fr/mediclogger/app/CoreTests.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp .tools/core-tests fr.mediclogger.app.CoreTests artifacts/core-tests
python3 scripts/verify-exports.py artifacts/core-tests
