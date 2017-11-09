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

package org.ethereum.vm;

import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.util.TestContract;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ContractDetails;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

public class ProgramTest {
    @Test
    public void childContractDoesntInheritMsgValue() {
        RskTestFactory factory = new RskTestFactory();

        TestContract parent = TestContract.parent();
        TestContract child = TestContract.child();
        ContractDetails parentContractDetails = factory.addContract(parent.data);
        factory.addContract(child.data);

        ProgramInvokeFactory programInvokeFactory = new ProgramInvokeFactoryImpl();
        Account sender = new AccountBuilder(factory.getBlockchain())
                .name("sender")
                .balance(BigInteger.valueOf(1000000))
                .build();
        Transaction transaction = new TransactionBuilder()
                .gasLimit(BigInteger.valueOf(100000))
                .sender(sender)
                .receiverAddress(parentContractDetails.getAddress())
                .data(parent.functions.get("createChild").encode())
                .value(BigInteger.TEN)
                .build();
        Block block = factory.getBlockchain().getBestBlock();
        Repository repository = factory.getRepository();
        BlockStore blockStore = factory.getBlockStore();

        byte[] bytes = Hex.decode(parent.data);

        ProgramInvoke programInvoke =
                programInvokeFactory.createProgramInvoke(transaction, block, repository, blockStore);

        VM vm = new VM();
        Program program = new Program(bytes, programInvoke, transaction);

        vm.play(program);
        Assert.assertNull(program.getResult().getException());
    }
}

