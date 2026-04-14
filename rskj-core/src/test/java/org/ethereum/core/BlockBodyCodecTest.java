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
import co.rsk.remasc.RemascTransaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlockBodyCodecTest {

    private static final ECKey TEST_KEY = new ECKey();
    private static final RskAddress TEST_ADDRESS = new RskAddress("0x1234567890123456789012345678901234567890");

    @Test
    void decodeTransactions_legacyRemascAtLastPosition_succeeds() {
        RemascTransaction remascTx = new RemascTransaction(1);
        byte[] legacyEncoded = remascTx.getEncoded();

        byte[] rlpList = RLP.encodeList(legacyEncoded);
        RLPList txList = RLP.decodeList(rlpList);

        List<Transaction> decoded = BlockBodyCodec.decodeTransactions(txList);

        assertEquals(1, decoded.size());
        assertInstanceOf(RemascTransaction.class, decoded.get(0));
    }

    @Test
    void decodeTransactions_typedTxAtLastPosition_isNotTreatedAsRemasc() {
        Transaction typedTx = buildSignedType1();
        byte[] encoded = BlockBodyCodec.encodeTransaction(typedTx);

        byte[] rlpList = RLP.encodeList(encoded);
        RLPList txList = RLP.decodeList(rlpList);

        List<Transaction> decoded = BlockBodyCodec.decodeTransactions(txList);

        assertEquals(1, decoded.size());
        assertFalse(decoded.get(0) instanceof RemascTransaction,
                "A typed transaction at the last position must not be treated as a remasc transaction");
        assertTrue(decoded.get(0).getType().isTyped());
    }

    @Test
    void validateRemascIsLegacy_legacyTransaction_doesNotThrow() {
        RemascTransaction remascTx = new RemascTransaction(1);
        assertDoesNotThrow(() -> BlockBodyCodec.validateRemascIsLegacy(remascTx));
    }

    @Test
    void validateRemascIsLegacy_typedTransaction_throwsException() {
        Transaction typedTx = buildSignedType1();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> BlockBodyCodec.validateRemascIsLegacy(typedTx)
        );
        assertTrue(ex.getMessage().contains("Remasc transaction must be legacy"));
    }

    private static Transaction buildSignedType1() {
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_1)
                .chainId((byte) 33)
                .nonce(BigInteger.ONE.toByteArray())
                .gasPrice(Coin.valueOf(1_000_000_000))
                .gasLimit(BigInteger.valueOf(21_000))
                .destination(TEST_ADDRESS)
                .value(Coin.valueOf(1))
                .build();
        tx.sign(TEST_KEY.getPrivKeyBytes());
        return tx;
    }
}
