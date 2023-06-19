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

import co.rsk.jmh.helpers.BenchmarkHelper;
import co.rsk.jmh.web3.BenchmarkWeb3Exception;
import co.rsk.jmh.web3.e2e.RskModuleWeb3j;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.math.BigInteger;

@State(Scope.Benchmark)
public class EthCallPlan extends BasePlan {

    private BigInteger blockNumber;
    private RskModuleWeb3j.EthCallArguments ethCallArguments;

    @Override
    @Setup(Level.Trial) // move to "Level.Iteration" in case we set a batch size at some point
    public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
        super.setUp(params);

        blockNumber = web3Connector.ethBlockNumber();
        ethCallArguments = buildEthCallArguments();
    }

    private org.web3j.protocol.core.methods.response.Transaction setupTransaction() {
        org.web3j.protocol.core.methods.response.Transaction tx = new org.web3j.protocol.core.methods.response.Transaction();

        tx.setHash(configuration.getString("eth_call.transaction.hash"));
        tx.setNonce(configuration.getString("eth_call.transaction.nonce"));
        tx.setBlockHash(configuration.getString("eth_call.transaction.blockHash"));
        tx.setBlockNumber(configuration.getString("eth_call.transaction.blockNumber"));
        tx.setTransactionIndex(configuration.getString("eth_call.transaction.transactionIndex"));
        tx.setFrom(configuration.getString("eth_call.transaction.from"));
        tx.setTo(configuration.getString("eth_call.transaction.to"));
        tx.setGas(configuration.getString("eth_call.transaction.gas"));
        tx.setGasPrice(configuration.getString("eth_call.transaction.gasPrice"));
        tx.setValue(configuration.getString("eth_call.transaction.value"));
        tx.setInput(configuration.getString("eth_call.transaction.input"));
        tx.setType(configuration.getString("eth_call.transaction.type"));

        return tx;
    }

    @TearDown(Level.Trial) // move to "Level.Iteration" in case we set a batch size at some point
    public void tearDown() throws InterruptedException {
        // wait for new blocks to have free account slots for transaction creation
        BenchmarkHelper.waitForBlocks(configuration);
    }


    private RskModuleWeb3j.EthCallArguments buildEthCallArguments() {
        RskModuleWeb3j.EthCallArguments args = new RskModuleWeb3j.EthCallArguments();
        org.web3j.protocol.core.methods.response.Transaction tx = setupTransaction();

        args.setFrom(tx.getFrom());
        args.setTo(tx.getTo());
        args.setGas("0x" + tx.getGas().toString(16));
        args.setGasPrice("0x" + tx.getGasPrice().toString(16));
        args.setValue("0x" + tx.getValue().toString(16));
        args.setNonce("0x" + tx.getNonce().toString(16));
        args.setChainId("0x" + BigInteger.valueOf(tx.getChainId()).toString(16));
        args.setData(tx.getInput());

        return args;
    }

    public BigInteger getBlockNumber() {
        return new BigInteger(configuration.getString("eth_call.transaction.blockNumber").replace("0x", ""), 16);
    }

    public RskModuleWeb3j.EthCallArguments getEthCallArguments() {
        return ethCallArguments;
    }
}
