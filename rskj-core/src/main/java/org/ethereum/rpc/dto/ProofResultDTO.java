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
 * DTO for eth_getProof RPC response.
 * Contains account information and Merkle proofs for the account and requested storage slots.
 * 
 * See EIP-1186: https://eips.ethereum.org/EIPS/eip-1186
 */
public class ProofResultDTO {
    private final String address;           // The address of the account
    private final String[] accountProof;    // Array of RLP-encoded trie nodes for account proof
    private final String balance;           // The balance of the account (hex encoded)
    private final String codeHash;          // The code hash of the account
    private final String nonce;             // The nonce of the account (hex encoded)
    private final String storageHash;       // The storage root hash
    private final StorageProofDTO[] storageProof;  // Array of storage proofs

    private ProofResultDTO(Builder builder) {
        this.address = builder.address;
        this.accountProof = builder.accountProof != null ? builder.accountProof.clone() : new String[0];
        this.balance = builder.balance;
        this.codeHash = builder.codeHash;
        this.nonce = builder.nonce;
        this.storageHash = builder.storageHash;
        this.storageProof = builder.storageProof != null ? builder.storageProof.clone() : new StorageProofDTO[0];
    }

    public String getAddress() {
        return address;
    }

    public String[] getAccountProof() {
        return accountProof.clone();
    }

    public String getBalance() {
        return balance;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public String getNonce() {
        return nonce;
    }

    public String getStorageHash() {
        return storageHash;
    }

    public StorageProofDTO[] getStorageProof() {
        return storageProof.clone();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProofResultDTO that = (ProofResultDTO) o;
        return address.equals(that.address) &&
                Arrays.equals(accountProof, that.accountProof) &&
                balance.equals(that.balance) &&
                codeHash.equals(that.codeHash) &&
                nonce.equals(that.nonce) &&
                storageHash.equals(that.storageHash) &&
                Arrays.equals(storageProof, that.storageProof);
    }

    @Override
    public int hashCode() {
        int result = address.hashCode();
        result = 31 * result + Arrays.hashCode(accountProof);
        result = 31 * result + balance.hashCode();
        result = 31 * result + codeHash.hashCode();
        result = 31 * result + nonce.hashCode();
        result = 31 * result + storageHash.hashCode();
        result = 31 * result + Arrays.hashCode(storageProof);
        return result;
    }

    public static class Builder {
        private String address;
        private String[] accountProof;
        private String balance;
        private String codeHash;
        private String nonce;
        private String storageHash;
        private StorageProofDTO[] storageProof;

        private Builder() {
        }

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public Builder accountProof(String[] accountProof) {
            this.accountProof = accountProof;
            return this;
        }

        public Builder balance(String balance) {
            this.balance = balance;
            return this;
        }

        public Builder codeHash(String codeHash) {
            this.codeHash = codeHash;
            return this;
        }

        public Builder nonce(String nonce) {
            this.nonce = nonce;
            return this;
        }

        public Builder storageHash(String storageHash) {
            this.storageHash = storageHash;
            return this;
        }

        public Builder storageProof(StorageProofDTO[] storageProof) {
            this.storageProof = storageProof;
            return this;
        }

        public ProofResultDTO build() {
            return new ProofResultDTO(this);
        }
    }
}
