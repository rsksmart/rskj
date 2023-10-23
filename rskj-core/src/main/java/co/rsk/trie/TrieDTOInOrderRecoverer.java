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
import org.ethereum.crypto.Keccak256Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

public class TrieDTOInOrderRecoverer {

    private static final Logger logger = LoggerFactory.getLogger(TrieDTOInOrderRecoverer.class);

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
        //logger.info("-- indexRoot: {}, childrenSize:{}", indexRoot, trieCollection[indexRoot].getChildrenSize().value);
        Optional<TrieDTO> left = recoverSubtree(trieCollection, start, indexRoot - 1, processTrieDTO);
        left.ifPresent(processTrieDTO);
        Optional<TrieDTO> right = recoverSubtree(trieCollection, indexRoot + 1, end, processTrieDTO);
        right.ifPresent(processTrieDTO);
        return Optional.of(fromTrieDTO(trieCollection[indexRoot], left, right));
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
            Keccak256 hash = new Keccak256(Keccak256Helper.keccak256(leftNode.toMessage()));
            result.setLeftHash(hash.getBytes());
        });
        right.ifPresent((rightNode) -> {
            Keccak256 hash = new Keccak256(Keccak256Helper.keccak256(rightNode.toMessage()));
            result.setRightHash(hash.getBytes());
        });
        /*logger.info("-- ChildrenSize: {} ,Hash: {}, Left: {}, Right:{}",
                result.getChildrenSize().value,
                getHashString(result.toMessage()).substring(0, 6),
                left.isPresent() ? HexUtils.toJsonHex(result.getLeft()).substring(0, 6):"",
                right.isPresent() ? HexUtils.toJsonHex(result.getRight()).substring(0, 6):"");
*/

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
