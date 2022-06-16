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

public class PostOrderIterator implements Iterator<IterationElement> {

    private final Deque<IterationElement> visiting;
    private final Deque<Boolean> visitingRightChild;

    public PostOrderIterator(Trie root) {
        Objects.requireNonNull(root);
        TrieKeySlice traversedPath = root.getSharedPath();
        this.visiting = new LinkedList<>();
        this.visitingRightChild = new LinkedList<>();
        // find the leftmost node, pushing all the intermediate nodes onto the visiting stack
        visiting.push(new IterationElement(traversedPath, root));
        visitingRightChild.push(Boolean.FALSE);
        pushLeftmostNodeRecord(traversedPath, root);
        // the node on top of the visiting stack is the next one to be visited, unless it has a right subtree
    }

    @Override
    public boolean hasNext() {
        return !visiting.isEmpty(); // no next node left
    }

    @Override
    @SuppressWarnings("squid:S2272") // NoSuchElementException is thrown by {@link Deque#element()} when it's empty
    public IterationElement next() {
        IterationElement visitingElement = visiting.element();
        Trie node = visitingElement.getNode();
        Trie rightNode = node.retrieveNode((byte) 0x01);
        if (rightNode == null || visitingRightChild.peek()) { // no right subtree, or right subtree already visited
            // already visited right child, time to visit the node on top
            visiting.removeFirst(); // it was already picked
            visitingRightChild.removeFirst(); // it was already picked
            return visitingElement;
        } else { // now visit this node's right subtree
            // mark that we're visiting this element's right subtree
            visitingRightChild.removeFirst();
            visitingRightChild.push(Boolean.TRUE);

            TrieKeySlice rightNodeKey = visitingElement.getNodeKey().rebuildSharedPath((byte) 0x01, rightNode.getSharedPath());
            visiting.push(new IterationElement(rightNodeKey, rightNode)); // push the right node
            visitingRightChild.push(Boolean.FALSE); // we're visiting the left subtree of the right node

            // now push everything down to the leftmost node in the right subtree
            pushLeftmostNodeRecord(rightNodeKey, rightNode);
            return next(); // use recursive call to visit that node
        }
    }

    /**
     * Find the leftmost node from this root, pushing all the intermediate nodes onto the visiting stack
     * and also stating that each is a left child of its parent
     * @param nodeKey
     * @param node the root of the subtree for which we are trying to reach the leftmost node
     */
    private void pushLeftmostNodeRecord(TrieKeySlice nodeKey, Trie node) {
        // find the leftmost node
        Trie leftNode = node.retrieveNode((byte) 0x00);
        if (leftNode != null) {
            TrieKeySlice leftNodeKey = nodeKey.rebuildSharedPath((byte) 0x00, leftNode.getSharedPath());
            visiting.push(new IterationElement(leftNodeKey, leftNode)); // push the left node
            visitingRightChild.push(Boolean.FALSE); // record that it is on the left
            pushLeftmostNodeRecord(leftNodeKey, leftNode); // continue looping
        }
    }
}

