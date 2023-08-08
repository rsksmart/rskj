/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

import java.util.*;

public class TrieDTOInOrderIterator implements Iterator<TrieDTO> {

    private final Deque<TrieDTO> visiting;
    private final TrieStore ds;


    public TrieDTOInOrderIterator(TrieStore ds, byte[] root) {
        this(ds, root, 0L);
    }

    public TrieDTOInOrderIterator(TrieStore ds, byte[] root, long initialChildrenSize) {
        Objects.requireNonNull(root);
        this.ds = ds;
        this.visiting = new LinkedList<>();
        // find the leftmost node, pushing all the intermediate nodes onto the visiting stack
        TrieDTO nodeDTO = pushNode(root, visiting);
        findByChildrenSize(initialChildrenSize, nodeDTO, visiting);
        // now the leftmost unvisited node is on top of the visiting stack
    }

    private TrieDTO findByChildrenSize(long childrenSize, TrieDTO nodeDTO, Deque<TrieDTO> visiting) {
        if (!nodeDTO.isTerminal()) {
            if (nodeDTO.getLeftHash() != null) {
                TrieDTO left = getNode(nodeDTO.getLeftHash());
                if (childrenSize < left.getTotalSize()) {
                    visiting.push(left);
                    return findByChildrenSize(childrenSize, left, visiting);
                } else {
                    final long leftAndParent = left.getTotalSize() + nodeDTO.getSize();
                    if (childrenSize <= leftAndParent) {
                        return this.next();
                    } else if (nodeDTO.getRightHash() != null) {
                        this.visiting.pop(); // Remove parent when going right
                        TrieDTO right = getNode(nodeDTO.getRightHash());
                        final long maxSize = leftAndParent + right.getTotalSize();
                        if (childrenSize < maxSize) {
                            visiting.push(right);
                            return findByChildrenSize(childrenSize - leftAndParent, right, visiting);
                        }
                        throw new RuntimeException("Invalid ChildrenSize " + childrenSize + ". Bigger than the size of the trie:" + maxSize);
                    }
                }
            }
        }
        return nodeDTO;
    }

    /**
     * return the leftmost node that has not yet been visited that node is normally on top of the stack
     */
    @Override
    @SuppressWarnings("squid:S2272") // NoSuchElementException is thrown by {@link Deque#pop()} when it's empty
    public TrieDTO next() {
        TrieDTO visiting = this.visiting.pop();
        // if the node has a right child, its leftmost node is next
        if (visiting.getRightHash() != null) {
            TrieDTO rightNode = pushNode(visiting.getRightHash(), this.visiting);

            // find the leftmost node of the right child
            pushLeftmostNode(rightNode);
            // note "node" has been replaced on the stack by its right child
        } // else: no right subtree, go back up the stack
        // next node on stack will be next returned
        return visiting;
    }

    @Override
    public boolean hasNext() {
        return !visiting.isEmpty(); // no next node left
    }

    /**
     * Find the leftmost node from this root, pushing all the intermediate nodes onto the visiting stack
     *
     * @param nodeKey the root of the subtree for which we are trying to reach the leftmost node
     */
    private void pushLeftmostNode(TrieDTO nodeKey) {
        // find the leftmost node
        if (nodeKey.getLeftHash() != null) {
            TrieDTO leftNode = pushNode(nodeKey.getLeftHash(), visiting);
            pushLeftmostNode(leftNode); // recurse on next left node
        }
    }

    private TrieDTO pushNode(byte[] root, Deque<TrieDTO> visiting) {
        final TrieDTO result = getNode(root);
        if (result != null) {
            visiting.push(result);
        }
        return result;
    }

    private TrieDTO getNode(byte[] hash) {
        if (hash != null) {
            byte[] node = this.ds.retrieveValue(hash);
            return TrieDTO.decodeFromMessage(node, this.ds);
        } else {
            return null;
        }
    }
}
