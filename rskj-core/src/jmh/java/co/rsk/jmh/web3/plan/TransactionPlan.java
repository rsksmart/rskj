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
import co.rsk.jmh.web3.factory.TransactionFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthTransaction;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Optional;

@State(Scope.Benchmark)
public class TransactionPlan extends BasePlan {

    private Iterator<Transaction> transactionsVT;
    private Iterator<Transaction> transactionsContractCreation;
    private Iterator<Transaction> transactionsContractCall;
    private org.web3j.protocol.core.methods.response.Transaction contractCallTransaction;
    private BigInteger blockNumber;

    @Override
    @Setup(Level.Trial) // move to "Level.Iteration" in case we set a batch size at some point
    public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
        super.setUp(params);

        String address = configuration.getString("sendTransaction.from");

        long nonce = Optional.ofNullable(web3Connector.ethGetTransactionCount(address))
                .map(BigInteger::longValue)
                .orElseThrow(() -> new BenchmarkWeb3Exception("Could not get account nonce"));

        long warmupIters = (long) params.getWarmup().getCount() * params.getWarmup().getBatchSize(); // in case we set a batch size at some point
        long measurementIters = (long) params.getMeasurement().getCount() * params.getMeasurement().getBatchSize();  // in case we set a batch size at some point
        long numOfTransactions = warmupIters + measurementIters;

        transactionsVT = TransactionFactory.createTransactions(TransactionFactory.TransactionType.VT, configuration, nonce, numOfTransactions).listIterator();
        transactionsContractCreation = TransactionFactory.createTransactions(TransactionFactory.TransactionType.CONTRACT_CREATION, configuration, nonce, numOfTransactions).listIterator();
        transactionsContractCall = TransactionFactory.createTransactions(TransactionFactory.TransactionType.CONTRACT_CALL, configuration, nonce, numOfTransactions).listIterator();
        blockNumber = web3Connector.ethBlockNumber();
    }

    private org.web3j.protocol.core.methods.response.Transaction setupContractCallTransaction(Transaction transaction) throws IOException {
        if (super.getConfig().equals("regtest")) {
            EthSendTransaction sendTransactionResponse = rskModuleWeb3j.ethSendTransaction(transaction).send();
            EthTransaction transactionResponse = rskModuleWeb3j.ethGetTransactionByHash(sendTransactionResponse.getResult()).send();
            return transactionResponse.getResult();
        } else {
            EthTransaction transactionResponse = rskModuleWeb3j.ethGetTransactionByHash(configuration.getString("eth.transactionContractCallHash")).send();
            return transactionResponse.getResult();
        }
    }

    @TearDown(Level.Trial) // move to "Level.Iteration" in case we set a batch size at some point
    public void tearDown() throws InterruptedException {
        // wait for new blocks to have free account slots for transaction creation
        BenchmarkHelper.waitForBlocks(configuration);
    }

    public Iterator<Transaction> getTransactionsVT() {
        return transactionsVT;
    }

    public Iterator<Transaction> getTransactionsContractCreation() {
        return transactionsContractCreation;
    }

    public Iterator<Transaction> getTransactionsContractCall() {
        return transactionsContractCall;
    }

    public org.web3j.protocol.core.methods.response.Transaction getContractCallTransaction() {
        if (contractCallTransaction == null) {
            try {
                contractCallTransaction = setupContractCallTransaction(transactionsContractCall.next());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return contractCallTransaction;
    }

    public RskModuleWeb3j.EthCallArguments getEthCallArguments() {
        RskModuleWeb3j.EthCallArguments args = new RskModuleWeb3j.EthCallArguments();
        org.web3j.protocol.core.methods.response.Transaction contractCallTransaction = getContractCallTransaction();

        args.setFrom(contractCallTransaction.getFrom());
        args.setTo(contractCallTransaction.getTo());
        args.setGas("0x" + contractCallTransaction.getGas().toString(16));
        args.setGasPrice("0x" + contractCallTransaction.getGasPrice().toString(16));
        args.setValue("0x" + contractCallTransaction.getValue().toString(16));
        args.setNonce("0x" + contractCallTransaction.getNonce().toString(16));
        args.setChainId("0x" + BigInteger.valueOf(contractCallTransaction.getChainId()).toString(16));
        args.setData(contractCallTransaction.getInput());

        return args;
    }

    public BigInteger getBlockNumber() {
        return blockNumber;
    }
}
