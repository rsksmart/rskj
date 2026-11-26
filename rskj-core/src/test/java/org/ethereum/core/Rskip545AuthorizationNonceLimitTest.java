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
import co.rsk.db.RepositorySnapshot;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.BlockBuilder;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.config.Constants;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.ethereum.core.Rskip545TestSupport.snapshotAtBest;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Block-level checks of authorization nonces at the top of the nonce range (2^64 - 1 is the first nonce a tuple
 * may not carry), and of a tuple whose nonce the authority already used earlier in the same block.
 *
 * <p>A skipped tuple leaves no trace in the receipt, so each test checks the authority's code and nonce.
 */
class Rskip545AuthorizationNonceLimitTest {

    private static final byte CHAIN_ID = Constants.REGTEST_CHAIN_ID;
    private static final BigInteger NONCE_LIMIT = BigInteger.ONE.shiftLeft(64);
    private static final BigInteger MAX_ACCOUNT_NONCE = NONCE_LIMIT.subtract(BigInteger.ONE);
    private static final BigInteger LAST_USABLE_TUPLE_NONCE = NONCE_LIMIT.subtract(BigInteger.TWO);
    private static final RskAddress FIRST_DELEGATE = new RskAddress("0x00000000000000000000000000000000000000d1");
    private static final RskAddress SECOND_DELEGATE = new RskAddress("0x00000000000000000000000000000000000000d2");

    @Test
    void tupleWithLastUsableNonce_applies_andASecondTupleWithTheMaxNonceIsSkipped() {
        World world = newWorld();
        Account authority = account(world, "authority", Coin.ZERO);
        seedNonce(world, authority.getAddress(), LAST_USABLE_TUPLE_NONCE);

        connect(world, sponsoredDelegation(world, 0,
                tuple(authority, FIRST_DELEGATE, LAST_USABLE_TUPLE_NONCE),
                tuple(authority, SECOND_DELEGATE, MAX_ACCOUNT_NONCE)));

        RepositorySnapshot state = snapshotAtBest(world);
        assertArrayEquals(DelegationCodeResolver.createDelegatedCode(FIRST_DELEGATE), state.getCode(authority.getAddress()),
                "the first tuple applies, the second is skipped");
        assertEquals(MAX_ACCOUNT_NONCE, state.getNonce(authority.getAddress()));
    }

    @Test
    void tupleWithTheMaxNonce_isSkipped_evenWhenItMatchesTheAccountNonce() {
        World world = newWorld();
        Account authority = account(world, "authority", Coin.ZERO);
        seedNonce(world, authority.getAddress(), MAX_ACCOUNT_NONCE);

        connect(world, sponsoredDelegation(world, 0, tuple(authority, FIRST_DELEGATE, MAX_ACCOUNT_NONCE)));

        assertNotDelegated(world, authority, MAX_ACCOUNT_NONCE);
    }

    @Test
    void tupleOneAboveTheAccountNonceNearTheTop_isSkipped() {
        World world = newWorld();
        Account authority = account(world, "authority", Coin.ZERO);
        BigInteger accountNonce = LAST_USABLE_TUPLE_NONCE.subtract(BigInteger.ONE);
        seedNonce(world, authority.getAddress(), accountNonce);

        connect(world, sponsoredDelegation(world, 0, tuple(authority, FIRST_DELEGATE, accountNonce.add(BigInteger.ONE))));

        assertNotDelegated(world, authority, accountNonce);
    }

    @Test
    void tupleOneBelowTheAccountNonceNearTheTop_isSkipped() {
        World world = newWorld();
        Account authority = account(world, "authority", Coin.ZERO);
        seedNonce(world, authority.getAddress(), LAST_USABLE_TUPLE_NONCE);

        connect(world, sponsoredDelegation(world, 0, tuple(authority, FIRST_DELEGATE, LAST_USABLE_TUPLE_NONCE.subtract(BigInteger.ONE))));

        assertNotDelegated(world, authority, LAST_USABLE_TUPLE_NONCE);
    }

    @Test
    void selfSponsoredSenderNearTheTop_reachesTheMaxNonce() {
        // The sender's nonce goes up before the tuples run, so its own tuple must carry the tx nonce + 1
        World world = newWorld();
        Account sender = account(world, "selfSponsor", Coin.valueOf(1_000_000_000L));
        BigInteger txNonce = NONCE_LIMIT.subtract(BigInteger.valueOf(3));
        seedNonce(world, sender.getAddress(), txNonce);

        connect(world, type4(sender, txNonce, tuple(sender, FIRST_DELEGATE, txNonce.add(BigInteger.ONE))));

        RepositorySnapshot state = snapshotAtBest(world);
        assertArrayEquals(DelegationCodeResolver.createDelegatedCode(FIRST_DELEGATE), state.getCode(sender.getAddress()));
        assertEquals(MAX_ACCOUNT_NONCE, state.getNonce(sender.getAddress()));
    }

    @Test
    void selfSponsoredTupleSignedWithTheTxNonce_isSkipped() {
        World world = newWorld();
        Account sender = account(world, "selfSponsor", Coin.valueOf(1_000_000_000L));
        BigInteger txNonce = NONCE_LIMIT.subtract(BigInteger.valueOf(3));
        seedNonce(world, sender.getAddress(), txNonce);

        connect(world, type4(sender, txNonce, tuple(sender, FIRST_DELEGATE, txNonce)));

        assertNotDelegated(world, sender, txNonce.add(BigInteger.ONE));
    }

    @Test
    void tupleSignedWithANonceTheAuthorityUsedEarlierInTheSameBlock_isSkipped() {
        World world = newWorld();
        Account authority = account(world, "authority", Coin.valueOf(1_000_000_000L));

        Transaction authorityTx = transfer(authority, 0);
        Transaction sponsorTx = sponsoredDelegation(world, 0, tuple(authority, FIRST_DELEGATE, BigInteger.ZERO));
        Block block = connect(world, authorityTx, sponsorTx);

        assertEquals(List.of(authorityTx.getHash(), sponsorTx.getHash()),
                block.getTransactionsList().stream().map(Transaction::getHash).toList(),
                "the authority's own tx must run first");
        assertNotDelegated(world, authority, BigInteger.ONE);
    }

    @Test
    void tupleSignedWithTheNextNonceAfterTheAuthorityTxInTheSameBlock_applies() {
        World world = newWorld();
        Account authority = account(world, "authority", Coin.valueOf(1_000_000_000L));

        Transaction authorityTx = transfer(authority, 0);
        Transaction sponsorTx = sponsoredDelegation(world, 0, tuple(authority, FIRST_DELEGATE, BigInteger.ONE));
        Block block = connect(world, authorityTx, sponsorTx);

        assertEquals(List.of(authorityTx.getHash(), sponsorTx.getHash()),
                block.getTransactionsList().stream().map(Transaction::getHash).toList());
        RepositorySnapshot state = snapshotAtBest(world);
        assertArrayEquals(DelegationCodeResolver.createDelegatedCode(FIRST_DELEGATE), state.getCode(authority.getAddress()));
        assertEquals(BigInteger.TWO, state.getNonce(authority.getAddress()));
    }

    private static void assertNotDelegated(World world, Account authority, BigInteger expectedNonce) {
        RepositorySnapshot state = snapshotAtBest(world);
        byte[] code = state.getCode(authority.getAddress());
        assertTrue(code == null || code.length == 0, "the tuple must be skipped");
        assertFalse(state.hasDelegationAuthority(authority.getAddress()));
        assertEquals(expectedNonce, state.getNonce(authority.getAddress()));
    }

    private static SetCodeAuthorization tuple(Account authority, RskAddress delegate, BigInteger nonce) {
        return Rskip545TestSupport.createSignedAuthorization(authority.getEcKey(), delegate, nonce, CHAIN_ID);
    }

    private static Transaction sponsoredDelegation(World world, long sponsorNonce, SetCodeAuthorization... tuples) {
        return type4(world.getAccountByName("sponsor"), BigInteger.valueOf(sponsorNonce), tuples);
    }

    private static Transaction type4(Account sender, BigInteger nonce, SetCodeAuthorization... tuples) {
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .nonce(nonce)
                .maxPriorityFeePerGas(Coin.valueOf(1))
                .maxFeePerGas(Coin.valueOf(1))
                .gasLimit(BigInteger.valueOf(200_000))
                .receiveAddress(sender.getAddress())
                .value(Coin.ZERO)
                .chainId(CHAIN_ID)
                .accessList(Rskip545TestSupport.EMPTY_ACCESS_LIST)
                .authorizationList(List.of(tuples))
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private static Transaction transfer(Account sender, long nonce) {
        Transaction tx = Transaction.builder()
                .nonce(BigInteger.valueOf(nonce))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21_000))
                .receiveAddress(new RskAddress("0x00000000000000000000000000000000000000aa"))
                .value(Coin.valueOf(1))
                .chainId(CHAIN_ID)
                .build();
        tx.sign(sender.getEcKey().getPrivKeyBytes());
        return tx;
    }

    private static Block connect(World world, Transaction... txs) {
        Block block = new BlockBuilder(world.getBlockChain(), world.getBridgeSupportFactory(), world.getBlockStore())
                .trieStore(world.getTrieStore())
                .parent(world.getBlockChain().getBestBlock())
                .transactions(List.of(txs))
                .build(world.getConfig());
        assertEquals(txs.length, block.getTransactionsList().size(), "the miner must keep every tx");
        assertEquals(ImportResult.IMPORTED_BEST, world.getBlockChain().tryToConnect(block));
        return block;
    }

    private static Account account(World world, String name, Coin balance) {
        Account account = new AccountBuilder(world).name(name).balance(balance).build();
        world.saveAccount(name, account);
        return account;
    }

    /**
     * Writes a nonce into the best block's state, the way {@link AccountBuilder} writes a balance. Nonces this high
     * can only be reached by seeding state.
     */
    private static void seedNonce(World world, RskAddress address, BigInteger nonce) {
        Block best = world.getBlockChain().getBestBlock();
        BlockDifficulty totalDifficulty = world.getBlockChain().getStatus().getTotalDifficulty();
        RepositorySnapshot repository = world.getRepositoryLocator().snapshotAt(best.getHeader());
        Repository track = repository.startTracking();
        track.setNonce(address, nonce);
        track.commit();
        track.save();
        best.setStateRoot(repository.getRoot());
        best.flushRLP();
        world.getBlockStore().saveBlock(best, totalDifficulty, true);
        assertEquals(nonce, snapshotAtBest(world).getNonce(address));
    }

    private static World newWorld() {
        TestSystemProperties config = new TestSystemProperties(rawConfig -> rawConfig
                .withValue("blockchain.config.consensusRules.rskip543", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip544", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip545", ConfigValueFactory.fromAnyRef(0))
                .withValue("blockchain.config.consensusRules.rskip546", ConfigValueFactory.fromAnyRef(0)));
        World world = new World(config);
        world.saveAccount("sponsor", new AccountBuilder(world).name("sponsor").balance(Coin.valueOf(1_000_000_000_000L)).build());
        return world;
    }
}
