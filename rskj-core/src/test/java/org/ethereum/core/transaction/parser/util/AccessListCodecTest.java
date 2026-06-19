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

import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;

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
}
