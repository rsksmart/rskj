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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.config.Constants;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers RSKIP-545 / Type 4 invariants on {@link Transaction#verify} and four-field typed
 * {@link TransactionReceipt} decoding. Structural rules are enforced in the Type 4 parser.
 */
class TransactionRskip545InvariantTest {

    private static final byte CHAIN_ID = 33;
    private static final byte[] EMPTY_ACCESS_LIST = RLP.encodeList();
    private static final RskAddress RECEIVER =
            new RskAddress("0x0000000000000000000000000000000000000002");

    @Test
    void builder_type4WithoutAuthorizationList_rejectedAtBuild() {
        assertThrows(RskJsonRpcRequestException.class, () -> Transaction.builder()
                .type(TransactionType.TYPE_4)
                .chainId(CHAIN_ID)
                .nonce(BigInteger.ONE.toByteArray())
                .gasPrice(Coin.valueOf(10))
                .gasLimit(BigInteger.valueOf(21_000))
                .receiveAddress(RECEIVER)
                .value(Coin.ZERO)
                .maxPriorityFeePerGas(Coin.valueOf(10))
                .maxFeePerGas(Coin.valueOf(100))
                .build());
    }

    // -------------------------------------------------------------------------
    // Transaction.validate / verify (Type 4)
    // -------------------------------------------------------------------------

    @Test
    void verify_signedType4_validSignature_doesNotThrow() {
        Transaction tx = signedType4();
        assertDoesNotThrow(() -> tx.verify(new ReceivedTxSignatureCache()));
    }

    @Test
    void verify_type4_highSOuterSignature_throws() {
        Transaction signed = signedType4();
        ECDSASignature badS = signed.getSignature();
        BigInteger highS = Constants.getSECP256K1N().subtract(BigInteger.ONE);
        Transaction tx = newTransaction(
                TransactionTypePrefix.typed(TransactionType.TYPE_4),
                signed.getMaxPriorityFeePerGas(),
                signed.getMaxFeePerGas(),
                signed.getAuthorizationList());
        tx.setSignature(ECDSASignature.fromComponents(
                org.bouncycastle.util.BigIntegers.asUnsignedByteArray(badS.getR()),
                org.bouncycastle.util.BigIntegers.asUnsignedByteArray(highS),
                badS.getV()));

        assertThrows(IllegalArgumentException.class,
                () -> tx.verify(new ReceivedTxSignatureCache()));
    }

    @Test
    void verify_type4_mismatchedMaxFee_recoversWrongSender() {
        Transaction signed = signedType4();
        RskAddress expectedSender = signed.getSender(new ReceivedTxSignatureCache());

        Transaction tampered = newTransaction(
                TransactionTypePrefix.typed(TransactionType.TYPE_4),
                signed.getMaxPriorityFeePerGas(),
                Coin.valueOf(999),
                signed.getAuthorizationList());
        tampered.setSignature(signed.getSignature());

        assertDoesNotThrow(() -> new ImmutableTransaction(tampered.getEncoded()));
        assertNotEquals(expectedSender, tampered.getSender(new ReceivedTxSignatureCache()));
    }

    @Test
    void verify_type4_mismatchedMaxPriorityFee_recoversWrongSender() {
        Transaction signed = signedType4();
        RskAddress expectedSender = signed.getSender(new ReceivedTxSignatureCache());

        Transaction tampered = newTransaction(
                TransactionTypePrefix.typed(TransactionType.TYPE_4),
                Coin.valueOf(20),
                signed.getMaxFeePerGas(),
                signed.getAuthorizationList());
        tampered.setSignature(signed.getSignature());

        assertNotEquals(expectedSender, tampered.getSender(new ReceivedTxSignatureCache()));
    }

    @Test
    void verify_type4_mismatchedAccessList_recoversWrongSender() {
        Transaction signed = signedType4();
        RskAddress expectedSender = signed.getSender(new ReceivedTxSignatureCache());
        byte[] nonEmptyAccessList = RLP.encodeList(
                RLP.encodeList(
                        RLP.encodeElement(new byte[20]),
                        RLP.encodeList(RLP.encodeElement(new byte[32]))
                )
        );

        Transaction tampered = newTransaction(
                TransactionTypePrefix.typed(TransactionType.TYPE_4),
                signed.getMaxPriorityFeePerGas(),
                signed.getMaxFeePerGas(),
                signed.getAuthorizationList(),
                nonEmptyAccessList);
        tampered.setSignature(signed.getSignature());

        assertNotEquals(expectedSender, tampered.getSender(new ReceivedTxSignatureCache()));
    }

    @Test
    void verify_type4_validSignature_recoversNonZeroSender() {
        Transaction tx = signedType4();
        ReceivedTxSignatureCache cache = new ReceivedTxSignatureCache();

        assertDoesNotThrow(() -> tx.verify(cache));
        assertNotNull(tx.getSender(cache));
        assertTrue(tx.acceptTransactionSignature(CHAIN_ID));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Transaction newTransaction(
            TransactionTypePrefix typePrefix,
            Coin maxPriorityFeePerGas,
            Coin maxFeePerGas,
            List<SetCodeAuthorization> authorizationList) {
        return newTransaction(typePrefix, maxPriorityFeePerGas, maxFeePerGas, authorizationList, EMPTY_ACCESS_LIST);
    }

    private static Transaction newTransaction(
            TransactionTypePrefix typePrefix,
            Coin maxPriorityFeePerGas,
            Coin maxFeePerGas,
            List<SetCodeAuthorization> authorizationList,
            byte[] accessListBytes) {
        return new Transaction(
                new byte[]{0x01},
                Coin.valueOf(1),
                BigInteger.valueOf(21_000).toByteArray(),
                RECEIVER,
                Coin.ZERO,
                EMPTY_BYTE_ARRAY,
                CHAIN_ID,
                false,
                typePrefix,
                accessListBytes,
                maxPriorityFeePerGas,
                maxFeePerGas,
                authorizationList);
    }

    private static Transaction signedType4() {
        Transaction tx = Rskip545TestSupport.unsignedType4(
                RECEIVER,
                Coin.valueOf(10),
                Coin.valueOf(100),
                EMPTY_BYTE_ARRAY,
                EMPTY_ACCESS_LIST);
        tx.sign(new ECKey().getPrivKeyBytes());
        return tx;
    }
}
