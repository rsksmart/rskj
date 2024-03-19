/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

package co.rsk.trie;

import co.rsk.crypto.Keccak256;
import org.ethereum.TestUtils;
import org.ethereum.datasource.HashMapDB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.bouncycastle.util.encoders.Hex.decode;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TrieDTOTest {


    private HashMapDB map;
    private TrieStore trieStore;

    @BeforeEach
    void setUp() {
        this.map = new HashMapDB();
        this.trieStore = new TrieStoreImpl(map);
    }

    @Test
    void testDecodeDto() {
        Trie trie = new Trie(trieStore)
                .put("foo", "bar".getBytes());
        Keccak256 hash = trie.getHash();
        trieStore.save(trie);

        Optional<TrieDTO> optTrieDTO = trieStore.retrieveDTO(hash.getBytes());

        assertTrue(optTrieDTO.isPresent());
        TrieDTO trieDTO = optTrieDTO.get();
        assertArrayEquals(trie.getValue(), trieDTO.getValue());
        String trieDtoDescription = trieDTO.toDescription();
        assertNotNull(trieDtoDescription);
        assertNotNull(trieDTO.toString());
    }

    @Test
    void testDecodeFromMessage() {
        Trie trie = new Trie(trieStore).put("foo", "bar".getBytes());
        trieStore.save(trie);
        byte[] message = trie.toMessage();
        TrieDTO decodedTrieDTO = TrieDTO.decodeFromMessage(message, trieStore);

        assertArrayEquals(trie.getValue(), decodedTrieDTO.getValue());
    }

    @Test
    void testGetSideHash() {
        Trie trie = buildTestTrie();
        TrieDTO trieDTO = TrieDTO.decodeFromMessage(trie.toMessage(), trieStore, true, null);
        byte[] leftHash = trieDTO.getLeftHash();
        byte[] rightHash = trieDTO.getRightHash();
        assertEquals(trie.getLeft().getHash().get(), new Keccak256(leftHash));
        assertEquals(trie.getRight().getHash().get(), new Keccak256(rightHash));
    }


    @Test
    void testMessageDecoding() {
        Trie trie = new Trie(trieStore).put("foo", "bar".getBytes());
        Keccak256 hash = trie.getHash();
        trieStore.save(trie);
        byte[] message = trie.toMessage();
        TrieDTO decodedTrieDTO = TrieDTO.decodeFromMessage(message, trieStore);
        TrieDTO retrievedDto = trieStore.retrieveDTO(hash.getBytes()).get();

        assertEquals(decodedTrieDTO, retrievedDto);
        assertNotEquals(decodedTrieDTO.hashCode(), retrievedDto.hashCode());
    }

    @Test
    void testMessageEncoding() {
        Trie trie = new Trie(trieStore)
                .put("foo", "bar".getBytes())
                .put("abc", "bc".getBytes())
                .put("def", "ef".getBytes());
        Keccak256 hash = trie.getHash();
        trieStore.save(trie);
        TrieDTO retrievedDto = trieStore.retrieveDTO(hash.getBytes()).get();
        byte[] message = retrievedDto.toMessage();
        TrieDTO decodedTrieDTO = TrieDTO.decodeFromMessage(message, trieStore);
        assertEquals(retrievedDto, decodedTrieDTO);
    }

    @Test
    void retrieveWithEmbedded() {
        Trie trie = new Trie(trieStore)
                .put("bar", "foo".getBytes())
                .put("foo", "bar".getBytes());

        trieStore.save(trie);
        TrieDTO trieDTO = TrieDTO.decodeFromMessage(trie.toMessage(), trieStore, true, null);
        assertNotNull(trieDTO);
        assertTrue(trieDTO.isTerminal());
        assertTrue(trieDTO.isLeftNodeEmbedded());
        assertTrue(trieDTO.isRightNodeEmbedded());

        assertTrue(trieDTO.isLeftNodePresent());
        assertTrue(trieDTO.isRightNodePresent());

        TrieDTO rightNode = trieDTO.getRightNode();
        assertArrayEquals("bar".getBytes(), rightNode.getValue());
        TrieDTO leftNode = trieDTO.getLeftNode();
        assertArrayEquals("foo".getBytes(), leftNode.getValue());
        assertTrue(trieDTO.isSharedPrefixPresent());
    }

    @Test
    void testBasicSetters() {
        Trie trie = new Trie(trieStore)
                .put("foo", "bar".getBytes());

        TrieDTO trieDTO = TrieDTO.decodeFromMessage(trie.toMessage(), trieStore);
        byte[] lBytes = TestUtils.generateBytes("left", 32);
        trieDTO.setLeft(lBytes);
        assertArrayEquals(lBytes, trieDTO.getLeft());
        byte[] rBytes = TestUtils.generateBytes("right", 32);
        trieDTO.setRight(rBytes);
        assertArrayEquals(rBytes, trieDTO.getRight());



        trie = buildTestTrie();
        trieDTO = TrieDTO.decodeFromMessage(trie.toMessage(), trieStore, true, null);
        byte[] leftHash = TestUtils.generateBytes("leftHash", 32);
        trieDTO.setLeftHash(leftHash);
        assertEquals(leftHash, trieDTO.getLeftHash());
        byte[] rightHash = TestUtils.generateBytes("rightHash", 32);
        trieDTO.setRightHash(rightHash);
        assertEquals(rightHash, trieDTO.getRightHash());
    }

    @Test
    void testLongValue() {
        Trie trie = new Trie(trieStore)
                .put("foo", TrieValueTest.makeValue(200));
        Keccak256 hash = trie.getHash();
        trieStore.save(trie);

        TrieDTO trieDTO = trieStore.retrieveDTO(hash.getBytes()).get();
        assertTrue(trieDTO.isHasLongVal());
    }


    private Trie buildTestTrie() {
        Trie trie = new Trie();
        trie = trie.put(decode("0a"), new byte[]{0x06});
        trie = trie.put(decode("0a00"), new byte[]{0x02});
        trie = trie.put(decode("0a80"), new byte[]{0x07});
        trie = trie.put(decode("0a0000"), new byte[]{0x01});
        trie = trie.put(decode("0a8 080"), new byte[]{0x08});
        return trie;
    }
}