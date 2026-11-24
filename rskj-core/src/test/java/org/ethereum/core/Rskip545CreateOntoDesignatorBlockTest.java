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
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.types.bytes.Bytes;
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.crypto.HashUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Collections;

import static org.ethereum.core.Rskip545TestSupport.initCodeReturning;
import static org.ethereum.core.Rskip545TestSupport.snapshotAtBest;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Block-level checks, with RSKIP-545 active, of a create whose target address already holds only a delegation
 * designator. The designator is seeded into the genesis state, because no key is known for a create address.
 */
class Rskip545CreateOntoDesignatorBlockTest {

    private static final byte CHAIN_ID = Constants.REGTEST_CHAIN_ID;
    private static final long GAS_LIMIT = 300_000;
    private static final RskAddress DELEGATE = new RskAddress("0x1111111111111111111111111111111111111111");
    private static final byte[] DESIGNATOR = DelegationCodeResolver.createDelegatedCode(DELEGATE);
    private static final byte[] RUNTIME = Hex.decode("600160005500"); // slot0 = 1
    private static final byte[] INIT_CODE = initCodeReturning(RUNTIME);
    // CALLDATACOPY(0, 0, CALLDATASIZE), CREATE2(value 0, offset 0, size CALLDATASIZE, salt 0), STOP
    private static final byte[] FACTORY_RUNTIME = Hex.decode("366000600037" + "600036600060" + "00f5" + "00");

    @Test
    void create2FromAContract_ontoADesignator_keepsTheDesignatorAndUsesAllTheGas() {
        World world = newWorld();
        Account sponsor = world.getAccountByName("sponsor");
        RskAddress factory = contractAddress(sponsor, 0);
        RskAddress target = new RskAddress(HashUtil.calcSaltAddr(factory, Bytes.of(INIT_CODE), new byte[32]));
        seedDesignator(world, target);

        deploy(world, sponsor, 0, FACTORY_RUNTIME);
        Coin before = snapshotAtBest(world).getBalance(sponsor.getAddress());
        connect(world, call(sponsor, 1, factory, INIT_CODE));

        RepositorySnapshot state = snapshotAtBest(world);
        assertArrayEquals(DESIGNATOR, state.getCode(target), "the designator must stay");
        assertTrue(state.hasDelegationAuthority(target));
        assertEquals(BigInteger.ZERO, state.getNonce(target));
        assertEquals(Coin.valueOf(GAS_LIMIT), before.subtract(state.getBalance(sponsor.getAddress())),
                "the colliding CREATE2 gets all the remaining gas and keeps it (gas price is 1)");
    }

    @Test
    void create2FromAContract_ontoACleanAddress_deploysTheRuntime() {
        World world = newWorld();
        Account sponsor = world.getAccountByName("sponsor");
        RskAddress factory = contractAddress(sponsor, 0);
        RskAddress target = new RskAddress(HashUtil.calcSaltAddr(factory, Bytes.of(INIT_CODE), new byte[32]));

        deploy(world, sponsor, 0, FACTORY_RUNTIME);
        Coin before = snapshotAtBest(world).getBalance(sponsor.getAddress());
        connect(world, call(sponsor, 1, factory, INIT_CODE));

        RepositorySnapshot state = snapshotAtBest(world);
        assertArrayEquals(RUNTIME, state.getCode(target));
        assertFalse(state.hasDelegationAuthority(target));
        assertTrue(before.subtract(state.getBalance(sponsor.getAddress())).compareTo(Coin.valueOf(GAS_LIMIT)) < 0,
                "unused create gas comes back");
    }

    /**
     * A create transaction has no collision check of its own (only the CREATE and CREATE2 opcodes have one), so
     * a designator at the address of a create transaction is replaced by the new runtime. This pins today's
     * behaviour. It cannot happen on a real network: it needs a known key for keccak256(rlp(sender, nonce)).
     */
    @Test
    void createTransaction_ontoADesignator_replacesTheDesignator_currentBehavior() {
        World world = newWorld();
        Account sponsor = world.getAccountByName("sponsor");
        RskAddress target = contractAddress(sponsor, 0);
        seedDesignator(world, target);

        Transaction create = Transaction.builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(GAS_LIMIT))
                .value(Coin.ZERO)
                .chainId(CHAIN_ID)
                .data(INIT_CODE)
                .build();
        create.sign(sponsor.getEcKey().getPrivKeyBytes());
        connect(world, create);

        RepositorySnapshot state = snapshotAtBest(world);
        assertArrayEquals(RUNTIME, state.getCode(target), "the create transaction overwrote the designator");
        assertFalse(state.hasDelegationAuthority(target), "the new account state drops the authority flag");
    }

    private static void seedDesignator(World world, RskAddress target) {
        Block best = world.getBlockChain().getBestBlock();
        assertEquals(0, best.getNumber(), "seed before any block, so only the genesis state changes");
        BlockDifficulty td = world.getBlockChain().getStatus().getTotalDifficulty();
        RepositorySnapshot repository = world.getRepositoryLocator().snapshotAt(best.getHeader());
        Repository track = repository.startTracking();
        track.createAccount(target);
        track.saveCode(target, DESIGNATOR);
        // as SetCodeAuthorizationTransactionExecutor.writeDelegation does
        track.initializeStorage(target);
        track.setDelegationAuthority(target);
        track.commit();
        track.save();
        best.setStateRoot(repository.getRoot());
        best.flushRLP();
        world.getBlockStore().saveBlock(best, td, true);

        RepositorySnapshot seeded = snapshotAtBest(world);
        assertArrayEquals(DESIGNATOR, seeded.getCode(target));
        assertTrue(seeded.hasDelegationAuthority(target));
    }

    private static RskAddress contractAddress(Account sender, long nonce) {
        return new RskAddress(HashUtil.calcNewAddr(sender.getAddress().getBytes(), BigInteger.valueOf(nonce).toByteArray()));
    }

    private static void deploy(World world, Account sender, long nonce, byte[] runtime) {
        Transaction create = Transaction.builder()
                .nonce(BigInteger.valueOf(nonce))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(GAS_LIMIT))
                .value(Coin.ZERO)
                .chainId(CHAIN_ID)
                .data(initCodeReturning(runtime))
                .build();
        create.sign(sender.getEcKey().getPrivKeyBytes());
        connect(world, create);
        assertArrayEquals(runtime, snapshotAtBest(world).getCode(contractAddress(sender, nonce)));
    }

    private static Transaction call(Account sender, long nonce, RskAddress to, byte[] data) {
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.valueOf(nonce))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(GAS_LIMIT))
                .receiveAddress(to)
                .value(Coin.ZERO)
                .chainId(CHAIN_ID)
                .data(data)
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

    private static World newWorld() {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip544", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(0)));
        World world = new World(config);
        world.saveAccount("sponsor",
                new AccountBuilder(world).name("sponsor").balance(Coin.valueOf(1_000_000_000_000L)).build());
        return world;
    }
}
