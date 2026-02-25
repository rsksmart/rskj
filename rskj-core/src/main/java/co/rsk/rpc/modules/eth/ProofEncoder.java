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
package co.rsk.rpc.modules.eth;

import co.rsk.trie.Trie;
import co.rsk.util.HexUtils;
import org.ethereum.util.RLP;

import java.util.List;

/**
 * Utility class for encoding Trie nodes into proof format.
 * Supports both RSKj native format and Ethereum-compatible format.
 */
public class ProofEncoder {

    private ProofEncoder() {
        // Utility class
    }

    /**
     * Encode a list of Trie nodes into hex-encoded proof strings using RSKj's native format.
     * The nodes are encoded using RSKj's binary trie serialization (RSKIP-107 format).
     *
     * @param nodes List of Trie nodes along the path (from leaf to root)
     * @return Array of hex-encoded node serializations
     */
    public static String[] encodeProofNodes(List<Trie> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return new String[0];
        }

        String[] proof = new String[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            Trie node = nodes.get(i);
            byte[] nodeData = node.toMessage();
            proof[i] = HexUtils.toUnformattedJsonHex(nodeData);
        }

        return proof;
    }

    /**
     * Encode a list of Trie nodes into RLP-encoded hex strings for Ethereum compatibility.
     * Each node is RLP-encoded to match Ethereum's Merkle Patricia Trie proof format.
     *
     * Note: RSKj uses a binary trie which differs from Ethereum's hexary MPT.
     * This method wraps RSKj's node serialization in RLP encoding for compatibility.
     *
     * @param nodes List of Trie nodes along the path (from leaf to root)
     * @return Array of RLP-encoded hex strings
     */
    public static String[] encodeProofNodesRlp(List<Trie> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return new String[0];
        }

        String[] proof = new String[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            Trie node = nodes.get(i);
            byte[] nodeData = node.toMessage();
            // Wrap the node data in RLP encoding
            byte[] rlpEncoded = RLP.encodeElement(nodeData);
            proof[i] = HexUtils.toUnformattedJsonHex(rlpEncoded);
        }

        return proof;
    }

    /**
     * Encode a single value as hex string with proper formatting.
     *
     * @param value The value bytes
     * @return Hex-encoded string prefixed with 0x
     */
    public static String encodeValue(byte[] value) {
        if (value == null || value.length == 0) {
            return "0x0";
        }
        return HexUtils.toUnformattedJsonHex(value);
    }

    /**
     * Encode a storage value as hex string with proper quantity formatting (no leading zeros).
     *
     * @param value The value bytes
     * @return Hex-encoded quantity string prefixed with 0x
     */
    public static String encodeStorageValue(byte[] value) {
        if (value == null || value.length == 0) {
            return "0x0";
        }
        // Strip leading zeros for storage values
        return HexUtils.toQuantityJsonHex(value);
    }
}
