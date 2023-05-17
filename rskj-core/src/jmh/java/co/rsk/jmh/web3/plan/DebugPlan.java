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
import co.rsk.jmh.web3.factory.TransactionFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class DebugPlan extends BasePlan {

    private String transactionVT;
    private String transactionContractCreation;
    private String transactionContractCall;
    private String block;

    private final Map<String, String> debugParams = new HashMap<>();

    @Override
    @Setup(Level.Trial)
    public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
        super.setUp(params);

        long nonce = 0;

        String address = configuration.getNullableProperty("debug.txFrom");
        if (address != null) {
            nonce = Optional.ofNullable(web3Connector.ethGetTransactionCount(address))
                    .map(BigInteger::longValue)
                    .orElseThrow(() -> new BenchmarkWeb3Exception("Could not get account nonce"));
        }

        boolean transactionCreated = false;

        transactionVT = configuration.getNullableProperty("debug.transactionVT");
        if (transactionVT == null) {
            transactionCreated = true;
            transactionVT = web3Connector.ethSendTransaction(TransactionFactory.buildTransactionVT(configuration, BigInteger.valueOf(nonce++)));
        }

        transactionContractCreation = configuration.getNullableProperty("debug.transactionContractCreation");
        if (transactionContractCreation == null) {
            transactionCreated = true;
            transactionContractCreation = web3Connector.ethSendTransaction(TransactionFactory.buildTransactionContractCreation(configuration, BigInteger.valueOf(nonce++)));
        }

        transactionContractCall = configuration.getNullableProperty("debug.transactionContractCall");
        if (transactionContractCall == null) {
            transactionCreated = true;
            transactionContractCall = web3Connector.ethSendTransaction(TransactionFactory.buildTransactionContractCall(configuration, BigInteger.valueOf(nonce)));
        }

        debugParams.put("disableMemory", "true");
        debugParams.put("disableStack", "true");
        debugParams.put("disableStorage", "true");

        if (transactionCreated) {
            try {
                // give time for created transactions to be part of a block
                BenchmarkHelper.waitForBlocks(configuration);
            } catch (InterruptedException e) { // NOSONAR
                throw new BenchmarkWeb3Exception("Error waiting for blocks: " + e.getMessage());
            }
        }

        block = configuration.getNullableProperty("debug.block");
        if (block == null) {
            block = web3Connector.ethGetBlockHashByNumber(BigInteger.ONE); // naive, valid only for regtest mode
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws InterruptedException {
        // TODO(iago) optimise this when working on performance
        TimeUnit.SECONDS.sleep(10); // give node a rest after calling debug methods
    }

    public String getTransactionVT() {
        return transactionVT;
    }

    public String getTransactionContractCreation() {
        return transactionContractCreation;
    }

    public String getTransactionContractCall() {
        return transactionContractCall;
    }

    public String getBlock() {
        return block;
    }

    public Map<String, String> getDebugParams() {
        return debugParams;
    }
}
