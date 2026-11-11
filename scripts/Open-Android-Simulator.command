#!/bin/zsh
set -euo pipefail
cd "${0:A:h:h}"
exec zsh scripts/run-android-simulator.sh
