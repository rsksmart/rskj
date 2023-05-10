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

package co.rsk.jmh.web3.factory;

import co.rsk.jmh.Config;
import org.web3j.protocol.core.methods.request.Transaction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class TransactionFactory {

    private TransactionFactory() {

    }

    public enum TransactionType {
        VT, CONTRACT_CREATION, CONTRACT_CALL
    }

    public static List<Transaction> createTransactions(TransactionType transactionType, Config config, long startingNonce, long numOfTransactions) {
        List<Transaction> transactionList = new ArrayList<>();

        for (int i = 0; i < numOfTransactions; i++) {
            BigInteger newNonce = BigInteger.valueOf(startingNonce + i);

            switch (transactionType) {
                case VT:
                    transactionList.add(buildTransactionVT(config, newNonce));
                    break;
                case CONTRACT_CALL:
                    transactionList.add(buildTransactionContractCall(config, newNonce));
                    break;
                case CONTRACT_CREATION:
                    transactionList.add(buildTransactionContractCreation(config, newNonce));
                    break;
            }
        }

        return transactionList;
    }

    public static Transaction buildTransactionVT(Config config, BigInteger nonce) {
        String from = config.getString("sendTransaction.from");
        String to = config.getString("sendTransaction.to");

        BigInteger gasLimit = BigInteger.valueOf(21_000);
        BigInteger gasPrice = BigInteger.valueOf(59_240_000);

        BigInteger value = BigInteger.valueOf(70_000);
        String data = null;

        Long chainId = config.getLong("chainId");

        return new Transaction(from, nonce, gasPrice, gasLimit, to, value, data, chainId, null, null);
    }

    public static Transaction buildTransactionContractCreation(Config config, BigInteger nonce) {
        String from = config.getString("sendTransaction.from");
        String to = null; // contract creation

        BigInteger gasLimit = BigInteger.valueOf(500_000);
        BigInteger gasPrice = BigInteger.valueOf(59_240_000);

        String data = config.getString("sendTransaction.contract.creation.data");
        BigInteger value = BigInteger.ZERO;

        Long chainId = config.getLong("chainId");

        return new Transaction(from, nonce, gasPrice, gasLimit, to, value, data, chainId, null, null);
    }

    public static Transaction buildTransactionContractCall(Config config, BigInteger nonce) {
        String from = config.getString("sendTransaction.from");
        String to = config.getString("sendTransaction.contract.call.to");

        BigInteger gasLimit = BigInteger.valueOf(100_000);
        BigInteger gasPrice = BigInteger.valueOf(59_240_000);

        String data = config.getString("sendTransaction.contract.call.data");
        BigInteger value = BigInteger.ZERO;

        Long chainId = config.getLong("chainId");

        return new Transaction(from, nonce, gasPrice, gasLimit, to, value, data, chainId, null, null);
    }

    public static Transaction buildTransactionEstimation(Config config, BigInteger nonce) {
        String from = config.getString("estimateGas.from");
        String to = config.getString("estimateGas.to");

        BigInteger gasLimit = BigInteger.valueOf(6_800_000);
        BigInteger gasPrice = BigInteger.valueOf(59_240_000);

        String data = config.getString("estimateGas.data");
        BigInteger value = BigInteger.ZERO;

        Long chainId = config.getLong("chainId");

        return new Transaction(from, nonce, gasPrice, gasLimit, to, value, data, chainId, null, null);
    }
}
