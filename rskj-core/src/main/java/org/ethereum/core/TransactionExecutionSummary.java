/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.core;

import static java.util.Collections.*;
import static org.ethereum.util.BIUtil.toBI;

import co.rsk.core.Coin;
import java.math.BigInteger;
import java.util.*;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.program.InternalTransaction;

public class TransactionExecutionSummary {

    private Transaction tx;
    private Coin value = Coin.ZERO;
    private Coin gasPrice = Coin.ZERO;
    private BigInteger gasLimit = BigInteger.ZERO;
    private BigInteger gasUsed = BigInteger.ZERO;
    private BigInteger gasLeftover = BigInteger.ZERO;
    private BigInteger gasRefund = BigInteger.ZERO;

    private List<DataWord> deletedAccounts = emptyList();
    private List<InternalTransaction> internalTransactions = emptyList();

    private byte[] result;
    private List<LogInfo> logs;

    private boolean failed;

    public Transaction getTransaction() {
        return tx;
    }

    public byte[] getTransactionHash() {
        return getTransaction().getHash().getBytes();
    }

    private Coin calcCost(BigInteger gas) {
        return gasPrice.multiply(gas);
    }

    public Coin getFee() {
        if (failed) {
            return calcCost(gasLimit);
        }

        return calcCost(gasLimit.subtract(gasLeftover.add(gasRefund)));
    }

    public Coin getRefund() {
        if (failed) {
            return Coin.ZERO;
        }

        return calcCost(gasRefund);
    }

    public Coin getLeftover() {
        if (failed) {
            return Coin.ZERO;
        }

        return calcCost(gasLeftover);
    }

    public Coin getGasPrice() {
        return gasPrice;
    }

    public BigInteger getGasLimit() {
        return gasLimit;
    }

    public BigInteger getGasUsed() {
        return gasUsed;
    }

    public BigInteger getGasLeftover() {
        return gasLeftover;
    }

    public Coin getValue() {
        return value;
    }

    public List<DataWord> getDeletedAccounts() {
        return deletedAccounts;
    }

    public List<InternalTransaction> getInternalTransactions() {
        return internalTransactions;
    }

    public BigInteger getGasRefund() {
        return gasRefund;
    }

    public boolean isFailed() {
        return failed;
    }

    public byte[] getResult() {
        return result;
    }

    public List<LogInfo> getLogs() {
        return logs;
    }

    public static Builder builderFor(Transaction transaction) {
        return new Builder(transaction);
    }

    public static class Builder {

        private final TransactionExecutionSummary summary;

        Builder(Transaction transaction) {
            Objects.requireNonNull(
                    transaction, "Cannot build TransactionExecutionSummary for null transaction.");

            summary = new TransactionExecutionSummary();
            summary.tx = transaction;
            summary.gasLimit = toBI(transaction.getGasLimit());
            summary.gasPrice = transaction.getGasPrice();
            summary.value = transaction.getValue();
        }

        public Builder gasUsed(BigInteger gasUsed) {
            summary.gasUsed = gasUsed;
            return this;
        }

        public Builder gasLeftover(BigInteger gasLeftover) {
            summary.gasLeftover = gasLeftover;
            return this;
        }

        public Builder gasRefund(BigInteger gasRefund) {
            summary.gasRefund = gasRefund;
            return this;
        }

        public Builder internalTransactions(List<InternalTransaction> internalTransactions) {
            summary.internalTransactions = unmodifiableList(internalTransactions);
            return this;
        }

        public Builder deletedAccounts(Set<DataWord> deletedAccounts) {
            summary.deletedAccounts = new ArrayList<>();
            for (DataWord account : deletedAccounts) {
                summary.deletedAccounts.add(account);
            }
            return this;
        }

        public Builder markAsFailed() {
            summary.failed = true;
            return this;
        }

        public Builder logs(List<LogInfo> logs) {
            summary.logs = logs;
            return this;
        }

        public Builder result(byte[] result) {
            summary.result = result;
            return this;
        }

        public TransactionExecutionSummary build() {
            if (summary.failed) {
                for (InternalTransaction transaction : summary.internalTransactions) {
                    transaction.reject();
                }
            }
            return summary;
        }
    }
}
