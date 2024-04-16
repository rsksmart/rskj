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

import co.rsk.jmh.web3.plan.LocalWalletPlan;
import co.rsk.jmh.web3.plan.TransactionPlan;
import org.openjdk.jmh.annotations.*;
import org.web3j.protocol.core.methods.request.Transaction;

import java.util.concurrent.TimeUnit;

// annotated fields at class, method or field level are providing default values that can be overriden via CLI or Runner parameters
@BenchmarkMode({Mode.SingleShotTime})
@Warmup(iterations = 3)
@Measurement(iterations = 20)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Timeout(time = 10)
public class BenchmarkLocalWalletWeb3 {

    private static final int TRANSACTION_ACCOUNT_SLOTS = 16; // transaction.accountSlots = 16

    @Benchmark
    @Measurement(iterations = TRANSACTION_ACCOUNT_SLOTS)
    public void ethSendTransaction_VT(TransactionPlan plan) throws BenchmarkWeb3Exception {
        Transaction tx = plan.getTransactionsVT().next();
        plan.getWeb3Connector().ethSendTransaction(tx);
    }

    @Benchmark
    @Measurement(iterations = TRANSACTION_ACCOUNT_SLOTS)
    public void ethSendTransaction_ContractCreation(TransactionPlan plan) throws BenchmarkWeb3Exception {
        Transaction tx = plan.getTransactionsContractCreation().next();
        plan.getWeb3Connector().ethSendTransaction(tx);
    }

    @Benchmark
    @Measurement(iterations = TRANSACTION_ACCOUNT_SLOTS)
    public void ethSendTransaction_ContractCall(TransactionPlan plan) throws BenchmarkWeb3Exception {
        Transaction tx = plan.getTransactionsContractCreation().next();
        plan.getWeb3Connector().ethSendTransaction(tx);
    }

    @Benchmark
    public void ethSign(LocalWalletPlan plan) throws BenchmarkWeb3Exception {
        String address = plan.getEthSignAddress();
        String message = plan.getEthSignMessage();
        plan.getWeb3Connector().ethSign(address, message);
    }

}
