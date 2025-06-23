#!/bin/bash

set -eo pipefail

./gradlew :rskj-core:test --tests "co.rsk.fasterblocks.DifficultyUpdateBenchmark.runBenchTest" -i > output.txt
# python3 benchmark_difficulty_draw_chart.py
