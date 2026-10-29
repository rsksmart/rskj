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

package co.rsk.core.bc;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.MutableTrieImpl;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.config.Constants;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RSKIP144: a transaction that deletes a contract and another transaction that accesses a storage key
 * of that contract are connected, so they cannot be placed in different parallel sublists.
 * The keys come from real repository operations, the expected verdict comes from the spec.
 */
class DeletedAccountStorageConnectionTest {

    private static final RskAddress CONTRACT = new RskAddress("0x00000000000000000000000000000000000000c1");
    private static final RskAddress OTHER = new RskAddress("0x00000000000000000000000000000000000000d2");
    private static final DataWord SLOT = DataWord.valueOf(7);
    private static final long TX_GAS = 16_000L;
    private static final long BLOCK_GAS_LIMIT = 8_160_000L;
    private static final short PARALLEL_SUBLISTS = 2;

    // Both transactions in one sublist yields a single edge; two sublists yield two edges.
    private static final short[] sameSublistEdges = new short[]{2};
    private static final short[] separateSublistEdges = new short[]{1, 2};

    private static final Consumer<MutableRepository> DELETE = repo -> repo.delete(CONTRACT);
    private static final Consumer<MutableRepository> READ_SLOT = repo -> repo.getStorageValue(CONTRACT, SLOT);
    private static final Consumer<MutableRepository> READ_SLOT_BYTES = repo -> repo.getStorageBytes(CONTRACT, SLOT);
    private static final Consumer<MutableRepository> WRITE_SLOT = repo -> repo.addStorageRow(CONTRACT, SLOT, DataWord.valueOf(9));

    private MutableTrie trie;

    @BeforeEach
    void setup() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        trie = new MutableTrieImpl(trieStore, new Trie(trieStore));
        MutableRepository seed = new MutableRepository(trie);
        seed.createAccount(CONTRACT);
        seed.setupContract(CONTRACT);
        seed.addStorageRow(CONTRACT, SLOT, DataWord.valueOf(42));
    }

    @Test
    void storageReadOfDeletedAccountCollidesWithTheDeletion() throws Exception {
        assertTrue(collisionBetween(DELETE, READ_SLOT));
    }

    @Test
    void storageBytesReadOfDeletedAccountCollidesWithTheDeletion() throws Exception {
        assertTrue(collisionBetween(DELETE, READ_SLOT_BYTES));
    }

    @Test
    void storageWriteOfDeletedAccountCollidesWithTheDeletion() throws Exception {
        assertTrue(collisionBetween(DELETE, WRITE_SLOT));
    }

    @Test
    void storageReadOfDeletedAccountSharesASublistWithTheDeletion() {
        assertArrayEquals(sameSublistEdges, edgesFor(DELETE, READ_SLOT));
    }

    @Test
    void storageWriteOfDeletedAccountSharesASublistWithTheDeletion() {
        assertArrayEquals(sameSublistEdges, edgesFor(DELETE, WRITE_SLOT));
    }

    @Test
    void accountRootReadCollidesWithTheDeletion() throws Exception {
        assertTrue(collisionBetween(DELETE, repo -> repo.isExist(CONTRACT)));
    }

    @Test
    void storageReadOfAnotherAccountDoesNotCollideWithTheDeletion() throws Exception {
        assertFalse(collisionBetween(DELETE, repo -> repo.getStorageValue(OTHER, SLOT)));
    }

    @Test
    void storageReadOfAnotherAccountGetsItsOwnSublist() {
        assertArrayEquals(separateSublistEdges, edgesFor(DELETE, repo -> repo.getStorageValue(OTHER, SLOT)));
    }

    @Test
    void storageReadIsNotConnectedToABalanceChangeOfTheSameAccount() throws Exception {
        Consumer<MutableRepository> addBalance = repo -> repo.addBalance(CONTRACT, Coin.valueOf(1));

        assertFalse(collisionBetween(addBalance, READ_SLOT));
        assertArrayEquals(separateSublistEdges, edgesFor(addBalance, READ_SLOT));
    }

    private boolean collisionBetween(Consumer<MutableRepository> first, Consumer<MutableRepository> second) throws Exception {
        ReadWrittenKeysTracker tracker = new ReadWrittenKeysTracker();
        MutableRepository repo = new MutableRepository(trie, tracker);
        onFreshThread(() -> first.accept(repo));
        onFreshThread(() -> second.accept(repo));
        return tracker.detectCollision();
    }

    private short[] edgesFor(Consumer<MutableRepository> first, Consumer<MutableRepository> second) {
        Block block = mock(Block.class);
        when(block.getGasLimit()).thenReturn(BigInteger.valueOf(BLOCK_GAS_LIMIT).toByteArray());
        long minSequential = Constants.regtest().getMinSequentialSetGasLimit();
        ParallelizeTransactionHandler handler = ParallelizeTransactionHandler.create(PARALLEL_SUBLISTS, block, minSequential);

        addToHandler(handler, "senderA", first);
        addToHandler(handler, "senderB", second);
        return handler.getTransactionsPerSublistInOrder();
    }

    private void addToHandler(ParallelizeTransactionHandler handler, String sender, Consumer<MutableRepository> op) {
        ReadWrittenKeysTracker tracker = new ReadWrittenKeysTracker();
        op.accept(new MutableRepository(trie, tracker));
        Set<ByteArrayWrapper> reads = tracker.getThisThreadReadKeys();
        Set<ByteArrayWrapper> writes = tracker.getThisThreadWrittenKeys();

        // A non-zero gas keeps the handler from placing both transactions in the first sublist on a tie.
        Transaction tx = new TransactionBuilder().nonce(1).sender(new AccountBuilder().name(sender).build())
                .value(BigInteger.ONE).gasLimit(BigInteger.valueOf(TX_GAS)).build();
        assertTrue(handler.addTransaction(tx, reads, writes, TX_GAS).isPresent());
    }

    private static void onFreshThread(Runnable r) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(r).get();
        } finally {
            executor.shutdown();
        }
    }
}
