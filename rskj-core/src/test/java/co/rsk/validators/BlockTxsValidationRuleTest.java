/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.validators;

import co.rsk.core.RskAddress;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockTxsValidationRuleTest {

    private BlockTxsValidationRule rule;
    private RepositorySnapshot repositorySnapshot;
    private Block parent;
    private SignatureCache signatureCache;

    @BeforeEach
    void setup() {
        parent = mock(Block.class);
        BlockHeader parentHeader = mock(BlockHeader.class);
        when(parent.getHeader()).thenReturn(parentHeader);

        RepositoryLocator repositoryLocator = mock(RepositoryLocator.class);
        repositorySnapshot = mock(RepositorySnapshot.class);
        when(repositoryLocator.snapshotAt(parentHeader)).thenReturn(repositorySnapshot);

        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        rule = new BlockTxsValidationRule(repositoryLocator, signatureCache);
    }

    @Test
    void validNonceSame() {
        RskAddress sender = TestUtils.randomAddress();
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 42));

        Assertions.assertTrue(rule.isValid(block, parent, null));
    }

    @Test
    void invalidNonceLower() {
        RskAddress sender = TestUtils.randomAddress();
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 41));

        Assertions.assertFalse(rule.isValid(block, parent, null));
    }

    @Test
    void invalidNonceHigher() {
        RskAddress sender = TestUtils.randomAddress();
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 43));

        Assertions.assertFalse(rule.isValid(block, parent, null));
    }

    @Test
    void validTransactionsWithSameNonceAndDifferentSenders() {
        RskAddress sender1 = TestUtils.randomAddress();
        initialNonce(sender1, 64);
        RskAddress sender2 = TestUtils.randomAddress();
        initialNonce(sender2, 64);

        Block block = block(transaction(sender1, 64), transaction(sender2, 64));

        Assertions.assertTrue(rule.isValid(block, parent, null));
    }

    @Test
    void validConsecutiveTransactionsFromSameSender() {
        RskAddress sender = TestUtils.randomAddress();
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 42), transaction(sender, 43));

        Assertions.assertTrue(rule.isValid(block, parent, null));
    }

    @Test
    void invalidTransactionsFromSameSenderWithSkippedNonce() {
        RskAddress sender = TestUtils.randomAddress();
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 42), transaction(sender, 44));

        Assertions.assertFalse(rule.isValid(block, parent, null));
    }

    @Test
    void invalidTransactionsWithSameNonceAndSameSender() {
        RskAddress sender = TestUtils.randomAddress();
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 42), transaction(sender, 42));

        Assertions.assertFalse(rule.isValid(block, parent, null));
    }

    private void initialNonce(RskAddress sender, int nonce) {
        when(repositorySnapshot.getNonce(sender)).thenReturn(BigInteger.valueOf(nonce));
    }

    private Block block(Transaction... transactions) {
        Block block = mock(Block.class);
        when(block.getTransactionsList()).thenReturn(Arrays.asList(transactions));
        return block;
    }

    private Transaction transaction(RskAddress sender, int nonce) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getSender(any(SignatureCache.class))).thenReturn(sender);
        when(transaction.getNonce()).thenReturn(BigIntegers.asUnsignedByteArray(BigInteger.valueOf(nonce)));
        return transaction;
    }
}
