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
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.ethereum.core.*;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

import org.junit.jupiter.api.Tag;

/**
 * Created by ajlopez on 07/05/2017.
 */
class CallContractFuzzTest {

    private static final TestSystemProperties config = new TestSystemProperties();
    private static final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

    @Tag("CallContractFuzzContractReturningOne")
    @FuzzTest
    void callContractReturningOne(FuzzedDataProvider data) {
        World world = new World();
        byte[] code = data.consumeBytes(2_000_000); //This size is unreal, but let's give some advantage to the fuzzer, because creating loops is hard
        Account account = new AccountBuilder(world).name("acc1").code(code).build();

        long executionTime = callContract(world, account.getAddress(), new byte[0]);
        if (executionTime > 100) {
            System.out.println("[COIN] CASE: It took " + executionTime + "ms with contract " + Hex.toHexString(code));
        }
    }

    private static long callContract(World world, RskAddress receiveAddress, byte[] data) {
        //Lets use 2_000_000 gas just for reference
        Transaction tx = CallTransaction.createRawTransaction(0, 0, 2_000_000,
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
                config.getActivationConfig(),
                world.getBlockTxSignatureCache());

        try {
            TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                    config,
                    null,
                    null,
                    blockFactory,
                    new ProgramInvokeFactoryImpl(),
                    new PrecompiledContracts(config, bridgeSupportFactory, world.getBlockTxSignatureCache()),
                    world.getBlockTxSignatureCache()
            );

            TransactionExecutor executor = transactionExecutorFactory
                    .newInstance(tx, 0, bestBlock.getCoinbase(), repository, bestBlock, 0)
                    .setLocalCall(true);

            long start = System.currentTimeMillis();
            executor.executeTransaction();
            long end = System.currentTimeMillis();

            return end - start;
        } finally {
            repository.rollback();
        }
    }
}
