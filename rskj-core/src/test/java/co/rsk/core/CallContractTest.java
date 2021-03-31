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
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.BtcBlockStoreWithCache;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import org.ethereum.core.*;
import org.ethereum.vm.PrecompiledContracts;
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
    private static final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

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
        Transaction tx = CallTransaction.createRawTransaction(0, 0, 100000000000000L,
                receiveAddress, 0, data, config.getNetworkConstants().getChainId());
        tx.sign(new byte[]{});

        Block bestBlock = world.getBlockChain().getBestBlock();

        Repository repository = world.getRepositoryLocator()
                .startTrackingAt(world.getBlockChain().getBestBlock().getHeader());
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(
                config.getNetworkConstants().getBridgeConstants().getBtcParams());
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                btcBlockStoreFactory,
                config.getNetworkConstants().getBridgeConstants(),
                config.getActivationConfig());

        try {
            TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                    config,
                    null,
                    null,
                    blockFactory,
                    new ProgramInvokeFactoryImpl(),
                    new PrecompiledContracts(config, bridgeSupportFactory),
                    world.getBlockTxSignatureCache()
            );

            org.ethereum.core.TransactionExecutor executor = transactionExecutorFactory
                    .newInstance(tx, 0, bestBlock.getCoinbase(), repository, bestBlock, 0)
                    .setLocalCall(true);

            executor.executeTransaction();

            return executor.getResult();
        } finally {
            repository.rollback();
        }
    }
}
