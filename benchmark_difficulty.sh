#!/bin/bash

set -eo pipefail

./gradlew :rskj-core:test --tests "co.rsk.fasterblocks.DifficultyUpdateBenchmark.runBenchTest" -i
mv ./rskj-core/bench.csv ./bench.csv
clear
python3 plot_benchmark.py
open time_per_scale.png && open error_per_scale.png
