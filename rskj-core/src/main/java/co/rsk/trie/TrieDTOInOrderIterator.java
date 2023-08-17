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

    private long from;
    private final long to;
    private final boolean preloadChildren;

    public TrieDTOInOrderIterator(TrieStore ds, byte[] root, long from, long to) {
        this(ds, root, from, to, false);
    }

    public TrieDTOInOrderIterator(TrieStore ds, byte[] root, long from, long to, boolean preloadChildren) {
        Objects.requireNonNull(root);
        this.ds = ds;
        this.visiting = new LinkedList<>();
        this.from = from;
        this.to = to;
        this.preloadChildren = preloadChildren;
        // find the leftmost node, pushing all the intermediate nodes onto the visiting stack
        findByChildrenSize(from, getNode(root), visiting);
        // now the leftmost unvisited node is on top of the visiting stack
    }

    private TrieDTO findByChildrenSize(long childrenSize, TrieDTO nodeDTO, Deque<TrieDTO> visiting) {
        if (!nodeDTO.isTerminal()) {

            if (nodeDTO.isLeftNodeEmbedded() && nodeDTO.isRightNodeEmbedded()) {
                return pushAndReturn(nodeDTO, visiting, childrenSize);
            }

            if (nodeDTO.isLeftNodePresent() && !nodeDTO.isLeftNodeEmbedded()) {
                TrieDTO left = getNode(nodeDTO.getLeftHash());
                long maxLeftSize = left.getTotalSize();
                if (childrenSize <= maxLeftSize) {
                    visiting.push(nodeDTO);
                    return findByChildrenSize(childrenSize, left, visiting);
                }
                maxLeftSize += nodeDTO.getSize();
                if (childrenSize <= maxLeftSize) {
                    return pushAndReturn(nodeDTO, visiting, (childrenSize - left.getTotalSize()));
                }
            }else if (nodeDTO.isLeftNodePresent() && nodeDTO.isLeftNodeEmbedded()) {
                if (childrenSize <= nodeDTO.getLeftSize()) {
                    return pushAndReturn(nodeDTO, visiting, childrenSize);
                }
            }

            if (nodeDTO.isRightNodePresent() && !nodeDTO.isRightNodeEmbedded()) {
                TrieDTO right = getNode(nodeDTO.getRightHash());
                long maxParentSize = nodeDTO.getTotalSize() - right.getTotalSize();
                long maxRightSize = nodeDTO.getTotalSize();
                if ( maxParentSize < childrenSize && childrenSize <= maxRightSize) {
                    //System.out.println("remove:" + maxParentSize);
                    return findByChildrenSize(childrenSize - maxParentSize, right, visiting);
                }
            } else if(nodeDTO.isRightNodeEmbedded()) {
                if (childrenSize <= nodeDTO.getTotalSize()) {
                    long leftAndParentSize = nodeDTO.getTotalSize() - nodeDTO.getRightSize();
                    final long offset = childrenSize - leftAndParentSize;
                    return pushAndReturn(nodeDTO, visiting, offset);
                }
            }
        }
        if (nodeDTO.getTotalSize() >= childrenSize) {
            return pushAndReturn(nodeDTO, visiting, childrenSize);
        } else {
            return nodeDTO;
        }
    }

    private TrieDTO pushAndReturn(TrieDTO nodeDTO, Deque<TrieDTO> visiting, long offset) {
        this.from -= offset;
        visiting.push(nodeDTO);
        return nodeDTO;
    }

    /**
     * return the leftmost node that has not yet been visited that node is normally on top of the stack
     */
    @Override
    @SuppressWarnings("squid:S2272") // NoSuchElementException is thrown by {@link Deque#pop()} when it's empty
    public TrieDTO next() {
        TrieDTO result = this.visiting.pop();
        // if the node has a right child, its leftmost node is next
        if (result.getRightHash() != null) {
            TrieDTO rightNode = pushNode(result.getRightHash(), this.visiting);

            // find the leftmost node of the right child
            pushLeftmostNode(rightNode);
            // note "node" has been replaced on the stack by its right child
        } // else: no right subtree, go back up the stack
        // next node on stack will be next returned
        this.from += getOffset(result);
        return result;
    }

    public static long getOffset(TrieDTO visiting) {
        return visiting.isTerminal() ? visiting.getTotalSize() : visiting.getSize();
    }

    @Override
    public boolean hasNext() {
        return this.from < this.to && !visiting.isEmpty();
    }
    public boolean isEmpty() {
        return visiting.isEmpty();
    }

    public long getFrom() {
        return from;
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
            return TrieDTO.decodeFromMessage(node, this.ds, this.preloadChildren, hash);
        } else {
            return null;
        }
    }
}
