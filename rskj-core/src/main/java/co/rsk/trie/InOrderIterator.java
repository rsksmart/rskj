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

public class InOrderIterator implements Iterator<IterationElement> {

    private final Deque<IterationElement> visiting;

    public InOrderIterator(Trie root) {
        Objects.requireNonNull(root);
        TrieKeySlice traversedPath = root.getSharedPath();
        this.visiting = new LinkedList<>();
        // find the leftmost node, pushing all the intermediate nodes onto the visiting stack
        visiting.push(new IterationElement(traversedPath, root));
        pushLeftmostNode(traversedPath, root);
        // now the leftmost unvisited node is on top of the visiting stack
    }

    /**
     * return the leftmost node that has not yet been visited that node is normally on top of the stack
     */
    @Override
    @SuppressWarnings("squid:S2272") // NoSuchElementException is thrown by {@link Deque#pop()} when it's empty
    public IterationElement next() {
        IterationElement visitingElement = visiting.pop();
        Trie node = visitingElement.getNode();
        // if the node has a right child, its leftmost node is next
        Trie rightNode = node.retrieveNode((byte) 0x01);
        if (rightNode != null) {
            TrieKeySlice rightNodeKey = visitingElement.getNodeKey().rebuildSharedPath((byte) 0x01, rightNode.getSharedPath());
            visiting.push(new IterationElement(rightNodeKey, rightNode)); // push the right node
            // find the leftmost node of the right child
            pushLeftmostNode(rightNodeKey, rightNode);
            // note "node" has been replaced on the stack by its right child
        } // else: no right subtree, go back up the stack
        // next node on stack will be next returned
        return visitingElement;
    }

    @Override
    public boolean hasNext() {
        return !visiting.isEmpty(); // no next node left
    }

    /**
     * Find the leftmost node from this root, pushing all the intermediate nodes onto the visiting stack
     *
     * @param nodeKey
     * @param node the root of the subtree for which we are trying to reach the leftmost node
     */
    private void pushLeftmostNode(TrieKeySlice nodeKey, Trie node) {
        // find the leftmost node
        Trie leftNode = node.retrieveNode((byte) 0x00);
        if (leftNode != null) {
            TrieKeySlice leftNodeKey = nodeKey.rebuildSharedPath((byte) 0x00, leftNode.getSharedPath());
            visiting.push(new IterationElement(leftNodeKey, leftNode)); // push the left node
            pushLeftmostNode(leftNodeKey, leftNode); // recurse on next left node
        }
    }
}
