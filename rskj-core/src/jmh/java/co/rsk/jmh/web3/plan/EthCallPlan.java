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

import co.rsk.jmh.web3.BenchmarkWeb3Exception;
import co.rsk.jmh.web3.e2e.RskModuleWeb3j;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.math.BigInteger;

@State(Scope.Benchmark)
public class EthCallPlan extends BasePlan {

    private RskModuleWeb3j.EthCallArguments ethCallArguments;

    @Override
    @Setup(Level.Trial) // move to "Level.Iteration" in case we set a batch size at some point
    public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
        super.setUp(params);

        ethCallArguments = buildEthCallArguments();
    }

    private RskModuleWeb3j.EthCallArguments buildEthCallArguments() {
        RskModuleWeb3j.EthCallArguments args = new RskModuleWeb3j.EthCallArguments();

        args.setGasLimit(configuration.getString("eth_call.gasLimit"));
        args.setFrom(configuration.getString("eth_call.transaction.from"));
        args.setTo(configuration.getString("eth_call.transaction.to"));
        args.setGas(configuration.getString("eth_call.transaction.gas"));
        args.setGasPrice(configuration.getString("eth_call.transaction.gasPrice"));
        args.setValue(configuration.getString("eth_call.transaction.value"));
        args.setData(configuration.getString("eth_call.transaction.input"));

        return args;
    }

    public BigInteger getBlockNumber() {
        return new BigInteger(configuration.getString("eth_call.transaction.blockNumber").replace("0x", ""), 16);
    }

    public RskModuleWeb3j.EthCallArguments getEthCallArguments() {
        return ethCallArguments;
    }
}
