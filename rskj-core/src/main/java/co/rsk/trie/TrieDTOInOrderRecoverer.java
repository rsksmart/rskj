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
import java.util.function.Consumer;

public class TrieDTOInOrderRecoverer {

    private static final Logger logger = LoggerFactory.getLogger(TrieDTOInOrderRecoverer.class);
    private TrieDTOInOrderRecoverer() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Optional<TrieDTO> recoverTrie(TrieDTO[] trieCollection, Consumer<? super TrieDTO> processTrieDTO) {
        Optional<TrieDTO> result = recoverSubtree(trieCollection, 0, trieCollection.length - 1, processTrieDTO);
        result.ifPresent(processTrieDTO);
        return result;
    }

    private static Optional<TrieDTO> recoverSubtree(TrieDTO[] trieCollection, int start, int end, Consumer<? super TrieDTO> processTrieDTO) {
        if (end - start < 0) {
            return Optional.empty();
        }
        if (end - start == 0) {
            return Optional.of(fromTrieDTO(trieCollection[start], Optional.empty(), Optional.empty()));
        }
        int indexRoot = findRoot(trieCollection, start, end);
        Optional<TrieDTO> left = recoverSubtree(trieCollection, start, indexRoot - 1, processTrieDTO);
        left.ifPresent(processTrieDTO);
        Optional<TrieDTO> right = recoverSubtree(trieCollection, indexRoot + 1, end, processTrieDTO);
        right.ifPresent(processTrieDTO);
        return Optional.of(fromTrieDTO(trieCollection[indexRoot], left, right));
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
        Optional<TrieDTO> result = TrieDTOInOrderRecoverer.recoverTrie(nodeArray, (t) -> {
        });
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

    private static TrieDTO fromTrieDTO(TrieDTO result,
                                       Optional<TrieDTO> left,
                                       Optional<TrieDTO> right) {
        left.ifPresent((leftNode) -> {
            try {
                Keccak256 hash = new Keccak256(Keccak256Helper.keccak256(leftNode.toMessage()));
                result.setLeftHash(hash.getBytes());
            } catch (Throwable e) {
                logger.error("Error recovering left node", e);
            }
        });
        right.ifPresent((rightNode) -> {
            Keccak256 hash = new Keccak256(Keccak256Helper.keccak256(rightNode.toMessage()));
            result.setRightHash(hash.getBytes());
        });
        return result;
    }

    private static Keccak256 getHash(byte[] recoveredBytes) {
        return new Keccak256(Keccak256Helper.keccak256(recoveredBytes));
    }

    private static String getHashString(byte[] recoveredBytes) {
        return getHash(recoveredBytes).toHexString();
    }

    private static long getValue(TrieDTO trieCollection) {
        return trieCollection.getChildrenSize().value;
    }

}
