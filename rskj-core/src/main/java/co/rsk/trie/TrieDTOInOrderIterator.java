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

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;

public class TrieDTOInOrderIterator implements Iterator<TrieDTO> {

    private final Deque<TrieDTO> visiting;
    private final TrieStore ds;

    public TrieDTOInOrderIterator(TrieStore ds, byte[] root) {
        Objects.requireNonNull(root);
        this.ds = ds;
        this.visiting = new LinkedList<>();
        // find the leftmost node, pushing all the intermediate nodes onto the visiting stack
        byte[] node = this.ds.retrieveValue(root);
        TrieDTO nodeDTO = TrieDTO.decode(node, this.ds);
        visiting.push(nodeDTO);
        pushLeftmostNode(nodeDTO);
        // now the leftmost unvisited node is on top of the visiting stack
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
            byte[] rightSrc = this.ds.retrieveValue(visiting.getRightHash());
            TrieDTO rightNode = TrieDTO.decode(rightSrc, this.ds);
            //TrieKeySlice rightNodeKey = visiting.getNodeKey().rebuildSharedPath((byte) 0x01, rightNode.getSharedPath());
            this.visiting.push(rightNode); // push the right node

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
     * @param nodeKey  the root of the subtree for which we are trying to reach the leftmost node
     */
    private void pushLeftmostNode(TrieDTO nodeKey) {
        // find the leftmost node
        if (nodeKey.getLeftHash() != null) {
            byte[] leftSrc = this.ds.retrieveValue(nodeKey.getLeftHash());
            TrieDTO leftNode = TrieDTO.decode(leftSrc, this.ds);
            //TrieKeySlice leftNodeKey = nodeKey.rebuildSharedPath((byte) 0x00, leftNode.getSharedPath());
            visiting.push(leftNode); // push the left node
            pushLeftmostNode(leftNode); // recurse on next left node
        }
    }
}
