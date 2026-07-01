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

package co.rsk.trie;

import co.rsk.crypto.Keccak256;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the bootstrap state-export OOM (dominant sink).
 *
 * <p>The in-order iterator descends from a caller-held root through {@link Trie#retrieveNode}, which
 * memoizes every loaded child into its parent {@link NodeReference#getNode()}. Because the root is
 * pinned for the whole walk, the default (caching) traversal retains the entire visited trie in heap
 * — O(state size), which OOMs the exporter on large state.
 *
 * <p>The non-caching traversal ({@code getInOrderIterator(false)}) must visit the <b>exact same</b>
 * nodes in the <b>exact same</b> order (so the serialized export stays byte-identical) while NOT
 * memoizing children into the held root (so the visited sub-trie is collectable and memory stays
 * bounded to the active path).
 */
class InOrderIteratorCachingTest {

    private TrieStore store;

    @BeforeEach
    void setUp() {
        this.store = new TrieStoreImpl(new HashMapDB());
    }

    @Test
    void nonCachingTraversalVisitsSameNodesInSameOrderAsCaching() {
        Keccak256 rootHash = buildAndStoreTrie();

        List<Keccak256> caching = collectVisitedHashes(retrieveRoot(rootHash).getInOrderIterator(true));
        List<Keccak256> nonCaching = collectVisitedHashes(retrieveRoot(rootHash).getInOrderIterator(false));

        assertTrue(caching.size() > 1, "test trie must have more than one node to be meaningful");
        assertEquals(caching, nonCaching, "non-caching traversal must yield an identical node sequence");
    }

    @Test
    void cachingTraversalPinsVisitedTrieToTheHeldRoot() {
        Keccak256 rootHash = buildAndStoreTrie();
        Trie root = retrieveRoot(rootHash);

        // Precondition: a freshly retrieved root has non-embedded children that are not yet loaded,
        // otherwise wasLoaded() below would not distinguish the two traversal modes.
        assertFalse(root.getLeft().wasLoaded() && root.getRight().wasLoaded(),
                "freshly retrieved root must have at least one lazy (non-embedded, unloaded) child");

        drain(root.getInOrderIterator(true));

        // The caching traversal memoizes the children back into the held root: the whole visited
        // sub-trie is now reachable from (and pinned by) the root. This is the leak being fixed.
        assertTrue(root.getLeft().wasLoaded() || root.getRight().wasLoaded(),
                "caching traversal is expected to memoize loaded children into the held root");
    }

    @Test
    void nonCachingTraversalDoesNotPinVisitedTrieToTheHeldRoot() {
        Keccak256 rootHash = buildAndStoreTrie();
        Trie root = retrieveRoot(rootHash);

        assertFalse(root.getLeft().wasLoaded(), "left child must start unloaded");
        assertFalse(root.getRight().wasLoaded(), "right child must start unloaded");

        drain(root.getInOrderIterator(false));

        // The fix: after a full non-caching walk, the held root never memoized its children, so the
        // visited sub-trie is not retained through it and memory stays bounded to the active path.
        assertFalse(root.getLeft().wasLoaded(),
                "non-caching traversal must not memoize the left child into the held root");
        assertFalse(root.getRight().wasLoaded(),
                "non-caching traversal must not memoize the right child into the held root");
    }

    private Trie retrieveRoot(Keccak256 rootHash) {
        return store.retrieve(rootHash.getBytes()).orElseThrow(AssertionError::new);
    }

    /**
     * Builds a trie wide and deep enough that the root is a branch node whose left and right children
     * are themselves non-embeddable stored nodes (so they are lazy references after a fresh retrieve).
     */
    private Keccak256 buildAndStoreTrie() {
        Trie trie = new Trie(store);
        for (int i = 0; i < 256; i++) {
            byte[] key = Keccak256Helper.keccak256(new byte[]{(byte) i, (byte) (i >> 8)});
            byte[] value = new byte[40]; // long value -> upper nodes stay non-embeddable
            value[0] = (byte) i;
            trie = trie.put(key, value);
        }
        store.save(trie);
        return trie.getHash();
    }

    private static List<Keccak256> collectVisitedHashes(Iterator<IterationElement> it) {
        List<Keccak256> hashes = new ArrayList<>();
        while (it.hasNext()) {
            hashes.add(it.next().getNode().getHash());
        }
        return hashes;
    }

    private static void drain(Iterator<IterationElement> it) {
        while (it.hasNext()) {
            it.next();
        }
    }
}
