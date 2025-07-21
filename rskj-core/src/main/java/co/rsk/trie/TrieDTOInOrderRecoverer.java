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

import co.rsk.crypto.Keccak256;
import com.google.common.collect.Lists;
import org.ethereum.crypto.Keccak256Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class TrieDTOInOrderRecoverer {

    public record RecoverSubtreeResponse(Optional<TrieDTO> node, int index) {
    }

    private static final Logger logger = LoggerFactory.getLogger(TrieDTOInOrderRecoverer.class);

    private TrieDTOInOrderRecoverer() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Recover a Trie structure given a list of nodes.
     *
     * @param getNodeFnByIndex will retrieve the node by index given any source
     * @param trieCollectionSize will be size of all the nodes to be recovered
     * @param processTrieDTO will update the nodes in the source. This is because some hashes will be calculated for validation purposes.
     * @return
     */
    public static RecoverSubtreeResponse recoverTrie(Function<Integer, TrieDTO> getNodeFnByIndex, int trieCollectionSize, BiConsumer<? super TrieDTO, Integer> processTrieDTO) {
        final var response = recoverSubtree(getNodeFnByIndex, 0, trieCollectionSize - 1, processTrieDTO);
        final var result = response.node();
        result.ifPresent(node -> processTrieDTO.accept(node, response.index()));

        return response;
    }

    private static RecoverSubtreeResponse recoverSubtree(Function<Integer, TrieDTO> getNodeFnByIndex, int start, int end, BiConsumer<? super TrieDTO, Integer> processTrieDTO) {
        if (end - start < 0) {
            return new RecoverSubtreeResponse(Optional.empty(), -1);
        }
        if (end - start == 0) {
            final var recoveredNode = Optional.of(fromTrieDTO(getNodeFnByIndex.apply(start), Optional.empty(), Optional.empty()));

            return new RecoverSubtreeResponse(recoveredNode, start);
        }

        int indexRoot = findRoot(getNodeFnByIndex, start, end);

        final var leftResponse = recoverSubtree(getNodeFnByIndex, start, indexRoot - 1, processTrieDTO);
        final var left = leftResponse.node();
        left.ifPresent(node -> processTrieDTO.accept(node, leftResponse.index()));

        final var rightResponse = recoverSubtree(getNodeFnByIndex, indexRoot + 1, end, processTrieDTO);
        final var right = rightResponse.node();
        right.ifPresent(node -> processTrieDTO.accept(node, rightResponse.index()));

        final var recoveredNode = Optional.of(fromTrieDTO(getNodeFnByIndex.apply(indexRoot), left, right));

        return new RecoverSubtreeResponse(recoveredNode, indexRoot);
    }

    public static boolean verifyChunk(byte[] remoteRootHash, List<TrieDTO> preRootNodes, List<TrieDTO> nodes, List<TrieDTO> postRootNodes) {
        List<TrieDTO> allNodes = Lists.newArrayList(preRootNodes);
        allNodes.addAll(nodes);
        allNodes.addAll(postRootNodes);
        if (allNodes.isEmpty()) {
            logger.warn("Received empty chunk");
            return false;
        }
        TrieDTO[] nodeArray = allNodes.toArray(new TrieDTO[0]);
        final var response = TrieDTOInOrderRecoverer.recoverTrie(index -> nodeArray[index], nodeArray.length, (node, index) -> {
        });
        final var result = response.node();
        if (!result.isPresent() || !Arrays.equals(remoteRootHash, result.get().calculateHash())) {
            logger.warn("Root hash does not match! Calculated is present: {}", result.isPresent());
            return false;
        }
        logger.debug("Received chunk with correct trie.");
        return true;
    }

    private static int findRoot(TrieDTO[] trieCollection, int start, int end) {
        int max = start;
        for (int i = start; i <= end; i++) {
            if (getValue(trieCollection[i]) > getValue(trieCollection[max])) {
                max = i;
            }
        }
        return max;
    }

    private static int findRoot(Function<Integer, TrieDTO> getNodeFnByIndex, int start, int end) {
        int max = start;
        TrieDTO maxNode = null;

        for (int i = start; i <= end; i++) {
            final var node = getNodeFnByIndex.apply(i);

            if(maxNode == null) {
                maxNode = node;
            }

            if (getValue(node) > getValue(maxNode)) {
                max = i;
                maxNode = node;
            }
        }
        return max;
    }

    private static TrieDTO fromTrieDTO(TrieDTO result,
                                       Optional<TrieDTO> left,
                                       Optional<TrieDTO> right) {
        left.ifPresent(leftNode -> {
            try {
                Keccak256 hash = new Keccak256(Keccak256Helper.keccak256(leftNode.toMessage()));
                result.setLeftHash(hash.getBytes());
            } catch (Throwable e) {
                logger.error("Error recovering left node", e);
            }
        });
        right.ifPresent(rightNode -> {
            Keccak256 hash = new Keccak256(Keccak256Helper.keccak256(rightNode.toMessage()));
            result.setRightHash(hash.getBytes());
        });
        return result;
    }

    private static long getValue(TrieDTO trieCollection) {
        return trieCollection.getChildrenSize().value;
    }

}
