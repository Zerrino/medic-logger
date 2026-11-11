#!/bin/zsh
set -euo pipefail
project_root="${0:A:h:h}"
cd "$project_root"
python3 scripts/generate-legal.py
python3 scripts/generate-ios-resources.py
python3 scripts/generate-ios-project.py
mkdir -p artifacts ios/build
xcodebuild -project ios/MedicLogger.xcodeproj -scheme MedicLogger \
  -configuration Release -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath ios/DerivedData ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build
ditto -c -k --sequesterRsrc --keepParent \
  ios/DerivedData/Build/Products/Release-iphonesimulator/MedicLogger.app \
  artifacts/Medic-Logger-iOS-Simulator.zip
shasum -a 256 artifacts/Medic-Logger-iOS-Simulator.zip > artifacts/Medic-Logger-iOS-Simulator.zip.sha256
if [[ "${1:-}" == '--device-archive' ]]; then
  xcodebuild -project ios/MedicLogger.xcodeproj -scheme MedicLogger \
    -configuration Release -destination 'generic/platform=iOS' \
    -derivedDataPath ios/DerivedData -archivePath ios/build/MedicLogger.xcarchive \
    CODE_SIGNING_ALLOWED=NO archive
fi
