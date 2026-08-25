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
import co.rsk.peg.constants.BridgeMainNetConstants;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.exception.TransactionException;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.List;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void verify_oversizeNonce_throws() {
        Transaction signed = signedType4();
        Transaction tx = copyType4(signed, oversizeWord(), signed.getGasLimit(), signed.getData());
        tx.setSignature(signed.getSignature());

        assertThrows(TransactionException.class, () -> tx.verify(new ReceivedTxSignatureCache()));
    }

    @Test
    void verify_oversizeGasLimit_throws() {
        Transaction signed = signedType4();
        Transaction tx = copyType4(signed, signed.getNonce(), oversizeWord(), signed.getData());
        tx.setSignature(signed.getSignature());

        assertThrows(TransactionException.class, () -> tx.verify(new ReceivedTxSignatureCache()));
    }

    @Test
    void verify_oversizeMaxPriorityFee_throws() {
        Transaction signed = signedType4();
        Transaction tx = newTransaction(
                TransactionTypePrefix.typed(TransactionType.TYPE_4),
                new Coin(oversizeWord()),
                signed.getMaxFeePerGas(),
                signed.getAuthorizationList());
        tx.setSignature(signed.getSignature());

        assertThrows(TransactionException.class, () -> tx.verify(new ReceivedTxSignatureCache()));
    }

    @Test
    void verify_oversizeMaxFee_throws() {
        Transaction signed = signedType4();
        Transaction tx = newTransaction(
                TransactionTypePrefix.typed(TransactionType.TYPE_4),
                signed.getMaxPriorityFeePerGas(),
                new Coin(oversizeWord()),
                signed.getAuthorizationList());
        tx.setSignature(signed.getSignature());

        assertThrows(TransactionException.class, () -> tx.verify(new ReceivedTxSignatureCache()));
    }

    @Test
    void verify_oversizeValue_throws() {
        Transaction signed = signedType4();
        Transaction tx = new Transaction(
                signed.getNonce(),
                signed.getGasPrice(),
                signed.getGasLimit(),
                RECEIVER,
                new Coin(oversizeWord()),
                EMPTY_BYTE_ARRAY,
                CHAIN_ID,
                false,
                TransactionTypePrefix.typed(TransactionType.TYPE_4),
                EMPTY_ACCESS_LIST,
                signed.getMaxPriorityFeePerGas(),
                signed.getMaxFeePerGas(),
                signed.getAuthorizationList());
        tx.setSignature(signed.getSignature());

        assertThrows(TransactionException.class, () -> tx.verify(new ReceivedTxSignatureCache()));
    }

    @Test
    void verify_oversizeSignatureR_throws() {
        Transaction signed = signedType4();
        ECDSASignature signature = signed.getSignature();
        Transaction tx = copyType4(signed, signed.getNonce(), signed.getGasLimit(), signed.getData());
        tx.setSignature(ECDSASignature.fromComponents(
                oversizeWord(),
                org.bouncycastle.util.BigIntegers.asUnsignedByteArray(signature.getS()),
                signature.getV()));

        assertThrows(TransactionException.class, () -> tx.verify(new ReceivedTxSignatureCache()));
    }

    @Test
    void getGasPrice_nullInternalGasPrice_returnsZero() {
        Transaction tx = new Transaction(
                new byte[]{0x01},
                null,
                BigInteger.valueOf(21_000).toByteArray(),
                RECEIVER,
                Coin.ZERO,
                EMPTY_BYTE_ARRAY,
                CHAIN_ID,
                false,
                TransactionTypePrefix.typed(TransactionType.LEGACY),
                null,
                null,
                null,
                null);

        assertEquals(Coin.ZERO, tx.getGasPrice());
    }

    @Test
    void verify_oversizeGasPrice_throws() {
        Transaction signed = signedType4();
        Transaction tx = new Transaction(
                signed.getNonce(),
                new Coin(oversizeWord()),
                signed.getGasLimit(),
                RECEIVER,
                signed.getValue(),
                signed.getData(),
                CHAIN_ID,
                false,
                TransactionTypePrefix.typed(TransactionType.TYPE_1),
                EMPTY_ACCESS_LIST,
                null,
                null,
                null);
        tx.setSignature(signed.getSignature());

        assertThrows(TransactionException.class, () -> tx.verify(new ReceivedTxSignatureCache()));
    }

    @Test
    void verify_oversizeSignatureS_throws() {
        Transaction signed = signedType4();
        ECDSASignature signature = signed.getSignature();
        Transaction tx = copyType4(signed, signed.getNonce(), signed.getGasLimit(), signed.getData());
        tx.setSignature(ECDSASignature.fromComponents(
                org.bouncycastle.util.BigIntegers.asUnsignedByteArray(signature.getR()),
                oversizeWord(),
                signature.getV()));

        assertThrows(TransactionException.class, () -> tx.verify(new ReceivedTxSignatureCache()));
    }

    @Test
    void verify_invalidReceiveAddressLength_throws() {
        RskAddress badAddress = Mockito.mock(RskAddress.class);
        Mockito.when(badAddress.getBytes()).thenReturn(new byte[]{0x01, 0x02, 0x03});

        Transaction tx = new Transaction(
                new byte[]{0x01},
                Coin.valueOf(1),
                BigInteger.valueOf(21_000).toByteArray(),
                badAddress,
                Coin.ZERO,
                EMPTY_BYTE_ARRAY,
                CHAIN_ID,
                false,
                TransactionTypePrefix.typed(TransactionType.LEGACY),
                null,
                null,
                null,
                null);
        tx.setSignature(ECDSASignature.fromComponents(new byte[32], new byte[32], (byte) 27));

        assertThrows(TransactionException.class, () -> tx.verify(new ReceivedTxSignatureCache()));
    }

    @Test
    void transactionCost_nullDataField_countsNoNonZeroBytes() throws Exception {
        Transaction tx = signedType4();
        java.lang.reflect.Field dataField = Transaction.class.getDeclaredField("data");
        dataField.setAccessible(true);
        dataField.set(tx, null);

        Constants constants = Mockito.mock(Constants.class);
        Mockito.doReturn(BridgeMainNetConstants.getInstance()).when(constants).getBridgeConstants();
        ActivationConfig.ForBlock activations = Mockito.mock(ActivationConfig.ForBlock.class);

        assertTrue(tx.transactionCost(constants, activations, new ReceivedTxSignatureCache()) > 0);
    }

    @Test
    void verify_invalidSenderAddressLength_throws() throws Exception {
        Transaction tx = signedType4();
        RskAddress badSender = Mockito.mock(RskAddress.class);
        Mockito.when(badSender.getBytes()).thenReturn(new byte[]{0x01, 0x02, 0x03});

        java.lang.reflect.Field senderField = Transaction.class.getDeclaredField("sender");
        senderField.setAccessible(true);
        senderField.set(tx, badSender);

        assertThrows(TransactionException.class, () -> tx.verify(new ReceivedTxSignatureCache()));
    }

    @Test
    void getSender_corruptSignature_returnsNullAddress() {
        Transaction tx = signedType4();
        ECDSASignature signature = tx.getSignature();
        byte[] garbageR = ArrayUtils.addAll(
                new byte[]{1},
                ByteUtil.bigIntegerToBytes(signature.getR(), 64));
        byte[] garbageS = ArrayUtils.addAll(
                new byte[]{1},
                ByteUtil.bigIntegerToBytes(signature.getS(), 64));
        tx.setSignature(ECDSASignature.fromComponents(garbageR, garbageS, signature.getV()));

        assertEquals(RskAddress.nullAddress(), tx.getSender());
    }

    @Test
    void equals_differentType_returnsFalse() {
        Transaction left = signedType4();
        assertFalse(left.equals("not-a-transaction"));
    }

    @Test
    void toString_type4_includesAuthorizationListLength() {
        Transaction tx = signedType4();
        assertTrue(tx.toString().contains("authorizationListLen=" + tx.getAuthorizationList().size()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static byte[] oversizeWord() {
        byte[] word = new byte[33];
        word[0] = 0x01;
        return word;
    }

    private static Transaction copyType4(
            Transaction signed,
            byte[] nonce,
            byte[] gasLimit,
            byte[] data) {
        return new Transaction(
                nonce,
                signed.getGasPrice(),
                gasLimit,
                RECEIVER,
                signed.getValue(),
                data,
                CHAIN_ID,
                false,
                TransactionTypePrefix.typed(TransactionType.TYPE_4),
                EMPTY_ACCESS_LIST,
                signed.getMaxPriorityFeePerGas(),
                signed.getMaxFeePerGas(),
                signed.getAuthorizationList());
    }

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
