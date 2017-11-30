/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.core;

import co.rsk.util.RskTransactionExecutor;
import co.rsk.util.TestContract;
import org.ethereum.core.Transaction;
import org.ethereum.util.ContractRunner;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

import static org.mockito.Mockito.*;

public class TransactionExecutorTest {
    @Test
    public void creationTransactionTouchesNewContractAddress() {
        TouchedAccountsTracker touchedAccounts = spy(TouchedAccountsTracker.class);
        RskTestFactory factory = new RskTestFactory();
        RskTransactionExecutor helper = new RskTransactionExecutor(factory);
        ContractRunner runner = new ContractRunner(factory);
        Transaction creationTx = runner.creationTransaction(TestContract.hello());
        helper.executeTransaction(touchedAccounts, creationTx);

        DataWord contractAddress = new DataWord(creationTx.getContractAddress());
        verify(touchedAccounts, times(1))
                .add(contractAddress);
        // TODO(mc): EIP-161 specifies that the source of a CREATE operation should be marked as touched,
        // but EthereumJ doesn't do that.
//        DataWord senderAddress = new DataWord(runner.sender.getAddress());
//        verify(touchedAccounts, times(1))
//                .add(senderAddress);
    }

    @Test
    public void callingFromParentToChildContractTouchesBoth() {
        RskTestFactory factory = new RskTestFactory();
        RskTransactionExecutor helper = new RskTransactionExecutor(factory);
        ContractRunner runner = new ContractRunner(factory);
        Transaction creationTx = runner.creationTransaction(TestContract.parent());
        helper.executeTransaction(new TouchedAccountsTracker(), creationTx);

        TouchedAccountsTracker touchedAccounts = spy(TouchedAccountsTracker.class);
        Transaction callTx = runner.callTransaction(TestContract.parent(), creationTx.getContractAddress(), "createChild", BigInteger.TEN);
        helper.executeTransaction(touchedAccounts, callTx);

        Assert.assertArrayEquals(creationTx.getContractAddress(), callTx.getReceiveAddress());

        DataWord parentContractAddress = new DataWord(callTx.getReceiveAddress());
        verify(touchedAccounts, times(1))
                .add(parentContractAddress);
        // childContractAddress is hardcoded because it's hard to get it programmatically from the parent contract creation
        DataWord childContractAddress = new DataWord("0000000000000000000000005a1356b9ccb20af5cdc4afde2f91329269818ec4");
        verify(touchedAccounts, times(1))
                .add(childContractAddress);
        // TODO(mc): EIP-161 specifies that the source of a CALL operation should be marked as touched,
        // but EthereumJ doesn't do that.
//        DataWord senderAddress = new DataWord(runner.sender.getAddress());
//        verify(touchedAccounts, times(1))
//                .add(senderAddress);
    }
}