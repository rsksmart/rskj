/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.ethereum.core.Rskip545TestSupport.initCodeReturning;
import static org.ethereum.core.Rskip545TestSupport.snapshotAtBest;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Block-level checks of the account state a delegation leaves behind, with the real VM and repository.
 */
class Rskip545DelegationStateEndToEndTest {

    private static final byte CHAIN_ID = Constants.REGTEST_CHAIN_ID;
    // slot0 += 1
    private static final byte[] COUNTER_RUNTIME = Hex.decode("60005460010160005500");

    @Test
    void type4Delegation_isVisibleToExtcodehash_andStoresTheAuthorityFlag() {
        World world = newWorld(0);
        Account sponsor = world.getAccountByName("sponsor");
        Account authority = new AccountBuilder(world).name("authority").build();

        RskAddress counter = deploy(world, sponsor, 0, COUNTER_RUNTIME);
        RskAddress probe = deploy(world, sponsor, 1, extcodehashProbeRuntime(authority.getAddress()));

        Transaction delegation = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .nonce(BigInteger.valueOf(2))
                .maxPriorityFeePerGas(Coin.valueOf(1))
                .maxFeePerGas(Coin.valueOf(1))
                .gasLimit(BigInteger.valueOf(200_000))
                .receiveAddress(sponsor.getAddress())
                .value(Coin.ZERO)
                .chainId(CHAIN_ID)
                .accessList(Rskip545TestSupport.EMPTY_ACCESS_LIST)
                .authorizationList(List.of(Rskip545TestSupport.createSignedAuthorization(
                        authority.getEcKey(), counter, BigInteger.valueOf(0), CHAIN_ID)))
                .build();
        delegation.sign(sponsor.getEcKey().getPrivKeyBytes());
        connect(world, delegation);

        connect(world, legacyCall(sponsor, 3, probe));

        byte[] designator = DelegationCodeResolver.createDelegatedCode(counter);
        RepositorySnapshot state = snapshotAtBest(world);

        assertArrayEquals(designator, state.getCode(authority.getAddress()));
        assertArrayEquals(HashUtil.keccak256(designator), state.getStorageValue(probe, DataWord.ZERO).getData(),
                "EXTCODEHASH of the authority must be the hash of the delegation designator");
        assertTrue(state.hasDelegationAuthority(authority.getAddress()));
        assertTrue(state.getAccountState(authority.getAddress()).hasDelegationAuthority(),
                "the stored account state must carry the delegation authority flag");
        assertEquals(BigInteger.ONE, state.getNonce(authority.getAddress()));
    }

    /**
     * The EVM treats an account as delegated from its code bytes alone. A contract deployed while RSKIP-544 is
     * off whose runtime code is 0xef0100 || address, and which never signed an authorization, runs as a
     * delegation once RSKIP-545 is active. This test pins that behavior, so changing it must change this test.
     *
     * <p>No shipped network runs RSKIP-545 with RSKIP-544 off.
     */
    @Test
    void codeOnlyDesignatorWithoutAuthorityFlag_isExecutedAsDelegation_currentBehavior() {
        // RSKIP-544 off, as before vetiver: a contract can still be created with 0xEF-prefixed code
        World world = newWorld(-1);
        Account sponsor = world.getAccountByName("sponsor");

        RskAddress counter = deploy(world, sponsor, 0, COUNTER_RUNTIME);
        RskAddress lookalike = deploy(world, sponsor, 1, DelegationCodeResolver.createDelegatedCode(counter));

        RepositorySnapshot before = snapshotAtBest(world);
        assertArrayEquals(DelegationCodeResolver.createDelegatedCode(counter), before.getCode(lookalike));
        assertFalse(before.hasDelegationAuthority(lookalike), "the lookalike never signed an authorization");

        connect(world, legacyCall(sponsor, 2, lookalike));

        RepositorySnapshot after = snapshotAtBest(world);
        assertEquals(DataWord.ONE, after.getStorageValue(lookalike, DataWord.ZERO),
                "the counter code ran in the lookalike's storage, as for a delegation");
        assertFalse(after.hasDelegationAuthority(lookalike));
    }

    private static byte[] extcodehashProbeRuntime(RskAddress target) {
        // PUSH20 target, EXTCODEHASH, PUSH1 0, SSTORE, STOP
        return ByteUtil.merge(new byte[]{0x73}, target.getBytes(), Hex.decode("3f60005500"));
    }

    private static RskAddress deploy(World world, Account sender, long nonce, byte[] runtime) {
        Transaction create = Transaction.builder()
                .nonce(BigInteger.valueOf(nonce))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(300_000))
                .value(Coin.ZERO)
                .chainId(CHAIN_ID)
                .data(initCodeReturning(runtime))
                .build();
        create.sign(sender.getEcKey().getPrivKeyBytes());
        connect(world, create);
        RskAddress address = new RskAddress(HashUtil.calcNewAddr(sender.getAddress().getBytes(), BigInteger.valueOf(nonce).toByteArray()));
        assertArrayEquals(runtime, snapshotAtBest(world).getCode(address), "deployment must store the runtime code");
        return address;
    }

    private static Transaction legacyCall(Account sender, long nonce, RskAddress to) {
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.valueOf(nonce))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(100_000))
                .receiveAddress(to)
                .value(Coin.ZERO)
                .chainId(CHAIN_ID)
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private static void connect(World world, Transaction tx) {
        Block best = world.getBlockChain().getBestBlock();
        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(best)
                .transactions(Collections.singletonList(tx))
                .build(world.getConfig());
        assertEquals(1, block.getTransactionsList().size());
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block));
    }

    private static World newWorld(long rskip544Activation) {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip544", ConfigValueFactory.fromAnyRef(rskip544Activation)));
        World world = new World(config);
        world.saveAccount("sponsor", new AccountBuilder(world).name("sponsor").balance(Coin.valueOf(1_000_000_000_000L)).build());
        return world;
    }
}
