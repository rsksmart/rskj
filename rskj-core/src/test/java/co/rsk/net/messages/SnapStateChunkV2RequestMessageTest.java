/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

package co.rsk.net.messages;

import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnapStateChunkV2RequestMessageTest {

    @Test
    void testConstructorAndGetters() {
        long id = 123L;
        byte[] blockHash = new byte[]{0x01, 0x02, 0x03, 0x04};
        byte[] fromKey = new byte[]{0x05, 0x06, 0x07, 0x08};
        
        SnapStateChunkV2RequestMessage message = new SnapStateChunkV2RequestMessage(id, blockHash, fromKey);
        
        assertEquals(id, message.getId());
        assertArrayEquals(blockHash, message.getBlockHash());
        assertArrayEquals(fromKey, message.getFromKey());
    }

    @Test
    void testConstructorWithNullValues() {
        long id = 456L;
        byte[] blockHash = null;
        byte[] fromKey = null;
        
        SnapStateChunkV2RequestMessage message = new SnapStateChunkV2RequestMessage(id, blockHash, fromKey);
        
        assertEquals(id, message.getId());
        assertNull(message.getBlockHash());
        assertNull(message.getFromKey());
    }

    @Test
    void testConstructorWithEmptyArrays() {
        long id = 789L;
        byte[] blockHash = new byte[0];
        byte[] fromKey = new byte[0];
        
        SnapStateChunkV2RequestMessage message = new SnapStateChunkV2RequestMessage(id, blockHash, fromKey);
        
        assertEquals(id, message.getId());
        assertArrayEquals(blockHash, message.getBlockHash());
        assertArrayEquals(fromKey, message.getFromKey());
        assertEquals(0, message.getBlockHash().length);
        assertEquals(0, message.getFromKey().length);
    }

    @Test
    void testMessageType() {
        SnapStateChunkV2RequestMessage message = new SnapStateChunkV2RequestMessage(1L, new byte[4], new byte[4]);
        
        assertEquals(MessageType.SNAP_STATE_CHUNK_V2_REQUEST_MESSAGE, message.getMessageType());
    }

    @Test
    void testResponseMessageType() {
        SnapStateChunkV2RequestMessage message = new SnapStateChunkV2RequestMessage(1L, new byte[4], new byte[4]);
        
        assertEquals(MessageType.SNAP_STATE_CHUNK_V2_RESPONSE_MESSAGE, message.getResponseMessageType());
    }

    @Test
    void testGetEncodedMessageWithoutId() {
        byte[] blockHash = new byte[]{0x01, 0x02, 0x03, 0x04};
        byte[] fromKey = new byte[]{0x05, 0x06, 0x07, 0x08};
        SnapStateChunkV2RequestMessage message = new SnapStateChunkV2RequestMessage(1L, blockHash, fromKey);
        
        byte[] encoded = message.getEncodedMessageWithoutId();
        
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
        
        // Verify the encoding structure by decoding it
        RLPList decoded = (RLPList) RLP.decode2(encoded).get(0);
        assertEquals(2, decoded.size());
        assertArrayEquals(blockHash, decoded.get(0).getRLPData());
        assertArrayEquals(fromKey, decoded.get(1).getRLPData());
    }

    @Test
    void testGetEncodedMessageWithoutIdWithNullValues() {
        SnapStateChunkV2RequestMessage message = new SnapStateChunkV2RequestMessage(1L, null, null);
        
        byte[] encoded = message.getEncodedMessageWithoutId();
        
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
        
        // Verify the encoding structure
        RLPList decoded = (RLPList) RLP.decode2(encoded).get(0);
        assertEquals(2, decoded.size());
        assertNull(decoded.get(0).getRLPData());
        assertNull(decoded.get(1).getRLPData());
    }

    @Test
    void testAccept() {
        SnapStateChunkV2RequestMessage message = new SnapStateChunkV2RequestMessage(1L, new byte[4], new byte[4]);
        MessageVisitor visitor = mock(MessageVisitor.class);
        
        message.accept(visitor);
        
        verify(visitor, times(1)).apply(message);
    }

    @Test
    void testDecodeMessage() {
        long expectedId = 987L;
        byte[] expectedBlockHash = new byte[]{(byte) 0x10, (byte) 0x20, (byte) 0x30, (byte) 0x40};
        byte[] expectedFromKey = new byte[]{(byte) 0x50, (byte) 0x60, (byte) 0x70, (byte) 0x80};
        
        // Create RLP structure manually
        byte[] rlpId = RLP.encodeBigInteger(java.math.BigInteger.valueOf(expectedId));
        byte[] rlpBlockHash = RLP.encodeElement(expectedBlockHash);
        byte[] rlpFromKey = RLP.encodeElement(expectedFromKey);
        byte[] messageData = RLP.encodeList(rlpBlockHash, rlpFromKey);
        byte[] fullMessage = RLP.encodeList(rlpId, messageData);
        
        RLPList list = (RLPList) RLP.decode2(fullMessage).get(0);
        
        Message decodedMessage = SnapStateChunkV2RequestMessage.decodeMessage(list);
        
        assertTrue(decodedMessage instanceof SnapStateChunkV2RequestMessage);
        SnapStateChunkV2RequestMessage snapMessage = (SnapStateChunkV2RequestMessage) decodedMessage;
        
        assertEquals(expectedId, snapMessage.getId());
        assertArrayEquals(expectedBlockHash, snapMessage.getBlockHash());
        assertArrayEquals(expectedFromKey, snapMessage.getFromKey());
    }

    @Test
    void testDecodeMessageWithNullId() {
        byte[] expectedBlockHash = new byte[]{(byte) 0x10, (byte) 0x20, (byte) 0x30, (byte) 0x40};
        byte[] expectedFromKey = new byte[]{(byte) 0x50, (byte) 0x60, (byte) 0x70, (byte) 0x80};
        
        // Create RLP structure with null ID
        byte[] rlpId = RLP.encodeElement((byte[]) null);
        byte[] rlpBlockHash = RLP.encodeElement(expectedBlockHash);
        byte[] rlpFromKey = RLP.encodeElement(expectedFromKey);
        byte[] messageData = RLP.encodeList(rlpBlockHash, rlpFromKey);
        byte[] fullMessage = RLP.encodeList(rlpId, messageData);
        
        RLPList list = (RLPList) RLP.decode2(fullMessage).get(0);
        
        Message decodedMessage = SnapStateChunkV2RequestMessage.decodeMessage(list);
        
        assertTrue(decodedMessage instanceof SnapStateChunkV2RequestMessage);
        SnapStateChunkV2RequestMessage snapMessage = (SnapStateChunkV2RequestMessage) decodedMessage;
        
        assertEquals(0L, snapMessage.getId()); // Should default to 0 when null
        assertArrayEquals(expectedBlockHash, snapMessage.getBlockHash());
        assertArrayEquals(expectedFromKey, snapMessage.getFromKey());
    }

    @Test
    void testDecodeMessageWithNullData() {
        long expectedId = 555L;
        
        // Create RLP structure with null data
        byte[] rlpId = RLP.encodeBigInteger(java.math.BigInteger.valueOf(expectedId));
        byte[] rlpBlockHash = RLP.encodeElement((byte[]) null);
        byte[] rlpFromKey = RLP.encodeElement((byte[]) null);
        byte[] messageData = RLP.encodeList(rlpBlockHash, rlpFromKey);
        byte[] fullMessage = RLP.encodeList(rlpId, messageData);
        
        RLPList list = (RLPList) RLP.decode2(fullMessage).get(0);
        
        Message decodedMessage = SnapStateChunkV2RequestMessage.decodeMessage(list);
        
        assertTrue(decodedMessage instanceof SnapStateChunkV2RequestMessage);
        SnapStateChunkV2RequestMessage snapMessage = (SnapStateChunkV2RequestMessage) decodedMessage;
        
        assertEquals(expectedId, snapMessage.getId());
        assertNull(snapMessage.getBlockHash());
        assertNull(snapMessage.getFromKey());
    }

    @Test
    void testEncodeDecodeRoundTrip() {
        long originalId = 777L;
        byte[] originalBlockHash = new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD};
        byte[] originalFromKey = new byte[]{(byte) 0xEE, (byte) 0xFF, (byte) 0x00, (byte) 0x11};
        
        SnapStateChunkV2RequestMessage originalMessage = new SnapStateChunkV2RequestMessage(originalId, originalBlockHash, originalFromKey);
        
        // Encode the full message (including ID)
        byte[] encodedFull = originalMessage.getEncodedMessage();
        RLPList list = (RLPList) RLP.decode2(encodedFull).get(0);
        
        // Decode back
        Message decodedMessage = SnapStateChunkV2RequestMessage.decodeMessage(list);
        
        assertTrue(decodedMessage instanceof SnapStateChunkV2RequestMessage);
        SnapStateChunkV2RequestMessage decodedSnapMessage = (SnapStateChunkV2RequestMessage) decodedMessage;
        
        assertEquals(originalMessage.getId(), decodedSnapMessage.getId());
        assertArrayEquals(originalMessage.getBlockHash(), decodedSnapMessage.getBlockHash());
        assertArrayEquals(originalMessage.getFromKey(), decodedSnapMessage.getFromKey());
        assertEquals(originalMessage.getMessageType(), decodedSnapMessage.getMessageType());
    }

    @Test
    void testLargeData() {
        long id = 999L;
        byte[] largeBlockHash = new byte[1024];
        byte[] largeFromKey = new byte[2048];
        
        // Fill with some pattern
        Arrays.fill(largeBlockHash, (byte) 0xAB);
        Arrays.fill(largeFromKey, (byte) 0xCD);
        
        SnapStateChunkV2RequestMessage message = new SnapStateChunkV2RequestMessage(id, largeBlockHash, largeFromKey);
        
        assertEquals(id, message.getId());
        assertArrayEquals(largeBlockHash, message.getBlockHash());
        assertArrayEquals(largeFromKey, message.getFromKey());
        
        // Test encoding doesn't fail with large data
        byte[] encoded = message.getEncodedMessageWithoutId();
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
    }

    @Test
    void testZeroId() {
        long zeroId = 0L;
        byte[] blockHash = new byte[]{0x01};
        byte[] fromKey = new byte[]{0x02};
        
        SnapStateChunkV2RequestMessage message = new SnapStateChunkV2RequestMessage(zeroId, blockHash, fromKey);
        
        assertEquals(zeroId, message.getId());
        assertArrayEquals(blockHash, message.getBlockHash());
        assertArrayEquals(fromKey, message.getFromKey());
    }

    @Test
    void testNegativeId() {
        long negativeId = -123L;
        byte[] blockHash = new byte[]{0x01};
        byte[] fromKey = new byte[]{0x02};
        
        SnapStateChunkV2RequestMessage message = new SnapStateChunkV2RequestMessage(negativeId, blockHash, fromKey);
        
        assertEquals(negativeId, message.getId());
        assertArrayEquals(blockHash, message.getBlockHash());
        assertArrayEquals(fromKey, message.getFromKey());
    }
} 