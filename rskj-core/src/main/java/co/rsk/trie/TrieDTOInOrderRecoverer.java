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
import org.apache.commons.lang3.ArrayUtils;
import org.ethereum.crypto.Keccak256Helper;

import java.util.*;

public class TrieDTOInOrderRecoverer {

    public static Optional<TrieDTO> recoverTrie(TrieDTO[] trieCollection) {
        return recoverSubtree(trieCollection, 0, trieCollection.length - 1);
    }

    private static Optional<TrieDTO> recoverSubtree(TrieDTO[] trieCollection, int start, int end) {
        if (end - start < 0) {
            return Optional.empty();
        }
        if (end - start == 0) {
            return Optional.of(fromTrieDTO(trieCollection[0], Optional.empty(), Optional.empty()));
        }
        int indexRoot = findRoot(trieCollection, start, end);
        final int percentage = (indexRoot * 100 / trieCollection.length) ;
        if (percentage % 5 == 0) {
            System.out.println("-- Completed:" + percentage);
        }
        Optional<TrieDTO> left = recoverSubtree(trieCollection, start, indexRoot - 1);
        Optional<TrieDTO> right = recoverSubtree(trieCollection, indexRoot + 1, end);
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
            result.setLeft(hash.getBytes());
        });
        right.ifPresent((rightNode) -> {
            Keccak256 hash = new Keccak256(Keccak256Helper.keccak256(rightNode.toMessage()));
            result.setRight(hash.getBytes());
        });
        return result;
    }

    private static long getValue(TrieDTO trieCollection) {
        return trieCollection.getChildrenSize().value;
    }

    private static int compare(TrieDTO element, TrieDTO element2) {
        return Long.compare(element.getChildrenSize().value, element2.getChildrenSize().value);
    }
}
