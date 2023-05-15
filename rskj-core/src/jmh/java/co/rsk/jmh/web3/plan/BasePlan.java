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

package co.rsk.jmh.web3.plan;

import co.rsk.jmh.Config;
import co.rsk.jmh.web3.BenchmarkWeb3;
import co.rsk.jmh.web3.BenchmarkWeb3Exception;
import co.rsk.jmh.web3.e2e.Web3ConnectorE2E;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;

@State(Scope.Benchmark)
public class BasePlan {

    @Param("regtest")
    public String config;

    @Param({"E2E"})
    public BenchmarkWeb3.Suites suite;

    @Param("http://localhost:4444")
    public String host;

    protected Web3ConnectorE2E web3Connector;

    protected Config configuration;

    @Setup(Level.Trial)
    public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
        configuration = Config.create(config);

        switch (suite) {
            case E2E:
                web3Connector = Web3ConnectorE2E.create(host);
                break;
            case INT:
            case UNIT:
                throw new BenchmarkWeb3Exception("Suite not implemented yet: " + suite);
            default:
                throw new BenchmarkWeb3Exception("Unknown suite: " + suite);
        }

    }

    public Web3ConnectorE2E getWeb3Connector() {
        return web3Connector;
    }

    public Config getConfiguration() {
        return configuration;
    }

    public String getConfig() {
        return config;
    }

    public BenchmarkWeb3.Suites getSuite() {
        return suite;
    }

    public String getHost() {
        return host;
    }
}
