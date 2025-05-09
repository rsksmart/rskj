/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.jmh.runners;

import co.rsk.jmh.helpers.OptionsHelper;
import co.rsk.jmh.web3.LogIndexBenchmark;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.Options;

public class BenchmarkLogIndexRunner {

    public static void main(String[] args) throws CommandLineOptionException, RunnerException {
        Options opt = OptionsHelper.createE2EBuilder(args, "result_rpc_e2e_log_index.csv")
                .include(LogIndexBenchmark.class.getName())
                .build();
        new Runner(opt).run();
    }
}
