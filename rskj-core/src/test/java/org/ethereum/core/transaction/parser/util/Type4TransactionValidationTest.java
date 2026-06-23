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
package org.ethereum.core.transaction.parser.util;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Rskip546TestSupport;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.exception.TransactionException;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.parser.ParsedType4Transaction;
import org.ethereum.core.transaction.parser.SignedSignature;
import org.ethereum.core.transaction.parser.UnsignedSignature;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Type4TransactionValidationTest {

    private static final byte CHAIN_ID = 33;
    private static final RskAddress RECEIVER =
            new RskAddress("0x1234567890123456789012345678901234567890");
    private static final SignatureCache SIGNATURE_CACHE =
            new BlockTxSignatureCache(new ReceivedTxSignatureCache());

    @Test
    void validateParsed_signedSignature_doesNotThrow() {
        ECKey.ECDSASignature sig = new ECKey().sign(new byte[32]);
        ECDSASignature signature = ECDSASignature.fromComponents(
                org.bouncycastle.util.BigIntegers.asUnsignedByteArray(sig.r),
                org.bouncycastle.util.BigIntegers.asUnsignedByteArray(sig.s),
                sig.v);
        ParsedType4Transaction parsed = sampleParsed(new SignedSignature((byte) CHAIN_ID, signature));

        assertDoesNotThrow(() -> Type4TransactionValidation.validateParsed(parsed));
    }

    @Test
    void validateParsed_unsignedSignature_doesNotThrow() {
        ParsedType4Transaction parsed = sampleParsed(new UnsignedSignature(CHAIN_ID));

        assertDoesNotThrow(() -> Type4TransactionValidation.validateParsed(parsed));
    }

    @Test
    void validateOuterSignatureFormat_invalidComponents_throws() {
        ECDSASignature invalid = ECDSASignature.fromComponents(new byte[0], new byte[0], (byte) 27);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> Type4TransactionValidation.validateOuterSignatureFormat(invalid));

        assertTrue(ex.getMessage().contains("signature components are invalid"), ex.getMessage());
    }

    @Test
    void validateOuterSignatureRecovery_nonType4_isNoOp() {
        Transaction legacy = Rskip546TestSupport.unsignedType1(
                CHAIN_ID, RECEIVER, Coin.valueOf(10), new byte[0], Rskip546TestSupport.EMPTY_ACCESS_LIST);
        legacy.sign(new byte[32]);

        assertDoesNotThrow(() -> Type4TransactionValidation.validateOuterSignatureRecovery(legacy, SIGNATURE_CACHE));
    }

    @Test
    void validateOuterSignatureRecovery_unsignedType4_isNoOp() {
        Transaction tx = Rskip545TestSupport.unsignedType4();

        assertDoesNotThrow(() -> Type4TransactionValidation.validateOuterSignatureRecovery(tx, SIGNATURE_CACHE));
    }

    @Test
    void validateOuterSignatureRecovery_zeroAddressSender_throws() {
        Transaction tx = mock(Transaction.class);
        when(tx.getType()).thenReturn(TransactionType.TYPE_4);
        when(tx.getSignature()).thenReturn(ECDSASignature.fromComponents(new byte[]{1}, new byte[]{1}, (byte) 27));
        when(tx.getSender(SIGNATURE_CACHE)).thenReturn(RskAddress.ZERO_ADDRESS);

        assertThrows(TransactionException.class,
                () -> Type4TransactionValidation.validateOuterSignatureRecovery(tx, SIGNATURE_CACHE));
    }

    private static ParsedType4Transaction sampleParsed(org.ethereum.core.transaction.parser.SignatureState signatureState) {
        return new ParsedType4Transaction(
                TransactionTypePrefix.typed(TransactionType.TYPE_4),
                new byte[]{0x01},
                BigInteger.valueOf(21_000).toByteArray(),
                RECEIVER,
                Coin.ZERO,
                new byte[0],
                signatureState,
                RLP.encodeList(),
                Coin.valueOf(10),
                Coin.valueOf(100),
                List.of(Rskip545TestSupport.minimalAuthorization(CHAIN_ID)));
    }
}
