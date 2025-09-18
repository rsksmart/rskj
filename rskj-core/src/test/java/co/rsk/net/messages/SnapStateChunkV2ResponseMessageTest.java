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

import co.rsk.trie.TrieChunk;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnapStateChunkV2ResponseMessageTest {

    @Test
    void testConstructorAndGetters() {
        long expectedId = 12345L;
        TrieChunk expectedChunk = createSimpleTrieChunk();
        
        SnapStateChunkV2ResponseMessage message = new SnapStateChunkV2ResponseMessage(expectedId, expectedChunk);
        
        assertEquals(expectedId, message.getId());
        assertEquals(expectedChunk, message.getChunk());
    }

    @Test
    void testGetMessageType() {
        SnapStateChunkV2ResponseMessage message = new SnapStateChunkV2ResponseMessage(1L, createSimpleTrieChunk());
        
        assertEquals(MessageType.SNAP_STATE_CHUNK_V2_RESPONSE_MESSAGE, message.getMessageType());
    }

    @Test
    void testGetEncodedMessageWithoutId() {
        TrieChunk chunk = createSimpleTrieChunk();
        SnapStateChunkV2ResponseMessage message = new SnapStateChunkV2ResponseMessage(123L, chunk);
        
        byte[] encoded = message.getEncodedMessageWithoutId();
        byte[] expectedEncoded = chunk.encode();
        
        assertArrayEquals(expectedEncoded, encoded);
    }

    @Test
    void testAcceptVisitor() {
        SnapStateChunkV2ResponseMessage message = new SnapStateChunkV2ResponseMessage(1L, createSimpleTrieChunk());
        MessageVisitor visitor = mock(MessageVisitor.class);
        
        message.accept(visitor);
        
        verify(visitor, times(1)).apply(message);
    }

    @Test
    void testDecodeMessage() {
        long expectedId = 987L;
        TrieChunk expectedChunk = createSimpleTrieChunk();
        
        // Create original message and encode it
        SnapStateChunkV2ResponseMessage originalMessage = new SnapStateChunkV2ResponseMessage(expectedId, expectedChunk);
        byte[] encodedWithoutId = originalMessage.getEncodedMessageWithoutId();
        byte[] encodedId = RLP.encodeBigInteger(java.math.BigInteger.valueOf(expectedId));
        
        // Create RLP list as it would appear in decodeMessage
        byte[] fullEncoded = RLP.encodeList(encodedId, encodedWithoutId);
        RLPList list = (RLPList) RLP.decode2(fullEncoded).get(0);
        
        // Decode and verify
        Message decodedMessage = SnapStateChunkV2ResponseMessage.decodeMessage(list);
        
        assertInstanceOf(SnapStateChunkV2ResponseMessage.class, decodedMessage);
        SnapStateChunkV2ResponseMessage responseMessage = (SnapStateChunkV2ResponseMessage) decodedMessage;
        
        assertEquals(expectedId, responseMessage.getId());
        assertNotNull(responseMessage.getChunk());
        assertEquals(expectedChunk.keyValues().size(), responseMessage.getChunk().keyValues().size());
    }

    @Test
    void testDecodeMessageWithNullId() {
        TrieChunk expectedChunk = createSimpleTrieChunk();
        
        byte[] encodedWithoutId = expectedChunk.encode();
        byte[] nullId = RLP.encodeElement(null);
        
        byte[] fullEncoded = RLP.encodeList(nullId, encodedWithoutId);
        RLPList list = (RLPList) RLP.decode2(fullEncoded).get(0);
        
        Message decodedMessage = SnapStateChunkV2ResponseMessage.decodeMessage(list);
        
        assertInstanceOf(SnapStateChunkV2ResponseMessage.class, decodedMessage);
        SnapStateChunkV2ResponseMessage responseMessage = (SnapStateChunkV2ResponseMessage) decodedMessage;
        
        assertEquals(0L, responseMessage.getId()); // null ID defaults to 0
        assertNotNull(responseMessage.getChunk());
    }

    @Test
    void testRoundTripEncodingDecoding() {
        long originalId = 777L;
        TrieChunk originalChunk = createComplexTrieChunk();
        
        SnapStateChunkV2ResponseMessage originalMessage = new SnapStateChunkV2ResponseMessage(originalId, originalChunk);
        
        // Encode
        byte[] encodedWithoutId = originalMessage.getEncodedMessageWithoutId();
        byte[] encodedId = RLP.encodeBigInteger(java.math.BigInteger.valueOf(originalId));
        byte[] fullEncoded = RLP.encodeList(encodedId, encodedWithoutId);
        RLPList list = (RLPList) RLP.decode2(fullEncoded).get(0);
        
        // Decode
        SnapStateChunkV2ResponseMessage decodedMessage = 
            (SnapStateChunkV2ResponseMessage) SnapStateChunkV2ResponseMessage.decodeMessage(list);
        
        // Verify round trip
        assertEquals(originalId, decodedMessage.getId());
        assertEquals(originalChunk.keyValues().size(), decodedMessage.getChunk().keyValues().size());
        assertEquals(originalChunk.proof().isEmpty(), decodedMessage.getChunk().proof().isEmpty());
    }

    @Test
    void testMessageWithEmptyChunk() {
        long id = 456L;
        TrieChunk emptyChunk = createEmptyTrieChunk();
        
        SnapStateChunkV2ResponseMessage message = new SnapStateChunkV2ResponseMessage(id, emptyChunk);
        
        assertEquals(id, message.getId());
        assertNotNull(message.getChunk());
        assertTrue(message.getChunk().keyValues().isEmpty());
        
        // Test encoding/decoding
        byte[] encoded = message.getEncodedMessageWithoutId();
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
    }

    @Test
    void testMessageWithLargeChunk() {
        long id = 999L;
        TrieChunk largeChunk = createLargeTrieChunk();
        
        SnapStateChunkV2ResponseMessage message = new SnapStateChunkV2ResponseMessage(id, largeChunk);
        
        assertEquals(id, message.getId());
        assertNotNull(message.getChunk());
        assertFalse(message.getChunk().keyValues().isEmpty());
        
        // Verify encoding works with large data
        byte[] encoded = message.getEncodedMessageWithoutId();
        assertNotNull(encoded);
        assertTrue(encoded.length > 1000); // Should be reasonably large
    }

    @Test
    void testMessageWithZeroId() {
        TrieChunk chunk = createSimpleTrieChunk();
        SnapStateChunkV2ResponseMessage message = new SnapStateChunkV2ResponseMessage(0L, chunk);
        
        assertEquals(0L, message.getId());
        assertEquals(chunk, message.getChunk());
    }

    @Test
    void testMessageWithNegativeId() {
        TrieChunk chunk = createSimpleTrieChunk();
        SnapStateChunkV2ResponseMessage message = new SnapStateChunkV2ResponseMessage(-1L, chunk);
        
        assertEquals(-1L, message.getId());
        assertEquals(chunk, message.getChunk());
    }

    @Test
    void testMessageWithMaxLongId() {
        TrieChunk chunk = createSimpleTrieChunk();
        SnapStateChunkV2ResponseMessage message = new SnapStateChunkV2ResponseMessage(Long.MAX_VALUE, chunk);
        
        assertEquals(Long.MAX_VALUE, message.getId());
        assertEquals(chunk, message.getChunk());
    }

    // Helper methods to create test TrieChunk objects

    private TrieChunk createSimpleTrieChunk() {
        LinkedHashMap<byte[], byte[]> keyValues = new LinkedHashMap<>();
        keyValues.put("key1".getBytes(), "value1".getBytes());
        keyValues.put("key2".getBytes(), "value2".getBytes());
        return new TrieChunk(keyValues, TrieChunk.Proof.EMPTY);
    }

    private TrieChunk createEmptyTrieChunk() {
        LinkedHashMap<byte[], byte[]> keyValues = new LinkedHashMap<>();
        return new TrieChunk(keyValues, TrieChunk.Proof.EMPTY);
    }

    private TrieChunk createComplexTrieChunk() {
        LinkedHashMap<byte[], byte[]> keyValues = new LinkedHashMap<>();
        keyValues.put("complexKey1".getBytes(), "complexValue1".getBytes());
        keyValues.put("complexKey2".getBytes(), "complexValue2".getBytes());
        keyValues.put("emptyKey".getBytes(), new byte[0]);
        keyValues.put(new byte[]{(byte) 0xFF, (byte) 0xEE}, "binaryValue".getBytes());
        return new TrieChunk(keyValues, TrieChunk.Proof.EMPTY);
    }

    private TrieChunk createLargeTrieChunk() {
        LinkedHashMap<byte[], byte[]> keyValues = new LinkedHashMap<>();
        
        // Create a chunk with many key-value pairs to test large data handling
        for (int i = 0; i < 50; i++) {
            String key = "largeKey" + i + "_".repeat(20); // Make keys reasonably long
            String value = "largeValue" + i + "_".repeat(30); // Make values reasonably long
            keyValues.put(key.getBytes(), value.getBytes());
        }
        
        return new TrieChunk(keyValues, TrieChunk.Proof.EMPTY);
    }
} 