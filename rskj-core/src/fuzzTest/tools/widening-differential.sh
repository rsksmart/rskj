#!/usr/bin/env bash
#
# Differential decode fuzz: compare accept/reject verdicts between this working tree and a
# baseline build, on structurally mutated transactions seeded from real mainnet history.
#
# Usage: widening-differential.sh <baseline-jar> [extra-seed-file]
#
# The baseline jar is a fat jar of the build to compare against (e.g. a master build). The
# verdict emitter is reflection-only, so the same source compiles against either jar and picks
# Transaction.fromRaw or new ImmutableTransaction, whichever that build has.
#
# Exits non-zero on a widening, value or corpus divergence. Narrowing is reported, not failed:
# whether it is a fork depends on activation gating, which this tool does not know.
set -euo pipefail

BASELINE_JAR=${1:?usage: widening-differential.sh <baseline-jar> [extra-seed-file]}
SEED_FILE=${2:-}

REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)
SRC="$REPO/rskj-core/src/fuzzTest/java/co/rsk/core/transaction/widening"
OUT="$REPO/rskj-core/build/fuzz-widening"
BASELINE_CLASSES="$OUT/baseline-classes"

GRADLE_ARGS=(--quiet fuzzTest --tests co.rsk.core.transaction.widening.TransactionWideningFuzzTest -x fuzzCoverageTestReport)
if [ -n "$SEED_FILE" ]; then
    GRADLE_ARGS+=("-Dwidening.seedFile=$SEED_FILE")
fi

echo "==> current build: emitting corpus and verdict stream"
(cd "$REPO" && ./gradlew "${GRADLE_ARGS[@]}")

echo "==> baseline build: compiling the verdict emitter against $BASELINE_JAR"
mkdir -p "$BASELINE_CLASSES"
javac -nowarn -cp "$BASELINE_JAR" -d "$BASELINE_CLASSES" \
    "$SRC/Hex.java" "$SRC/RlpFrames.java" "$SRC/WideningCorpus.java" "$SRC/DecodeVerdicts.java"

echo "==> baseline build: emitting verdicts over the same corpus"
java -cp "$BASELINE_JAR:$BASELINE_CLASSES" co.rsk.core.transaction.widening.DecodeVerdicts \
    emit "$OUT/corpus.tsv" "$OUT/verdicts-baseline.jsonl"

echo "==> diffing the two verdict streams"
java -cp "$BASELINE_JAR:$BASELINE_CLASSES" co.rsk.core.transaction.widening.DecodeVerdicts \
    diff "$OUT/verdicts-baseline.jsonl" "$OUT/verdicts-current.jsonl"
