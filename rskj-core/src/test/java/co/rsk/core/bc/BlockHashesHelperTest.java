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

package co.rsk.core.bc;

import co.rsk.core.Coin;
import co.rsk.crypto.Keccak256;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.trie.Trie;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.core.TransactionReceipt;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;


public class BlockHashesHelperTest {

    @Test
    public void calculateReceiptsTrieRootForOK() {
        World world = new World();

        // Creation of transactions
        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx1 = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        Account acc3 = new AccountBuilder(world).name("acc3").balance(Coin.valueOf(2000000)).build();
        Account acc4 = new AccountBuilder().name("acc4").build();
        Transaction tx2 = new TransactionBuilder().sender(acc3).receiver(acc4).value(BigInteger.valueOf(500000)).build();
        Account acc5 = new AccountBuilder(world).name("acc5").balance(Coin.valueOf(2000000)).build();
        Account acc6 = new AccountBuilder().name("acc6").build();
        Transaction tx3 = new TransactionBuilder().sender(acc5).receiver(acc6).value(BigInteger.valueOf(800000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx1);
        txs.add(tx2);
        txs.add(tx3);

        Block block = mock(Block.class);
        when(block.getTransactionsList()).thenReturn(txs);
        when(block.getHash()).thenReturn(new Keccak256(Hex.decode("0246c165ac839255aab76c1bc3df7842673ee3673e20dd908bba60862cf41326")));
        ReceiptStore receiptStore = mock(ReceiptStore.class);

        byte[] rskBlockHash = new byte[]{0x2};

        when(receiptStore.get(tx1.getHash().getBytes(), block.getHash().getBytes())).thenReturn(Optional.of(new TransactionInfo(new TransactionReceipt(),
                rskBlockHash, 0)));
        when(receiptStore.get(tx2.getHash().getBytes(), block.getHash().getBytes())).thenReturn(Optional.of(new TransactionInfo(new TransactionReceipt(),
                rskBlockHash, 0)));
        when(receiptStore.get(tx3.getHash().getBytes(), block.getHash().getBytes())).thenReturn(Optional.of(new TransactionInfo(new TransactionReceipt(),
                rskBlockHash, 0)));

        List<Trie> trie = BlockHashesHelper.calculateReceiptsTrieRootFor(block, receiptStore, tx1.getHash());

        assertNotNull(trie);
        assertEquals(trie.size(), 2);

    }


    @Test
    public void calculateReceiptsTrieRootForException() {
        World world = new World();

        // Creation of transactions
        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx);

        Block block = mock(Block.class);
        when(block.getTransactionsList()).thenReturn(txs);
        when(block.getHash()).thenReturn(new Keccak256(Hex.decode("0246c165ac839255aab76c1bc3df7842673ee3673e20dd908bba60862cf41326")));
        ReceiptStore receiptStore = mock(ReceiptStore.class);

        Keccak256 hashTx = tx.getHash();

        Assertions.assertThrows(BlockHashesHelperException.class, () -> BlockHashesHelper.calculateReceiptsTrieRootFor(block, receiptStore, hashTx));
    }

    @Test
    public void calculateReceiptsTrieRootForDifferentTxHash() {
        World world = new World();

        // Creation of transactions
        Account acc1 = new AccountBuilder(world).name("acc1").balance(Coin.valueOf(2000000)).build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx1 = new TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        Account acc3 = new AccountBuilder(world).name("acc3").balance(Coin.valueOf(2000000)).build();
        Account acc4 = new AccountBuilder().name("acc4").build();
        Transaction tx2 = new TransactionBuilder().sender(acc3).receiver(acc4).value(BigInteger.valueOf(500000)).build();
        Account acc5 = new AccountBuilder(world).name("acc5").balance(Coin.valueOf(2000000)).build();
        Account acc6 = new AccountBuilder().name("acc6").build();
        Transaction tx3 = new TransactionBuilder().sender(acc5).receiver(acc6).value(BigInteger.valueOf(800000)).build();
        List<Transaction> txs = new ArrayList<>();
        txs.add(tx1);
        txs.add(tx2);

        Block block = mock(Block.class);
        when(block.getTransactionsList()).thenReturn(txs);
        when(block.getHash()).thenReturn(new Keccak256(Hex.decode("0246c165ac839255aab76c1bc3df7842673ee3673e20dd908bba60862cf41326")));
        ReceiptStore receiptStore = mock(ReceiptStore.class);

        byte[] rskBlockHash = new byte[]{0x2};

        when(receiptStore.get(tx1.getHash().getBytes(), block.getHash().getBytes())).thenReturn(Optional.of(new TransactionInfo(new TransactionReceipt(),
                rskBlockHash, 0)));
        when(receiptStore.get(tx2.getHash().getBytes(), block.getHash().getBytes())).thenReturn(Optional.of(new TransactionInfo(new TransactionReceipt(),
                rskBlockHash, 0)));
        when(receiptStore.get(tx3.getHash().getBytes(), block.getHash().getBytes())).thenReturn(Optional.of(new TransactionInfo(new TransactionReceipt(),
                rskBlockHash, 0)));

        //Tx3 is not part of the transaction list of the block
        List<Trie> trie = BlockHashesHelper.calculateReceiptsTrieRootFor(block, receiptStore, tx3.getHash());

        assertNull(trie);
    }
}
