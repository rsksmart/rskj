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

    // preRoots are all the root nodes on the left of the current node. They are used to validate the chunk.
    // When initializing the iterator, everytime we turn right, we add the node to the list.
    private final List<TrieDTO> preRootNodes = new ArrayList<>();

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

    private TrieDTO findByChildrenSize(long offset, TrieDTO nodeDTO, Deque<TrieDTO> visiting) {
        // TODO poner los nodos padres intermedios en el stack, tenemos que serializarlos para poder validar el chunk completo.
        if (!nodeDTO.isTerminal()) {

            if (isLeftNotEmbedded(nodeDTO)){
                TrieDTO left = getNode(nodeDTO.getLeftHash());

                if (left == null) {
                    throw new NullPointerException("Left node is null");
                }

                long maxLeftSize = left.getTotalSize();

                if (offset <= maxLeftSize) {
                    visiting.push(nodeDTO);
                    return findByChildrenSize(offset, left, visiting);
                }
                maxLeftSize += nodeDTO.getSize();
                if (offset <= maxLeftSize) {
                    return pushAndReturn(nodeDTO, visiting, (offset - left.getTotalSize()));
                }
            } else if (nodeDTO.isLeftNodePresent() && nodeDTO.isLeftNodeEmbedded() && (offset <= nodeDTO.getLeftSize())) {
                return pushAndReturn(nodeDTO, visiting, offset);
            }

            if (nodeDTO.isRightNodePresent() && !nodeDTO.isRightNodeEmbedded()) {
                TrieDTO right = getNode(nodeDTO.getRightHash());
                if (right == null) {
                    throw new NullPointerException("Right node is null.");
                }

                long maxParentSize = nodeDTO.getTotalSize() - right.getTotalSize();
                long maxRightSize = nodeDTO.getTotalSize();

                if (maxParentSize < offset && offset <= maxRightSize) {
                    preRootNodes.add(nodeDTO);
                    return findByChildrenSize(offset - maxParentSize, right, visiting);
                }
            } else if (nodeDTO.isRightNodeEmbedded() && (offset <= nodeDTO.getTotalSize())) {
                long leftAndParentSize = nodeDTO.getTotalSize() - nodeDTO.getRightSize();
                return pushAndReturn(nodeDTO, visiting, offset - leftAndParentSize);
            }
        }
        if (nodeDTO.getTotalSize() >= offset) {
            return pushAndReturn(nodeDTO, visiting, offset);
        } else {
            return nodeDTO;
        }
    }

    private boolean isLeftNotEmbedded(TrieDTO nodeDTO){
        return nodeDTO.isLeftNodePresent() && !nodeDTO.isLeftNodeEmbedded();
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
        TrieDTO result = this.visiting.peek();
        // if the node has a right child, its leftmost node is next
        long offset = getOffset(result);
        if (this.from + offset < this.to) {
            this.visiting.pop(); // remove the node from the stack
            if (result.getRightHash() != null) {
                TrieDTO rightNode = pushNode(result.getRightHash(), this.visiting);
                // find the leftmost node of the right child
                if (rightNode != null) {
                    pushLeftmostNode(rightNode);
                }
                // note "node" has been replaced on the stack by its right child
            }
        } // else: no right subtree, go back up the stack
        // next node on stack will be next returned
        this.from += offset;
        return result;
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
            if (leftNode != null) {
                pushLeftmostNode(leftNode); // recurse on next left node
            }
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

    public TrieDTO peek() {
        return this.visiting.peek();
    }

    public static long getOffset(TrieDTO visiting) {
        return visiting.isTerminal() ? visiting.getTotalSize() : visiting.getSize();
    }

    public List<TrieDTO> getNodesLeftVisiting() {
        return new ArrayList<>(visiting);
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

    public List<TrieDTO> getPreRootNodes() {
        return preRootNodes;
    }
}
