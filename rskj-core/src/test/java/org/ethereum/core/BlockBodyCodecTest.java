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

import co.rsk.remasc.RemascTransaction;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlockBodyCodecTest {

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
    void decodeTransactions_typedRemascAtLastPosition_throwsException() {
        RemascTransaction remascTx = new RemascTransaction(1);
        byte[] legacyEncoded = remascTx.getEncoded();

        byte[] typedCanonical = ByteUtil.merge(new byte[]{TransactionType.TYPE_1.getByteCode()}, legacyEncoded);
        byte[] wrappedTyped = RLP.encodeElement(typedCanonical);
        byte[] rlpList = RLP.encodeList(wrappedTyped);
        RLPList txList = RLP.decodeList(rlpList);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> BlockBodyCodec.decodeTransactions(txList)
        );
        assertTrue(ex.getMessage().contains("Remasc transaction must be legacy"));
    }

    @Test
    void validateRemascIsLegacy_legacyTransaction_doesNotThrow() {
        RemascTransaction remascTx = new RemascTransaction(1);
        assertDoesNotThrow(() -> BlockBodyCodec.validateRemascIsLegacy(remascTx));
    }

    @Test
    void validateRemascIsLegacy_typedTransaction_throwsException() {
        RemascTransaction legacyRemasc = new RemascTransaction(1);
        byte[] legacyEncoded = legacyRemasc.getEncoded();

        byte[] typedCanonical = ByteUtil.merge(new byte[]{TransactionType.TYPE_1.getByteCode()}, legacyEncoded);
        Transaction typedTx = new ImmutableTransaction(typedCanonical);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> BlockBodyCodec.validateRemascIsLegacy(typedTx)
        );
        assertTrue(ex.getMessage().contains("Remasc transaction must be legacy"));
    }
}
