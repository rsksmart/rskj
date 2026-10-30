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
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.ethereum.TestUtils;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.util.EthModuleTestUtils;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

import org.junit.jupiter.api.Tag;

/**
 * Created by ajlopez on 07/05/2017.
 */
class CallContractFuzzTest {

    private static final TestSystemProperties config = new TestSystemProperties();

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
        Block bestBlock = world.getBlockChain().getBestBlock();
        ReversibleTransactionExecutor executor = EthModuleTestUtils.buildReversibleTransactionExecutor(world, config);

        long start = System.currentTimeMillis();
        //Lets use 2_000_000 gas just for reference
        executor.executeTransaction(
                bestBlock,
                bestBlock.getCoinbase(),
                BigInteger.ZERO.toByteArray(),
                BigInteger.valueOf(2_000_000).toByteArray(),
                receiveAddress.getBytes(),
                BigInteger.ZERO.toByteArray(),
                data,
                TestUtils.generateAddress("caller"));
        long end = System.currentTimeMillis();

        return end - start;
    }
}
