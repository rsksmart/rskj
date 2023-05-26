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
import co.rsk.jmh.web3.EthMethodsConfig;
import co.rsk.jmh.web3.e2e.RskDebugModuleWeb3j;
import co.rsk.jmh.web3.e2e.RskModuleWeb3j;
import co.rsk.jmh.web3.e2e.RskTraceModuleWeb3j;
import co.rsk.jmh.web3.e2e.Web3ConnectorE2E;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.web3j.protocol.http.HttpService;

import java.time.Duration;

@State(Scope.Benchmark)
public class BasePlan {
    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected RskDebugModuleWeb3j debugModuleWeb3j;
    protected RskModuleWeb3j rskModuleWeb3j;
    protected RskTraceModuleWeb3j traceModuleWeb3j;

    @Param("regtest")
    public String config;
    @Param({"E2E"})
    public BenchmarkWeb3.Suites suite;
    @Param("http://localhost:4444")
    public String host;

    protected Web3ConnectorE2E web3Connector;
    protected Config configuration;
    protected EthMethodsConfig ethMethodsConfig;


    @Setup(Level.Trial)
    public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        configuration = Config.create(config);

        switch (suite) {
            case E2E:
                OkHttpClient httpClient = HttpService.getOkHttpClientBuilder()
                        .readTimeout(Duration.ofSeconds(120))
                        .writeTimeout(Duration.ofSeconds(120))
                        .callTimeout(Duration.ofSeconds(120))
                        .connectTimeout(Duration.ofSeconds(120))
                        .build();

                this.debugModuleWeb3j = new RskDebugModuleWeb3j(new HttpService(host, httpClient));
                this.rskModuleWeb3j = new RskModuleWeb3j(new HttpService(host, httpClient));
                this.traceModuleWeb3j = new RskTraceModuleWeb3j(new HttpService(host));

                web3Connector = Web3ConnectorE2E.create(host, debugModuleWeb3j, rskModuleWeb3j, traceModuleWeb3j);
                break;
            case INT:
            case UNIT:
                throw new BenchmarkWeb3Exception("Suite not implemented yet: " + suite);
            default:
                throw new BenchmarkWeb3Exception("Unknown suite: " + suite);
        }
        ethMethodsConfig = new EthMethodsConfig(configuration);
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

    public EthMethodsConfig getEthMethodsConfig() {
        return ethMethodsConfig;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public RskDebugModuleWeb3j getDebugModuleWeb3j() {
        return debugModuleWeb3j;
    }

    public RskModuleWeb3j getRskModuleWeb3j() {
        return rskModuleWeb3j;
    }

    public RskTraceModuleWeb3j getTraceModuleWeb3j() {
        return traceModuleWeb3j;
    }
}
