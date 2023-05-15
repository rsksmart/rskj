/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

package co.rsk.jmh.helpers;

import co.rsk.jmh.web3.BenchmarkWeb3;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.file.Path;
import java.nio.file.Paths;

public class OptionsHelper {

    private OptionsHelper() {
    }

    public static ChainedOptionsBuilder createE2EBuilder(String[] args, String reportFileName) throws CommandLineOptionException {
        Path resultDir = Paths.get(System.getProperty("user.dir"), "build", "reports", "jmh");

        // TODO(iago) fix
        resultDir.toFile().mkdirs();

        return new OptionsBuilder()
                .param("suite", BenchmarkWeb3.Suites.E2E.name())
                .param("host", ParamsHelper.getRequired("host", args))
                .param("config", ParamsHelper.getRequired("config", args))
                .mode(Mode.SingleShotTime) // we cannot flood the server with http requests
                .forks(2)
                .warmupIterations(1) // for RPC calls usually one warmup call is enough
                .result(resultDir + "/" + reportFileName)
                .resultFormat(ResultFormatType.CSV)
                .shouldFailOnError(true);
    }

}
