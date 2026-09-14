#!/bin/sh

set -eu

SCRIPT_DIRECTORY=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIRECTORY/.." && pwd)
ASSET_ROOT="$PROJECT_ROOT/app/src/main/assets"
CATALOG="$ASSET_ROOT/research_pack/catalog.properties"

checks=0
failures=0

property() {
    awk -F= -v requested_key="$1" '
        $1 == requested_key {
            sub(/^[^=]*=/, "")
            print
            found = 1
            exit
        }
        END { if (!found) exit 1 }
    ' "$CATALOG"
}

record_check() {
    checks=$((checks + 1))
    if [ "$1" != "$2" ]; then
        failures=$((failures + 1))
        printf 'FAIL %s: expected=%s actual=%s\n' "$3" "$1" "$2"
    fi
}

sha256_file() {
    shasum -a 256 "$1" | awk '{ print $1 }'
}

verify_direct_asset() {
    label=$1
    relative_path=$2
    expected_sha=$3
    expected_bytes=$4
    asset="$ASSET_ROOT/$relative_path"

    if [ ! -f "$asset" ]; then
        failures=$((failures + 1))
        printf 'FAIL %s: missing asset %s\n' "$label" "$relative_path"
        return
    fi
    actual_sha=$(sha256_file "$asset")
    actual_bytes=$(wc -c < "$asset" | tr -d ' ')
    record_check "$expected_sha" "$actual_sha" "$label sha256"
    record_check "$expected_bytes" "$actual_bytes" "$label bytes"
}

verify_dataset() {
    index=$1
    prefix="dataset.$index"
    relative_path=$(property "$prefix.assetPath")
    expected_sha=$(property "$prefix.sha256")
    expected_bytes=$(property "$prefix.rawBytes")
    expected_rows=$(property "$prefix.rowCount")
    asset="$ASSET_ROOT/$relative_path"

    if [ ! -f "$asset" ] || ! gzip -t "$asset"; then
        failures=$((failures + 1))
        printf 'FAIL %s: missing or invalid gzip asset %s\n' "$prefix" "$relative_path"
        return
    fi
    actual_sha=$(gzip -dc "$asset" | shasum -a 256 | awk '{ print $1 }')
    actual_bytes=$(gzip -dc "$asset" | wc -c | tr -d ' ')
    actual_lines=$(gzip -dc "$asset" | wc -l | tr -d ' ')
    actual_rows=$((actual_lines - 1))
    record_check "$expected_sha" "$actual_sha" "$prefix raw sha256"
    record_check "$expected_bytes" "$actual_bytes" "$prefix raw bytes"
    record_check "$expected_rows" "$actual_rows" "$prefix data rows"
}

dataset_count=$(property datasetCount)
dataset_index=0
while [ "$dataset_index" -lt "$dataset_count" ]; do
    verify_dataset "$dataset_index"
    dataset_index=$((dataset_index + 1))
done

one_micron_count=$(property oneMicronRunCount)
run_index=0
while [ "$run_index" -lt "$one_micron_count" ]; do
    prefix="oneMicronRun.$run_index"
    verify_direct_asset "$prefix model" "$(property "$prefix.modelAssetPath")" "$(property "$prefix.modelSha256")" "$(property "$prefix.modelBytes")"
    verify_direct_asset "$prefix report" "$(property "$prefix.reportAssetPath")" "$(property "$prefix.reportSha256")" "$(property "$prefix.reportBytes")"
    verify_direct_asset "$prefix history" "$(property "$prefix.historyAssetPath")" "$(property "$prefix.historySha256")" "$(property "$prefix.historyBytes")"
    run_index=$((run_index + 1))
done

classifier_count=$(property classifierRunCount)
run_index=0
while [ "$run_index" -lt "$classifier_count" ]; do
    prefix="classifierRun.$run_index"
    verify_direct_asset "$prefix summary" "$(property "$prefix.summaryAssetPath")" "$(property "$prefix.summarySha256")" "$(property "$prefix.summaryBytes")"
    verify_direct_asset "$prefix history" "$(property "$prefix.historyAssetPath")" "$(property "$prefix.historySha256")" "$(property "$prefix.historyBytes")"
    model_count=$(property "$prefix.modelCount")
    model_index=0
    while [ "$model_index" -lt "$model_count" ]; do
        model_prefix="$prefix.model.$model_index"
        verify_direct_asset "$model_prefix" "$(property "$model_prefix.assetPath")" "$(property "$model_prefix.sha256")" "$(property "$model_prefix.bytes")"
        model_index=$((model_index + 1))
    done
    run_index=$((run_index + 1))
done

printf 'Bundled pack verification: checks=%d failures=%d\n' "$checks" "$failures"
test "$failures" -eq 0
