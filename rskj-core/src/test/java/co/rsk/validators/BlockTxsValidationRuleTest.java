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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockTxsValidationRuleTest {

    private BlockTxsValidationRule rule;
    private RepositorySnapshot repositorySnapshot;
    private Block parent;
    private SignatureCache signatureCache;
    private RepositoryLocator repositoryLocator;

    @BeforeEach
    void setup() {
        parent = mock(Block.class);
        BlockHeader parentHeader = mock(BlockHeader.class);
        when(parent.getHeader()).thenReturn(parentHeader);

        repositoryLocator = mock(RepositoryLocator.class);
        repositorySnapshot = mock(RepositorySnapshot.class);
        when(repositoryLocator.snapshotAt(parentHeader)).thenReturn(repositorySnapshot);

        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        rule = new BlockTxsValidationRule(repositoryLocator, signatureCache, ActivationConfigsForTest.allBut(ConsensusRule.RSKIP545));
    }

    private static final long RSKIP545_ACTIVATION_HEIGHT = 1000;

    private BlockTxsValidationRule ruleWithRskip545ActiveFrom(long activationHeight) {
        ActivationConfig activationConfig = mock(ActivationConfig.class);
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP545), anyLong()))
                .thenAnswer(invocation -> (long) invocation.getArgument(1) >= activationHeight);
        return new BlockTxsValidationRule(repositoryLocator, signatureCache, activationConfig);
    }

    @Test
    void rskip545Active_acceptsTxWithNonceAheadOfParentAfterOtherSenderTx() {
        RskAddress otherSender = TestUtils.generateAddress("otherSender");
        RskAddress sender = TestUtils.generateAddress("sender");
        initialNonce(otherSender, 10);
        initialNonce(sender, 5);

        Block block = block(RSKIP545_ACTIVATION_HEIGHT, transaction(otherSender, 10), transaction(sender, 6));

        Assertions.assertTrue(ruleWithRskip545ActiveFrom(RSKIP545_ACTIVATION_HEIGHT).isValid(block, parent));
    }

    @Test
    void rskip545Active_stillRejectsTxWithInvalidSignature() {
        RskAddress sender = TestUtils.generateAddress("sender");
        initialNonce(sender, 5);
        Transaction badTx = transaction(sender, 5);
        doThrow(new IllegalArgumentException("bad signature")).when(badTx).verify(any(SignatureCache.class));

        Block block = block(RSKIP545_ACTIVATION_HEIGHT, badTx);

        Assertions.assertFalse(ruleWithRskip545ActiveFrom(RSKIP545_ACTIVATION_HEIGHT).isValid(block, parent));
    }

    @Test
    void rskip545Active_doesNotReadParentState() {
        RskAddress sender = TestUtils.generateAddress("sender");

        Block block = block(RSKIP545_ACTIVATION_HEIGHT, transaction(sender, 3));

        Assertions.assertTrue(ruleWithRskip545ActiveFrom(RSKIP545_ACTIVATION_HEIGHT).isValid(block, parent));
        verify(repositoryLocator, never()).snapshotAt(any());
    }

    @Test
    void rskip545Boundary_blockBeforeActivationKeepsStaticCheck() {
        RskAddress sender = TestUtils.generateAddress("sender");
        initialNonce(sender, 5);

        Block block = block(RSKIP545_ACTIVATION_HEIGHT - 1, transaction(sender, 5), transaction(sender, 7));

        Assertions.assertFalse(ruleWithRskip545ActiveFrom(RSKIP545_ACTIVATION_HEIGHT).isValid(block, parent));
    }

    @Test
    void rskip545Boundary_activationBlockSkipsStaticCheck() {
        RskAddress sender = TestUtils.generateAddress("sender");
        initialNonce(sender, 5);

        Block block = block(RSKIP545_ACTIVATION_HEIGHT, transaction(sender, 5), transaction(sender, 7));

        Assertions.assertTrue(ruleWithRskip545ActiveFrom(RSKIP545_ACTIVATION_HEIGHT).isValid(block, parent));
    }


    @Test
    void validAuthorityTransactionAfterSponsorTupleBumpedItsNonce() {
        ECKey sponsorKey = new ECKey();
        ECKey authorityKey = new ECKey();
        RskAddress sponsor = new RskAddress(sponsorKey.getAddress());
        RskAddress authority = new RskAddress(authorityKey.getAddress());
        RskAddress delegate = TestUtils.generateAddress("delegate");
        initialNonce(sponsor, 10);
        initialNonce(authority, 5);

        SetCodeAuthorization tuple = Rskip545TestSupport.createSignedAuthorization(
                authorityKey, delegate, BigInteger.valueOf(5), Rskip545TestSupport.REGTEST_CHAIN_ID);

        Transaction sponsorTx = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .chainId(Rskip545TestSupport.REGTEST_CHAIN_ID)
                .nonce(BigInteger.valueOf(10))
                .gasLimit(BigInteger.valueOf(100_000))
                .maxPriorityFeePerGas(Coin.valueOf(1))
                .maxFeePerGas(Coin.valueOf(1))
                .receiveAddress(delegate)
                .value(Coin.ZERO)
                .data(new byte[0])
                .authorizationList(List.of(tuple))
                .build();
        sponsorTx.sign(sponsorKey.getPrivKeyBytes());

        Transaction authorityTx = legacyTx(authorityKey, 6, delegate, 21_000);

        Block block = block(RSKIP545_ACTIVATION_HEIGHT, sponsorTx, authorityTx);

        Assertions.assertTrue(ruleWithRskip545ActiveFrom(RSKIP545_ACTIVATION_HEIGHT).isValid(block, parent));
    }

    private static Transaction legacyTx(ECKey senderKey, long nonce, RskAddress to, long gasLimit) {
        Transaction tx = Transaction.builder()
                .chainId(Rskip545TestSupport.REGTEST_CHAIN_ID)
                .nonce(BigInteger.valueOf(nonce))
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(gasLimit))
                .receiveAddress(to)
                .value(BigInteger.ZERO)
                .build();
        tx.sign(senderKey.getPrivKeyBytes());
        return tx;
    }

    @Test
    void validNonceSame() {
        RskAddress sender = TestUtils.generateAddress("sender");
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 42));

        Assertions.assertTrue(rule.isValid(block, parent));
    }

    @Test
    void invalidNonceLower() {
        RskAddress sender = TestUtils.generateAddress("sender");
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 41));

        Assertions.assertFalse(rule.isValid(block, parent));
    }

    @Test
    void invalidNonceHigher() {
        RskAddress sender = TestUtils.generateAddress("sender");
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 43));

        Assertions.assertFalse(rule.isValid(block, parent));
    }

    @Test
    void validTransactionsWithSameNonceAndDifferentSenders() {
        RskAddress sender1 = TestUtils.generateAddress("sender1");
        initialNonce(sender1, 64);
        RskAddress sender2 = TestUtils.generateAddress("sender2");
        initialNonce(sender2, 64);

        Block block = block(transaction(sender1, 64), transaction(sender2, 64));

        Assertions.assertTrue(rule.isValid(block, parent));
    }

    @Test
    void validConsecutiveTransactionsFromSameSender() {
        RskAddress sender = TestUtils.generateAddress("sender");
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 42), transaction(sender, 43));

        Assertions.assertTrue(rule.isValid(block, parent));
    }

    @Test
    void invalidTransactionsFromSameSenderWithSkippedNonce() {
        RskAddress sender = TestUtils.generateAddress("sender");
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 42), transaction(sender, 44));

        Assertions.assertFalse(rule.isValid(block, parent));
    }

    @Test
    void invalidTransactionsWithSameNonceAndSameSender() {
        RskAddress sender = TestUtils.generateAddress("sender");
        initialNonce(sender, 42);

        Block block = block(transaction(sender, 42), transaction(sender, 42));

        Assertions.assertFalse(rule.isValid(block, parent));
    }

    private void initialNonce(RskAddress sender, int nonce) {
        when(repositorySnapshot.getNonce(sender)).thenReturn(BigInteger.valueOf(nonce));
    }

    private Block block(Transaction... transactions) {
        return block(1, transactions);
    }

    private Block block(long number, Transaction... transactions) {
        Block block = mock(Block.class);
        when(block.getNumber()).thenReturn(number);
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
