/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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
import co.rsk.panic.PanicProcessor;
import org.ethereum.crypto.Keccak256Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

public class TrieSerializer {

    private static final int ARITY = 2;
    private static final int MESSAGE_HEADER_LENGTH = 2 + Short.BYTES * 2;
    private static final String PANIC_TOPIC = "newtrie";
    private static final String INVALID_ARITY = "Invalid arity";
    private static final String ERROR_CREATING_TRIE = "Error creating trie from message";

    private static final Logger logger = LoggerFactory.getLogger("triestore");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    /**
     * toMessage serialize the node to bytes. Used to persist the node in a key-value store
     * like levelDB or redis.
     *
     * The serialization includes:
     * - arity: byte
     * - bits with present hashes: two bytes (example: 0x0203 says that the node has
     * hashes at index 0, 1, 9 (the other subnodes are null)
     * - present hashes: 32 bytes each
     * - associated value: remainder bytes (no bytes if null)
     *
     * @return a byte array with the serialized info
     */
    public byte[] serialize(Trie trie) {
        byte[] value = trie.getValue();
        int lvalue = value == null ? 0 : value.length;
        int nnodes = trie.getNodeCount();
        int lshared = trie.getSharedPathLength();
        int lencoded = getEncodedPathLength(lshared);
        boolean hasLongVal = trie.hasLongValue();

        int bits = 0;

        for (int k = 0; k < ARITY; k++) {
            Keccak256 nodeHash = trie.getHash(k);

            if (nodeHash == null) {
                continue;
            }

            bits |= 1 << k;
        }

        ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_HEADER_LENGTH + lencoded + nnodes * Keccak256Helper.DEFAULT_SIZE_BYTES + (hasLongVal ? Keccak256Helper.DEFAULT_SIZE_BYTES : lvalue));

        buffer.put((byte) ARITY);

        byte flags = 0;

        if (trie.isSecure()) {
            flags |= 1;
        }

        if (hasLongVal) {
            flags |= 2;
        }

        buffer.put(flags);
        buffer.putShort((short) bits);
        buffer.putShort((short) lshared);

        if (lshared > 0) {
            buffer.put(trie.getEncodedSharedPath());
        }

        for (int k = 0; k < ARITY; k++) {
            Keccak256 nodeHash = trie.getHash(k);

            if (nodeHash == null) {
                continue;
            }

            buffer.put(nodeHash.getBytes());
        }

        if (lvalue > 0) {
            if (hasLongVal) {
                buffer.put(trie.getValueHash());
            }
            else {
                buffer.put(trie.getValue());
            }
        }

        return buffer.array();

    }

    /**
     * Pool method, to create a NewTrie from a serialized message
     * the store argument is used to retrieve any subnode
     * of the subnode
     *
     * @param rawTrie   the content (arity, hashes, value) serializaced as byte array
     * @param trieStore     the store containing the rest of the trie nodes, to be used on retrieve them if they are needed in memory
     */
    public Trie deserialize(byte[] rawTrie, TrieStore trieStore) {
        if (rawTrie == null) {
            return null;
        }

        ByteArrayInputStream bstream = new ByteArrayInputStream(rawTrie, 0, rawTrie.length);
        DataInputStream istream = new DataInputStream(bstream);

        try {
            int arity = istream.readByte();

            if (arity != ARITY) {
                throw new IllegalArgumentException(INVALID_ARITY);
            }

            int flags = istream.readByte();
            boolean isSecure = (flags & 0x01) == 1;
            boolean hasLongVal = (flags & 0x02) == 2;
            int bhashes = istream.readShort();
            int lshared = istream.readShort();

            int nhashes = 0;
            int lencoded = getEncodedPathLength(lshared);

            byte[] encodedSharedPath = null;

            if (lencoded > 0) {
                encodedSharedPath = new byte[lencoded];
                if (istream.read(encodedSharedPath) != lencoded) {
                    throw new EOFException();
                }
            }

            Keccak256[] hashes = new Keccak256[arity];

            for (int k = 0; k < arity; k++) {
                if ((bhashes & (1 << k)) == 0) {
                    continue;
                }

                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];

                if (istream.read(valueHash) != Keccak256Helper.DEFAULT_SIZE_BYTES) {
                    throw new EOFException();
                }

                hashes[k] = new Keccak256(valueHash);
                nhashes++;
            }

            int offset = MESSAGE_HEADER_LENGTH + lencoded + nhashes * Keccak256Helper.DEFAULT_SIZE_BYTES;
            byte[] value = null;

            if (hasLongVal) {
                byte[] valueHash = new byte[Keccak256Helper.DEFAULT_SIZE_BYTES];

                if (istream.read(valueHash) != Keccak256Helper.DEFAULT_SIZE_BYTES) {
                    throw new EOFException();
                }

                value = trieStore.retrieveValue(valueHash);
            }
            else {
                int lvalue = rawTrie.length - offset;

                if (lvalue > 0) {
                    value = new byte[lvalue];
                    if (istream.read(value) != lvalue) {
                        throw new EOFException();
                    }
                }
            }

            return new TrieImpl(encodedSharedPath, lshared, value, null, hashes, trieStore, isSecure);
        } catch (IOException ex) {
            logger.error(ERROR_CREATING_TRIE, ex);
            panicProcessor.panic(PANIC_TOPIC, ERROR_CREATING_TRIE +": " + ex.getMessage());
            throw new TrieSerializationException(ERROR_CREATING_TRIE, ex);
        }
    }


    private static int getEncodedPathLength(int length) {
        return length / 8 + (length % 8 == 0 ? 0 : 1);
    }
}
