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

import org.web3j.protocol.core.methods.request.Transaction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class TransactionFactory {

    private TransactionFactory() {

    }

    public enum TransactionType {
        VT, CONTRACT_CREATION, CONTRACT_CALL
    }

    public static List<Transaction> createTransactions(TransactionType transactionType, Properties properties, long startingNonce, long numOfTransactions) {
        List<Transaction> transactionList = new ArrayList<>();

        for (int i = 0; i < numOfTransactions; i++) {
            BigInteger newNonce = BigInteger.valueOf(startingNonce + i);

            switch (transactionType) {
                case VT:
                    transactionList.add(buildTransactionVT(properties, newNonce));
                    break;
                case CONTRACT_CALL:
                    transactionList.add(buildTransactionContractCall(properties, newNonce));
                    break;
                case CONTRACT_CREATION:
                    transactionList.add(buildTransactionContractCreation(properties, newNonce));
                    break;
            }
        }

        return transactionList;
    }

    public static Transaction buildTransactionVT(Properties properties, BigInteger nonce) {
        String from = properties.getProperty("sendTransaction.from");
        String to = properties.getProperty("sendTransaction.to");

        BigInteger gasLimit = BigInteger.valueOf(21_000);
        BigInteger gasPrice = BigInteger.valueOf(59_240_000);

        BigInteger value = BigInteger.valueOf(70_000);
        String data = null;

        Long chainId = Long.valueOf(properties.getProperty("chainId"));

        return new Transaction(from, nonce, gasPrice, gasLimit, to, value, data, chainId, null, null);
    }

    public static Transaction buildTransactionContractCreation(Properties properties, BigInteger nonce) {
        String from = properties.getProperty("sendTransaction.from");
        String to = null; // contract creation

        BigInteger gasLimit = BigInteger.valueOf(500_000);
        BigInteger gasPrice = BigInteger.valueOf(59_240_000);

        String data = properties.getProperty("sendTransaction.contract.creation.data");
        BigInteger value = BigInteger.ZERO;

        Long chainId = Long.valueOf(properties.getProperty("chainId"));

        return new Transaction(from, nonce, gasPrice, gasLimit, to, value, data, chainId, null, null);
    }

    public static Transaction buildTransactionContractCall(Properties properties, BigInteger nonce) {
        String from = properties.getProperty("sendTransaction.from");
        String to = properties.getProperty("sendTransaction.contract.call.to");

        BigInteger gasLimit = BigInteger.valueOf(100_000);
        BigInteger gasPrice = BigInteger.valueOf(59_240_000);

        String data = properties.getProperty("sendTransaction.contract.call.data");
        BigInteger value = BigInteger.ZERO;

        Long chainId = Long.valueOf(properties.getProperty("chainId"));

        return new Transaction(from, nonce, gasPrice, gasLimit, to, value, data, chainId, null, null);
    }

    public static Transaction buildTransactionEstimation(Properties properties, BigInteger nonce) {
        String from = properties.getProperty("estimateGas.from");
        String to = properties.getProperty("estimateGas.to");

        BigInteger gasLimit = BigInteger.valueOf(6_800_000);
        BigInteger gasPrice = BigInteger.valueOf(59_240_000);

        String data = properties.getProperty("estimateGas.data");
        BigInteger value = BigInteger.ZERO;

        Long chainId = Long.valueOf(properties.getProperty("chainId"));

        return new Transaction(from, nonce, gasPrice, gasLimit, to, value, data, chainId, null, null);
    }
}
