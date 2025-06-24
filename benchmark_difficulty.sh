#!/bin/bash

set -eo pipefail

./gradlew :rskj-core:test --tests "co.rsk.fasterblocks.DifficultyUpdateBenchmark.runBenchTest" -i
mv ./rskj-core/bench.csv ./bench.csv
clear
python3 benchmark_difficulty_analysis.py
open fp_vs_bigdecimal_time_per_scale.png &&\
open rel_error_per_scale.png &&\
open benchmark_rankings.txt
