/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
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
import co.rsk.util.NodeStopper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NodeReferenceTest {

    @Test
    void testGetNode_lazyNodeIsNotNull_returnsOptionalOfLazyNode() {
        // Given
        Trie lazyNodeMock = mock(Trie.class);

        NodeReference nodeReference = new NodeReference(null, lazyNodeMock, null);

        // When
        Optional<Trie> result = nodeReference.getNode();

        // Then
        assertTrue(result.isPresent());
        assertEquals(lazyNodeMock, result.get());
    }

    @Test
    void testGetNode_lazyNodeIsNullAndLazyHashIsNull_returnsOptionalEmpty() {
        // Given
        NodeReference nodeReference = new NodeReference(null, null, null);

        // When
        Optional<Trie> result = nodeReference.getNode();

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testGetNode_lazyNodeIsNullLazyHashIsNotNullAndTrieStoreCanRetrieveNode_returnsNode() {
        // Given
        Keccak256 lazyHashMock = mock(Keccak256.class);
        byte[] bytesMock = new byte[0];
        doReturn(bytesMock).when(lazyHashMock).getBytes();

        Trie trieMock = mock(Trie.class);

        TrieStoreImpl trieStoreMock = mock(TrieStoreImpl.class);
        doReturn(Optional.of(trieMock)).when(trieStoreMock).retrieve(bytesMock);

        NodeReference nodeReference = new NodeReference(trieStoreMock, null, lazyHashMock);

        // When
        Optional<Trie> result = nodeReference.getNode();

        // Then
        assertTrue(result.isPresent());
        assertEquals(trieMock, result.get());

        verify(lazyHashMock, times(1)).getBytes();
        verify(trieStoreMock, times(1)).retrieve(bytesMock);
    }

    @Test
    void testGetNode_brokenDatabase_stopMethodIsCalled() {
        // Given
        Keccak256 lazyHashMock = mock(Keccak256.class);
        byte[] bytesMock = new byte[0];
        doReturn(bytesMock).when(lazyHashMock).getBytes();

        int exitStatus = 1;
        NodeStopper nodeStopperMock = mock(NodeStopper.class);
        doNothing().when(nodeStopperMock).stop(exitStatus);

        TrieStoreImpl trieStoreMock = mock(TrieStoreImpl.class);
        doReturn(Optional.empty()).when(trieStoreMock).retrieve(bytesMock);

        NodeReference nodeReference = new NodeReference(trieStoreMock, null, lazyHashMock, nodeStopperMock);

        // When
        nodeReference.getNode();

        // Then
        verify(nodeStopperMock, times(1)).stop(exitStatus);
    }

    @Test
    void testFromRef_nullNodeReference_throwsIllegalArgumentException() {
        // Given
        NodeReference nullNodeRef = null;

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Trie.fromRef(nullNodeRef)
        );
        assertEquals("Node reference is empty or null", exception.getMessage());
    }

    @Test
    void testFromRef_emptyNodeReference_throwsIllegalArgumentException() {
        // Given
        NodeReference emptyNodeRef = NodeReference.empty();

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> Trie.fromRef(emptyNodeRef)
        );
        assertEquals("Node reference is empty or null", exception.getMessage());
    }

    @Test
    void testFromRef_nodeReferenceWithNoValidNode_throwsIllegalStateException() {
        // Given
        Keccak256 lazyHashMock = mock(Keccak256.class);
        byte[] bytesMock = new byte[0];
        doReturn(bytesMock).when(lazyHashMock).getBytes();

        TrieStoreImpl trieStoreMock = mock(TrieStoreImpl.class);
        doReturn(Optional.empty()).when(trieStoreMock).retrieve(bytesMock);

        NodeStopper nodeStopperMock = mock(NodeStopper.class);
        doNothing().when(nodeStopperMock).stop(anyInt());

        NodeReference nodeReference = new NodeReference(trieStoreMock, null, lazyHashMock, nodeStopperMock);

        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> Trie.fromRef(nodeReference)
        );
        assertEquals("Node reference does not point to a valid node", exception.getMessage());
    }

    @Test
    void testFromRef_validNodeReference_returnsTrie() {
        // Given
        Trie trieMock = mock(Trie.class);
        NodeReference nodeReference = new NodeReference(null, trieMock, null);

        // When
        Trie result = Trie.fromRef(nodeReference);

        // Then
        assertNotNull(result);
        assertEquals(trieMock, result);
    }

}
