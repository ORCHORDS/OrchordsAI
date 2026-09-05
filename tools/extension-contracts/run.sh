#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
command -v kotlinc >/dev/null || { echo 'Kotlin compiler is required.' >&2; exit 1; }
command -v java >/dev/null || { echo 'Java runtime is required.' >&2; exit 1; }
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
src=app/src/main/java/com/orchords/orchordsai/data/extensions
kotlinc "$src/BuiltInLibrary.kt" "$src/BuiltInLibraryContent.kt" "$src/BuiltInModes.kt" \
  "$src/BuiltInLorebooks.kt" "$src/BuiltInEngineeringSkills.kt" "$src/BuiltInProductivitySkills.kt" \
  tools/extension-contracts/LibraryContractMain.kt -include-runtime -d "$tmp/library.jar"
java -jar "$tmp/library.jar"
kotlinc "$src/ConnectorPolicy.kt" tools/extension-contracts/ConnectorPolicyMain.kt \
  -include-runtime -d "$tmp/policy.jar"
java -jar "$tmp/policy.jar"
