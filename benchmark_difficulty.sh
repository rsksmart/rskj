#!/bin/bash

set -eo pipefail

./gradlew :rskj-core:test --tests "co.rsk.fasterblocks.DifficultyUpdateBenchmark.runBenchTest" -i
mv ./rskj-core/bench_avg.csv ./bench_avg.csv
mv ./rskj-core/bench_med.csv ./bench_med.csv
clear
python3 plot_benchmark.py
open time_per_scale_avg.png && open error_per_scale_avg.png
open time_per_scale_med.png && open error_per_scale_med.png
