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

package co.rsk.validators;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.core.Block;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.signature.ECDSASignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the block transaction field validation rule, with emphasis on the chain-id acceptance
 * check performed before sender recovery.
 *
 * <p>{@link BlockTxsFieldsValidationRule} runs on every incoming block, before the block is executed
 * and while its parent is still the only thing known about it. Its per-transaction work used to start
 * at {@code tx.verify(signatureCache)}, which calls {@code getSender(signatureCache)} and therefore
 * performs an ECDSA public-key recovery and writes the recovered address into the shared
 * {@link BlockTxSignatureCache}. Nothing upstream had checked that the transaction's signature is
 * even acceptable for this network, so a peer could hand the node blocks packed with transactions
 * that can never be accepted — signed for another chain id, malleable (high-S), or unsigned — and
 * still make it pay the recovery cost per transaction and evict useful entries from the bounded
 * shared cache.
 *
 * <p>The rule now rejects a transaction failing {@code acceptTransactionSignature(chainId)} before
 * {@code verify()} runs, which is the same predicate transaction execution already enforces, so the
 * set of accepted blocks does not change. These tests pin both halves of that: the accept/reject
 * outcomes, and the fact that a rejected transaction costs no sender recovery and leaves no trace in
 * the signature cache.</p>
 */
class BlockTxsFieldsValidationRuleTest {

    /** The chain id this node validates for. */
    private static final byte CHAIN_ID = (byte) 33;

    /** Any other network's chain id. */
    private static final byte FOREIGN_CHAIN_ID = (byte) 30;

    /** Pre-EIP155 transactions carry no chain id and remain acceptable on every network. */
    private static final byte NO_CHAIN_ID = (byte) 0;

    private SignatureCache signatureCache;
    private BlockTxsFieldsValidationRule rule;
    private Block parent;

    @BeforeEach
    void setUp() {
        signatureCache = spy(new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
        rule = new BlockTxsFieldsValidationRule(signatureCache, CHAIN_ID);
        parent = mock(Block.class);
    }

    @Test
    void nullBlockIsInvalid() {
        assertFalse(rule.isValid(null, parent));
    }

    @Test
    void blockWithoutTransactionsIsValid() {
        assertTrue(rule.isValid(block(), parent));
    }

    @Test
    void transactionSignedForThisChainIdIsValid() {
        Transaction tx = signedTransaction(CHAIN_ID, "sender");

        assertTrue(rule.isValid(block(tx), parent));

        // the accepted transaction is the one that legitimately populates the shared cache
        verify(signatureCache, times(1)).getSender(tx);
        assertTrue(cachedSenders().containsKey(tx.getHash()));
    }

    @Test
    void transactionWithoutChainIdIsValid() {
        // pre-EIP155 signatures are chain-agnostic, so they must keep passing
        Transaction tx = signedTransaction(NO_CHAIN_ID, "sender");

        assertTrue(rule.isValid(block(tx), parent));
    }

    @Test
    void transactionSignedForAnotherChainIdIsRejected() {
        Transaction tx = signedTransaction(FOREIGN_CHAIN_ID, "sender");

        assertFalse(rule.isValid(block(tx), parent));
    }

    @Test
    void transactionSignedForAnotherChainIdIsRejectedWithoutRecoveringItsSender() {
        Transaction tx = signedTransaction(FOREIGN_CHAIN_ID, "sender");

        assertFalse(rule.isValid(block(tx), parent));

        // no ECDSA recovery is paid for a transaction that could never be accepted, and the bounded
        // shared cache is left untouched, so it cannot be filled with junk from a rejected block
        verify(signatureCache, never()).getSender(any());
        assertTrue(cachedSenders().isEmpty());
    }

    @Test
    void aSingleForeignChainIdTransactionRejectsTheBlockWithoutVerifyingTheRest() {
        Transaction foreign = signedTransaction(FOREIGN_CHAIN_ID, "attacker");
        Transaction firstValid = signedTransaction(CHAIN_ID, "sender-1");
        Transaction secondValid = signedTransaction(CHAIN_ID, "sender-2");

        assertFalse(rule.isValid(block(foreign, firstValid, secondValid), parent));

        // the rule short-circuits on the first unacceptable transaction: the remaining ones are never
        // recovered either, so the cost of an invalid block does not grow with the transactions in it
        verify(signatureCache, never()).getSender(any());
        assertTrue(cachedSenders().isEmpty());
    }

    @Test
    void transactionWithMalleableHighSSignatureIsRejectedWithoutRecoveringItsSender() {
        Transaction tx = signedTransaction(CHAIN_ID, "sender");
        tx.setSignature(withHighS(tx.getSignature()));

        assertFalse(rule.isValid(block(tx), parent));

        verify(signatureCache, never()).getSender(any());
        assertTrue(cachedSenders().isEmpty());
    }

    @Test
    void unsignedTransactionIsRejectedWithoutRecoveringItsSender() {
        Transaction tx = unsignedTransaction(CHAIN_ID);

        assertFalse(rule.isValid(block(tx), parent));

        verify(signatureCache, never()).getSender(any());
        assertTrue(cachedSenders().isEmpty());
    }

    @Test
    void remascTransactionIsValid() {
        // the remasc transaction is unsigned by design and opts out of signature acceptance, so the
        // new check must not reject the block it closes
        Transaction remasc = new RemascTransaction(2L);

        assertTrue(rule.isValid(block(remasc), parent));
    }

    @Test
    void blockWithRegularAndRemascTransactionsIsValid() {
        Transaction tx = signedTransaction(CHAIN_ID, "sender");
        Transaction remasc = new RemascTransaction(2L);

        assertTrue(rule.isValid(block(tx, remasc), parent));
    }

    @Test
    void transactionWithMalformedFieldsIsRejected() {
        // field validation itself is unchanged: an over-long nonce still fails, now after the
        // acceptance check rather than as the first thing the rule does.
        // Transaction.builder() rejects an over-long nonce at build time, so the malformed
        // transaction is assembled through the raw constructor to reach the rule.
        byte[] oversizedNonce = new byte[33];
        Arrays.fill(oversizedNonce, (byte) 0x01);
        Transaction tx = new Transaction(
                oversizedNonce,
                Coin.valueOf(1L),
                BigInteger.valueOf(21000).toByteArray(),
                TestUtils.generateAddress("receiver"),
                Coin.valueOf(10L),
                null,
                CHAIN_ID,
                false,
                TransactionTypePrefix.legacy(),
                null,
                null,
                null,
                null);
        tx.sign(TestUtils.generateECKey("sender").getPrivKeyBytes());

        assertFalse(rule.isValid(block(tx), parent));
    }

    private Block block(Transaction... transactions) {
        Block block = mock(Block.class);
        when(block.getTransactionsList()).thenReturn(Arrays.asList(transactions));
        return block;
    }

    private Transaction signedTransaction(byte chainId, String senderDiscriminator) {
        ECKey senderKey = TestUtils.generateECKey(senderDiscriminator);
        Transaction tx = transaction(chainId, BigInteger.ONE.toByteArray());
        tx.sign(senderKey.getPrivKeyBytes());
        return tx;
    }

    private Transaction unsignedTransaction(byte chainId) {
        return transaction(chainId, BigInteger.ONE.toByteArray());
    }

    private Transaction transaction(byte chainId, byte[] nonce) {
        return Transaction.builder()
                .nonce(nonce)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .receiveAddress(TestUtils.generateAddress("receiver"))
                .chainId(chainId)
                .value(BigInteger.TEN)
                .build();
    }

    /**
     * The other valid signature of the same message, with S above half the curve order. Rejected by
     * {@code acceptTransactionSignature} as malleable, without needing a key recovery to find out.
     */
    private ECDSASignature withHighS(ECDSASignature signature) {
        BigInteger highS = Constants.getSECP256K1N().subtract(signature.getS());
        return new ECDSASignature(signature.getR(), highS, signature.getV());
    }

    private Map<Keccak256, RskAddress> cachedSenders() {
        Map<Keccak256, RskAddress> addressesCache = TestUtils.getInternalState(signatureCache, "addressesCache");
        return addressesCache == null ? Collections.emptyMap() : addressesCache;
    }
}
