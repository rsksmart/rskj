/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BlockHeaderExtensionV2Test {

    @Test
    void constructorAndGetters() {
        byte[] logsBloom = new byte[256];
        Arrays.fill(logsBloom, (byte) 0xAB);
        short[] edges = new short[] {1, 2, 3};
        byte[] baseEvent = new byte[] {0x01, 0x02};

        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(logsBloom, edges, baseEvent);

        assertArrayEquals(logsBloom, ext.getLogsBloom());
        assertArrayEquals(edges, ext.getTxExecutionSublistsEdges());
        assertArrayEquals(baseEvent, ext.getBridgeEvent());
        assertEquals(0x2, ext.getVersion());
    }

    @Test
    void setBaseEventCopiesArray() {
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(null, null, null);
        byte[] hash = new byte[] {0x0A, 0x0B};
        ext.setBridgeEvent(hash);

        assertArrayEquals(hash, ext.getBridgeEvent());
        // Mutate original array to check for defensive copy
        hash[0] = 0x00;
        assertNotEquals(hash[0], ext.getBridgeEvent()[0]);
    }

    @Test
    void getBaseEventReturnsCopy() {
        byte[] hash = new byte[] {0x0A, 0x0B};
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(null, null, hash);
        byte[] returned = ext.getBridgeEvent();
        returned[0] = 0x00;
        assertNotEquals(returned[0], ext.getBridgeEvent()[0]);
    }

    @Test
    void encodingAndDecoding() {
        byte[] logsBloom = new byte[256];
        Arrays.fill(logsBloom, (byte) 0x01);
        short[] edges = new short[] {10, 20};
        byte[] superChainDataHash = new byte[] {0x55, 0x66};

        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(logsBloom, edges, superChainDataHash);
        byte[] encoded = ext.getEncoded();

        BlockHeaderExtensionV2 decoded = BlockHeaderExtensionV2.fromEncoded(encoded);

        assertArrayEquals(logsBloom, decoded.getLogsBloom());
        assertArrayEquals(edges, decoded.getTxExecutionSublistsEdges());
        assertArrayEquals(superChainDataHash, decoded.getBridgeEvent());
    }

    @Test
    void encodingWithNullFields() {
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(null, null, null);
        byte[] encoded = ext.getEncoded();
        BlockHeaderExtensionV2 decoded = BlockHeaderExtensionV2.fromEncoded(encoded);

        assertNull(decoded.getLogsBloom());
        assertNull(decoded.getTxExecutionSublistsEdges());
        assertNull(decoded.getBridgeEvent());
    }

    @Test
    void decodeNullInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> BlockHeaderExtensionV2.fromEncoded(null));
    }

    @Test
    void decodeNullFields() {
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(null, null, null);
        byte[] encoded = ext.getEncoded();

        BlockHeaderExtensionV2 decoded = BlockHeaderExtensionV2.fromEncoded(encoded);

        assertNull(decoded.getLogsBloom());
        assertNull(decoded.getTxExecutionSublistsEdges());
        assertNull(decoded.getBridgeEvent());
    }

    @Test
    void decodeOnlyLogsBloom() {
        byte[] logsBloom = new byte[256];
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(logsBloom, null, null);
        byte[] encoded = ext.getEncoded();

        BlockHeaderExtensionV2 decoded = BlockHeaderExtensionV2.fromEncoded(encoded);

        assertArrayEquals(logsBloom, decoded.getLogsBloom());
        assertNull(decoded.getTxExecutionSublistsEdges());
        assertNull(decoded.getBridgeEvent());
    }

    @Test
    void decodeLogsBloomAndEdges() {
        byte[] logsBloom = new byte[256];
        short[] edges = new short[] {1, 2};
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(logsBloom, edges, null);
        byte[] encoded = ext.getEncoded();

        BlockHeaderExtensionV2 decoded = BlockHeaderExtensionV2.fromEncoded(encoded);

        assertArrayEquals(logsBloom, decoded.getLogsBloom());
        assertArrayEquals(edges, decoded.getTxExecutionSublistsEdges());
        assertNull(decoded.getBridgeEvent());
    }

    @Test
    void decodeWithEmptyEdges() {
        byte[] logsBloom = new byte[256];
        byte[] baseEvent = new byte[128];
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(logsBloom, null, baseEvent);
        byte[] encoded = ext.getEncoded();

        BlockHeaderExtensionV2 decoded = BlockHeaderExtensionV2.fromEncoded(encoded);

        assertArrayEquals(logsBloom, decoded.getLogsBloom());
        assertArrayEquals(baseEvent, decoded.getBridgeEvent());
        assertNull(decoded.getTxExecutionSublistsEdges());
    }

    @Test
    void decodeWithEmptyBridgeEvent() {
        byte[] logsBloom = new byte[256];
        short[] edges = new short[] {1};
        byte[] baseEvent = new byte[0];
        BlockHeaderExtensionV2 ext = new BlockHeaderExtensionV2(logsBloom, edges, baseEvent);
        byte[] encoded = ext.getEncoded();

        BlockHeaderExtensionV2 decoded = BlockHeaderExtensionV2.fromEncoded(encoded);

        assertArrayEquals(logsBloom, decoded.getLogsBloom());
        assertArrayEquals(edges, decoded.getTxExecutionSublistsEdges());
        assertNull(decoded.getBridgeEvent());
    }

    @Test
    void decodeMalformedRLPThrows() {
        byte[] malformed = new byte[] {0x01, 0x02, 0x03};
        assertThrows(Exception.class, () -> BlockHeaderExtensionV2.fromEncoded(malformed));
    }
}