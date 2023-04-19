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

package co.rsk.jmh.web3;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.file.Path;
import java.nio.file.Paths;

public class BenchmarkWeb3RunnerE2E {

    public static void main(String[] args) throws Exception {
        Path resultDir = Paths.get(System.getProperty("user.dir"), "build", "reports", "jmh");
        resultDir.toFile().mkdirs();

        Options opt = new OptionsBuilder()
                .threads(1)
                .param("suite", "e2e")
                .param("host", "http://localhost:4444")
                .result(resultDir + "/result_test.txt")
                .shouldFailOnError(true)
                .forks(1)
                .build();

        new Runner(opt).run();
    }

}
