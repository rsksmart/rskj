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
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.HashUtil;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;

/**
 * Unit tests for {@link AccessListCodec}.
 *
 * <p>RSKIP-546 / EIP-2930: access lists are RLP-encoded as
 * {@code [[address, [storageKey, ...]], ...]} where address is 20 bytes
 * and each storage key is 32 bytes.
 */
class AccessListCodecTest {

    private static final byte[] EMPTY_LIST_RLP = new byte[]{(byte) 0xc0};

    // -------------------------------------------------------------------------
    // defaultAccessListBytes
    // -------------------------------------------------------------------------

    @Test
    void defaultAccessListBytes_null_returnsEmptyListRlp() {
        assertArrayEquals(EMPTY_LIST_RLP, AccessListCodec.defaultAccessListBytes(null));
    }

    @Test
    void defaultAccessListBytes_emptyArray_returnsEmptyListRlp() {
        assertArrayEquals(EMPTY_LIST_RLP, AccessListCodec.defaultAccessListBytes(new byte[0]));
    }

    @Test
    void defaultAccessListBytes_validRlp_passesThroughUnchanged() {
        // Encode an empty list and pass it in; should come back unchanged
        byte[] validRlp = RLP.encodeList();
        assertArrayEquals(validRlp, AccessListCodec.defaultAccessListBytes(validRlp));
    }

    @Test
    void defaultAccessListBytes_invalidRlp_throws() {
        // 0xff is not valid RLP
        byte[] garbage = new byte[]{(byte) 0xff, 0x01, 0x02};
        assertThrows(IllegalArgumentException.class,
                () -> AccessListCodec.defaultAccessListBytes(garbage));
    }

    @Test
    void defaultAccessListBytes_notAList_throws() {
        byte[] notList = RLP.encodeElement(new byte[] {0x01});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AccessListCodec.defaultAccessListBytes(notList));
        assertTrue(ex.getMessage().contains("Access list must be an RLP list"), ex.getMessage());
    }

    @Test
    void defaultAccessListBytes_emptyEntry_throws() {
        byte[] accessList = RLP.encodeList(RLP.encodeList());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AccessListCodec.defaultAccessListBytes(accessList));
        assertTrue(ex.getMessage().contains("2 elements"), ex.getMessage());
    }

    @Test
    void defaultAccessListBytes_wrongAddressLength_throws() {
        byte[] accessList = RLP.encodeList(
                RLP.encodeList(
                        RLP.encodeElement(new byte[21]),
                        RLP.encodeList()
                )
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AccessListCodec.defaultAccessListBytes(accessList));
        assertTrue(ex.getMessage().contains("20 bytes"), ex.getMessage());
    }

    @Test
    void defaultAccessListBytes_wrongStorageKeyLength_throws() {
        byte[] accessList = RLP.encodeList(
                RLP.encodeList(
                        RLP.encodeElement(new byte[20]),
                        RLP.encodeList(RLP.encodeElement(new byte[16]))
                )
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AccessListCodec.defaultAccessListBytes(accessList));
        assertTrue(ex.getMessage().contains("32 bytes"), ex.getMessage());
    }

    @Test
    void defaultAccessListBytes_malformedEntryShape_throws() {
        byte[] accessList = RLP.encodeList(RLP.encodeList(RLP.encodeElement(new byte[20])));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AccessListCodec.defaultAccessListBytes(accessList));
        assertTrue(ex.getMessage().contains("2 elements"), ex.getMessage());
    }

    @Test
    void defaultAccessListBytes_validEntry_passes() {
        byte[] address = new byte[20];
        address[19] = 0x01;
        byte[] storageKey = new byte[32];
        storageKey[0] = 0x02;
        byte[] accessList = RLP.encodeList(
                RLP.encodeList(
                        RLP.encodeElement(address),
                        RLP.encodeList(RLP.encodeElement(storageKey))
                )
        );

        assertDoesNotThrow(() -> AccessListCodec.defaultAccessListBytes(accessList));
    }

    @Test
    void defaultAccessListBytes_returnedArrayIsIndependentCopy() {
        byte[] result1 = AccessListCodec.defaultAccessListBytes(null);
        byte[] result2 = AccessListCodec.defaultAccessListBytes(null);
        assertNotSame(result1, result2, "Each call must return a fresh copy");
    }

    // -------------------------------------------------------------------------
    // encodeAccessList
    // -------------------------------------------------------------------------

    @Test
    void encodeAccessList_null_returnsNull() {
        assertNull(AccessListCodec.encodeAccessList(null));
    }

    @Test
    void encodeAccessList_emptyList_returnsNull() {
        assertNull(AccessListCodec.encodeAccessList(Collections.emptyList()));
    }

    @Test
    void encodeAccessList_singleEntryNoStorageKeys_encodesCorrectly() {
        CallArguments.AccessListEntry entry = new CallArguments.AccessListEntry();
        entry.setAddress("0x" + "aa".repeat(20));
        entry.setStorageKeys(Collections.emptyList());

        byte[] encoded = AccessListCodec.encodeAccessList(List.of(entry));

        assertNotNull(encoded);
        // Verify it round-trips through defaultAccessListBytes without error
        assertDoesNotThrow(() -> AccessListCodec.defaultAccessListBytes(encoded));
    }

    @Test
    void encodeAccessList_singleEntryWithStorageKey_encodesCorrectly() {
        CallArguments.AccessListEntry entry = new CallArguments.AccessListEntry();
        entry.setAddress("0x" + "bb".repeat(20));
        entry.setStorageKeys(List.of("0x" + "cc".repeat(32)));

        byte[] encoded = AccessListCodec.encodeAccessList(List.of(entry));

        assertNotNull(encoded);
        assertDoesNotThrow(() -> AccessListCodec.defaultAccessListBytes(encoded));
    }

    @Test
    void encodeAccessList_multipleEntries_encodesAll() {
        CallArguments.AccessListEntry e1 = new CallArguments.AccessListEntry();
        e1.setAddress("0x" + "11".repeat(20));
        e1.setStorageKeys(Collections.emptyList());

        CallArguments.AccessListEntry e2 = new CallArguments.AccessListEntry();
        e2.setAddress("0x" + "22".repeat(20));
        e2.setStorageKeys(Arrays.asList("0x" + "aa".repeat(32), "0x" + "bb".repeat(32)));

        byte[] encoded = AccessListCodec.encodeAccessList(List.of(e1, e2));

        assertNotNull(encoded);
        assertDoesNotThrow(() -> AccessListCodec.defaultAccessListBytes(encoded));
    }

    @Test
    void encodeAccessList_entryMissingAddress_throws() {
        CallArguments.AccessListEntry entry = new CallArguments.AccessListEntry();
        entry.setStorageKeys(Collections.emptyList());

        assertThrows(RskJsonRpcRequestException.class,
                () -> AccessListCodec.encodeAccessList(List.of(entry)));
    }

    @Test
    void encodeAccessList_addressWrongLength_throws() {
        CallArguments.AccessListEntry entry = new CallArguments.AccessListEntry();
        entry.setAddress("0x" + "aa".repeat(10)); // 10 bytes, not 20
        entry.setStorageKeys(Collections.emptyList());

        assertThrows(RskJsonRpcRequestException.class,
                () -> AccessListCodec.encodeAccessList(List.of(entry)));
    }

    @Test
    void encodeAccessList_storageKeyWrongLength_throws() {
        CallArguments.AccessListEntry entry = new CallArguments.AccessListEntry();
        entry.setAddress("0x" + "aa".repeat(20));
        entry.setStorageKeys(List.of("0x" + "bb".repeat(16))); // 16 bytes, not 32

        assertThrows(RskJsonRpcRequestException.class,
                () -> AccessListCodec.encodeAccessList(List.of(entry)));
    }

    // -------------------------------------------------------------------------
    // requireRawAccessListBytes
    // -------------------------------------------------------------------------

    @Test
    void requireRawAccessListBytes_validList_returnsItsFrame() {
        byte[] accessList = populatedAccessList();

        assertArrayEquals(accessList, AccessListCodec.requireRawAccessListBytes(RLP.decode2(accessList).get(0)));
    }

    @Test
    void requireRawAccessListBytes_emptyList_returnsEmptyListRlp() {
        assertArrayEquals(EMPTY_LIST_RLP, AccessListCodec.requireRawAccessListBytes(RLP.decode2(EMPTY_LIST_RLP).get(0)));
    }

    @Test
    void requireRawAccessListBytes_stringFramedSlot_throws() {
        RLPElement slot = RLP.decode2(RLP.encodeElement(populatedAccessList())).get(0);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AccessListCodec.requireRawAccessListBytes(slot));
        assertTrue(e.getMessage().contains("Access list must be encoded as an RLP list"), e.getMessage());
    }

    @Test
    void requireRawAccessListBytes_malformedEntry_throws() {
        byte[] accessList = RLP.encodeList(RLP.encodeList(RLP.encodeElement(new byte[20])));

        assertThrows(IllegalArgumentException.class,
                () -> AccessListCodec.requireRawAccessListBytes(RLP.decode2(accessList).get(0)));
    }

    /** The raw path proves the access list's framing once, as part of the whole envelope. */
    @Test
    void typedRawParseReencodesTheAccessListOnce() {
        byte[] accessList = populatedAccessList();
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_2)
                .nonce(BigInteger.ONE)
                .maxPriorityFeePerGas(Coin.valueOf(1))
                .maxFeePerGas(Coin.valueOf(2))
                .gasLimit(BigInteger.valueOf(30_000))
                .receiveAddress(new byte[20])
                .value(BigInteger.ZERO)
                .accessList(accessList)
                .chainId((byte) 33)
                .build();
        tx.sign(HashUtil.keccak256("access-list-sender".getBytes()));
        byte[] raw = tx.getEncoded();

        try (MockedStatic<CommonParsingUtils> utils =
                     Mockito.mockStatic(CommonParsingUtils.class, Mockito.CALLS_REAL_METHODS)) {
            Transaction.fromRaw(raw);

            utils.verify(() -> CommonParsingUtils.reencodeCanonical(argThat(element ->
                    element instanceof RLPList && Arrays.equals(accessList, element.getRLPRawData()))), times(1));
        }
    }

    private static byte[] populatedAccessList() {
        byte[] address = new byte[20];
        address[0] = 0x55;
        byte[] key = new byte[32];
        key[0] = (byte) 0x80;
        return RLP.encodeList(RLP.encodeList(RLP.encodeElement(address), RLP.encodeList(RLP.encodeElement(key))));
    }
}
