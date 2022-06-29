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

import co.rsk.core.RskAddress;
import org.ethereum.db.TrieKeyMapper;

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;

public class PreOrderIterator implements Iterator<IterationElement> {

        private final Deque<IterationElement> visiting;
        private boolean stopAtAccountDepth;

        public PreOrderIterator(Trie root, boolean stopAtAccountDepth) {
            Objects.requireNonNull(root);
            this.stopAtAccountDepth = stopAtAccountDepth;
            TrieKeySlice traversedPath = root.getSharedPath();
            this.visiting = new LinkedList<>();
            this.visiting.push(new IterationElement(traversedPath, root));
        }

        @Override
        @SuppressWarnings("squid:S2272") // NoSuchElementException is thrown by {@link Deque#pop()} when it's empty
        public IterationElement next() {
            IterationElement visitingElement = visiting.pop();
            Trie node = visitingElement.getNode();
            TrieKeySlice nodeKey = visitingElement.getNodeKey();

            int nodeKeyLength = nodeKey.length();
            // If we're stoping at accounts, do not add children
            if ((!stopAtAccountDepth) || (nodeKeyLength < (1 + TrieKeyMapper.SECURE_KEY_SIZE + RskAddress.LENGTH_IN_BYTES) * Byte.SIZE))
            {
                // need to visit the left subtree first, then the right since a stack is a LIFO, push the right subtree first,
                // then the left
                Trie rightNode = node.retrieveNode((byte) 0x01);
                if (rightNode != null) {
                    TrieKeySlice rightNodeKey = nodeKey.rebuildSharedPath((byte) 0x01, rightNode.getSharedPath());
                    visiting.push(new IterationElement(rightNodeKey, rightNode));
                }
                Trie leftNode = node.retrieveNode((byte) 0x00);
                if (leftNode != null) {
                    TrieKeySlice leftNodeKey = nodeKey.rebuildSharedPath((byte) 0x00, leftNode.getSharedPath());
                    visiting.push(new IterationElement(leftNodeKey, leftNode));
                }
            }
            // may not have pushed anything.  If so, we are at the end
            return visitingElement;
        }

        @Override
        public boolean hasNext() {
            return !visiting.isEmpty(); // no next node left
        }

}