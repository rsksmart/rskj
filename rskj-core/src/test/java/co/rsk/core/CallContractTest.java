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

import co.rsk.test.builders.AccountBuilder;
import org.ethereum.core.*;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.program.ProgramResult;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

/**
 * Created by ajlopez on 07/05/2017.
 */
public class CallContractTest {

    private final RskTestFactory objects = new RskTestFactory();

    @Test
    public void callContractReturningOne() {
        byte[] code = new byte[] { 0x60, 0x01, 0x60, 0x00, 0x52, 0x60, 0x20, 0x60, 0x00, (byte)0xf3 };
        Account account = new AccountBuilder(objects.getBlockchain()).name("acc1").code(code).build();

        ProgramResult result = callContract(account.getAddress(), new byte[0]);

        Assert.assertNotNull(result);

        byte[] value = result.getHReturn();

        Assert.assertNotNull(value);
        Assert.assertEquals(32, value.length);
        Assert.assertEquals(BigInteger.ONE, new BigInteger(1, value));
    }

    private ProgramResult callContract(RskAddress receiveAddress, byte[] data) {
        Transaction tx = CallTransaction.createRawTransaction(objects.getRskSystemProperties(), 0, 0, 50000L,
                receiveAddress, 0, data);
        tx.sign(new byte[32]);

        Block bestBlock = objects.getBlockchain().getBestBlock();

        Repository repository = objects.getRepository().startTracking();

        try {
            org.ethereum.core.TransactionExecutor executor = objects.getTransactionExecutorFactory()
                    .newInstance(tx, 0,  bestBlock.getCoinbase(), repository, bestBlock, 0);

            executor.init();
            executor.execute();
            executor.go();
            executor.finalization();

            return executor.getResult();
        } finally {
            repository.rollback();
        }
    }
}
