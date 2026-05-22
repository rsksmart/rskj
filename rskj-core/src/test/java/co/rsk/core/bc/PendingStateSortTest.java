/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
import co.rsk.crypto.Keccak256;
import org.ethereum.TestUtils;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;

class PendingStateSortTest {

    private final SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

    @Test
    void sortWithNonCanonicalNonce_discardsAndReturnsSortedValidTxs() {
        byte[] addressBytes = TestUtils.generateBytes("addr", 20);
        RskAddress sender = new RskAddress(addressBytes);

        Transaction validTx1 = mockTransaction(sender, new byte[]{0x01}, Coin.valueOf(10), hashFromByte((byte) 1));
        Transaction validTx2 = mockTransaction(sender, new byte[]{0x02}, Coin.valueOf(5), hashFromByte((byte) 2));
        Transaction nonCanonicalTx = mockTransaction(sender, new byte[9], Coin.valueOf(20), hashFromByte((byte) 3));

        List<Transaction> txs = new LinkedList<>();
        txs.add(validTx1);
        txs.add(nonCanonicalTx);
        txs.add(validTx2);

        List<Transaction> discardedTxs = new ArrayList<>();
        List<Transaction> result = PendingState.sortByPriceTakingIntoAccountSenderAndNonce(txs, signatureCache, discardedTxs);

        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(validTx1, result.get(0));
        Assertions.assertEquals(validTx2, result.get(1));

        Assertions.assertEquals(1, discardedTxs.size());
        Assertions.assertEquals(nonCanonicalTx, discardedTxs.get(0));
    }

    @Test
    void sortWithAllValidTxs_returnsAllSorted() {
        byte[] addressBytes = TestUtils.generateBytes("addr", 20);
        RskAddress sender = new RskAddress(addressBytes);

        Transaction tx1 = mockTransaction(sender, new byte[]{0x02}, Coin.valueOf(5), hashFromByte((byte) 1));
        Transaction tx2 = mockTransaction(sender, new byte[]{0x01}, Coin.valueOf(10), hashFromByte((byte) 2));

        List<Transaction> txs = new LinkedList<>();
        txs.add(tx1);
        txs.add(tx2);

        List<Transaction> discardedTxs = new ArrayList<>();
        List<Transaction> result = PendingState.sortByPriceTakingIntoAccountSenderAndNonce(txs, signatureCache, discardedTxs);

        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(tx2, result.get(0));
        Assertions.assertEquals(tx1, result.get(1));
        Assertions.assertTrue(discardedTxs.isEmpty());
    }

    @Test
    void sortWithEmptyList_returnsEmpty() {
        List<Transaction> txs = new LinkedList<>();

        List<Transaction> discardedTxs = new ArrayList<>();
        List<Transaction> result = PendingState.sortByPriceTakingIntoAccountSenderAndNonce(txs, signatureCache, discardedTxs);

        Assertions.assertTrue(result.isEmpty());
        Assertions.assertTrue(discardedTxs.isEmpty());
    }

    @Test
    void sortWithNullDiscardedList_doesNotThrow() {
        byte[] addressBytes = TestUtils.generateBytes("addr", 20);
        RskAddress sender = new RskAddress(addressBytes);

        Transaction validTx = mockTransaction(sender, new byte[]{0x01}, Coin.valueOf(10), hashFromByte((byte) 1));
        Transaction nonCanonicalTx = mockTransaction(sender, new byte[9], Coin.valueOf(20), hashFromByte((byte) 2));

        List<Transaction> txs = new LinkedList<>();
        txs.add(validTx);
        txs.add(nonCanonicalTx);

        List<Transaction> result = PendingState.sortByPriceTakingIntoAccountSenderAndNonce(txs, signatureCache, null);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(validTx, result.get(0));
    }

    private Transaction mockTransaction(RskAddress sender, byte[] nonce, Coin gasPrice, Keccak256 hash) {
        Transaction tx = Mockito.mock(Transaction.class);
        Mockito.when(tx.getSender(any(SignatureCache.class))).thenReturn(sender);
        Mockito.when(tx.getNonce()).thenReturn(nonce);
        Mockito.when(tx.getGasPrice()).thenReturn(gasPrice);
        Mockito.when(tx.getHash()).thenReturn(hash);
        return tx;
    }

    private Keccak256 hashFromByte(byte b) {
        byte[] hash = new byte[32];
        hash[0] = b;
        return new Keccak256(hash);
    }
}
