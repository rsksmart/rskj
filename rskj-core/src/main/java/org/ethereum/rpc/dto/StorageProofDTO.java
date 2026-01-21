/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package org.ethereum.rpc.dto;

import java.util.Arrays;

/**
 * DTO for storage proof in eth_getProof response.
 * Contains the key, value, and Merkle proof for a single storage slot.
 */
public class StorageProofDTO {
    private final String key;       // The requested storage key
    private final String value;     // The storage value at that key
    private final String[] proof;   // Array of RLP-encoded trie nodes forming the Merkle proof

    public StorageProofDTO(String key, String value, String[] proof) {
        this.key = key;
        this.value = value;
        this.proof = proof != null ? proof.clone() : new String[0];
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public String[] getProof() {
        return proof.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StorageProofDTO that = (StorageProofDTO) o;
        return key.equals(that.key) &&
                value.equals(that.value) &&
                Arrays.equals(proof, that.proof);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + value.hashCode();
        result = 31 * result + Arrays.hashCode(proof);
        return result;
    }
}
