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
import co.rsk.core.SenderResolverVisitor;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlockTxsValidationRuleTest {
    private BlockTxsValidationRule rule;
    private RepositorySnapshot repositorySnapshot;
    private Block parent;

    @Before
    public void setup() {
        parent = mock(Block.class);
        BlockHeader parentHeader = mock(BlockHeader.class);
        when(parent.getHeader()).thenReturn(parentHeader);

        RepositoryLocator repositoryLocator = mock(RepositoryLocator.class);
        repositorySnapshot = mock(RepositorySnapshot.class);
        when(repositoryLocator.snapshotAt(parentHeader)).thenReturn(repositorySnapshot);

        rule = new BlockTxsValidationRule(repositoryLocator, new SenderResolverVisitor());
    }

    @Test
    public void validNonceSame() {
        RskAddress sender = TestUtils.randomAddress();
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 42));

        Assert.assertTrue(rule.isValid(block, parent));
    }

    @Test
    public void invalidNonceLower() {
        RskAddress sender = TestUtils.randomAddress();
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 41));

        Assert.assertFalse(rule.isValid(block, parent));
    }

    @Test
    public void invalidNonceHigher() {
        RskAddress sender = TestUtils.randomAddress();
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 43));

        Assert.assertFalse(rule.isValid(block, parent));
    }

    @Test
    public void validTransactionsWithSameNonceAndDifferentSenders() {
        RskAddress sender1 = TestUtils.randomAddress();
        initialNonce(sender1, 64);
        RskAddress sender2 = TestUtils.randomAddress();
        initialNonce(sender2, 64);

        Block block = block(transaction(sender1, 64), transaction(sender2, 64));

        Assert.assertTrue(rule.isValid(block, parent));
    }

    @Test
    public void validConsecutiveTransactionsFromSameSender() {
        RskAddress sender = TestUtils.randomAddress();
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 42), transaction(sender, 43));

        Assert.assertTrue(rule.isValid(block, parent));
    }

    @Test
    public void invalidTransactionsFromSameSenderWithSkippedNonce() {
        RskAddress sender = TestUtils.randomAddress();
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 42), transaction(sender, 44));

        Assert.assertFalse(rule.isValid(block, parent));
    }

    @Test
    public void invalidTransactionsWithSameNonceAndSameSender() {
        RskAddress sender = TestUtils.randomAddress();
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 42), transaction(sender, 42));

        Assert.assertFalse(rule.isValid(block, parent));
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
        when(transaction.accept(any(SenderResolverVisitor.class))).thenReturn(sender);
        when(transaction.getNonce()).thenReturn(BigIntegers.asUnsignedByteArray(BigInteger.valueOf(nonce)));
        return transaction;
    }
}
