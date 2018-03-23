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

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import org.ethereum.core.*;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

/**
 * Created by ajlopez on 07/05/2017.
 */
public class CallContractTest {

    private static final TestSystemProperties config = new TestSystemProperties();

    @Test
    public void callContractReturningOne() {
        World world = new World();
        byte[] code = new byte[] { 0x60, 0x01, 0x60, 0x00, 0x52, 0x60, 0x20, 0x60, 0x00, (byte)0xf3 };
        Account account = new AccountBuilder(world).name("acc1").code(code).build();

        ProgramResult result = callContract(world, account.getAddress(), new byte[0]);

        Assert.assertNotNull(result);

        byte[] value = result.getHReturn();

        Assert.assertNotNull(value);
        Assert.assertEquals(32, value.length);
        Assert.assertEquals(BigInteger.ONE, new BigInteger(1, value));
    }

    private static ProgramResult callContract(World world, RskAddress receiveAddress, byte[] data) {
        Transaction tx = CallTransaction.createRawTransaction(config, 0, 0, 100000000000000L,
                receiveAddress, 0, data);
        tx.sign(new byte[32]);

        Block bestBlock = world.getBlockChain().getBestBlock();

        Repository repository = world.getRepository().startTracking();

        try {
            org.ethereum.core.TransactionExecutor executor = new org.ethereum.core.TransactionExecutor
                    (config, tx, 0, bestBlock.getCoinbase(), repository, null, null,
                            new ProgramInvokeFactoryImpl(), bestBlock)
                    .setLocalCall(true);

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
