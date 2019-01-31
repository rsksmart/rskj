/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TrieStoreImpl store and retrieve Trie node by hash
 *
 * It saves/retrieves the serialized form (byte array) of a Trie node
 *
 * Internally, it uses a key value data source
 *
 * Created by ajlopez on 08/01/2017.
 */
public class TrieStoreImpl implements TrieStore {

    private static final int LAST_BYTE_ONLY_MASK = 0x000000ff;

    // a key value data source to use
    private KeyValueDataSource store;

    public TrieStoreImpl(KeyValueDataSource store) {
        this.store = store;
    }

    /**
     * save saves a Trie to the store
     * @param trie
     */
    @Override
    public void save(Trie trie) {
        this.store.put(trie.getHash().getBytes(), trie.toMessage());

        if (trie.hasLongValue()) {
            this.store.put(trie.getValueHash(), trie.getValue());
        }
    }

    /**
     * retrieve retrieves a Trie instance from store, using hash a key
     *
     * @param hash  the hash to retrieve
     *
     * @return  the retrieved Trie, null if key does not exist
     */
    @Override
    public Trie retrieve(byte[] hash) {
        byte[] message = this.store.get(hash);

        return TrieImpl.fromMessage(message, this);
    }

    public byte[] retrieveValue(byte[] hash) {
        return this.store.get(hash);
    }

    public byte[] storeValue(byte[] key, byte[] value) {
        return this.store.put(key, value);
    }

    @Override
    public byte[] serialize() {
        List<byte[]> keys = new ArrayList<>();
        List<byte[]> values = new ArrayList<>();

        int lkeys = 0;
        int lvalues = 0;

        for (byte[] key : this.store.keys()) {
            byte[] value = this.store.get(key);

            if (value == null || value.length == 0) {
                continue;
            }

            keys.add(key);
            values.add(value);

            lkeys += key.length;
            lvalues += value.length;
        }

        int nkeys = keys.size();

        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES + Integer.BYTES + lkeys + lvalues + Integer.BYTES * nkeys * 2);

        buffer.putShort((short)0);
        buffer.putInt(nkeys);

        for (int k = 0; k < nkeys; k++) {
            byte[] key = keys.get(k);
            byte[] value = values.get(k);

            buffer.putInt(key.length);
            buffer.put(key);

            buffer.putInt(value.length);
            buffer.put(value);
        }

        return buffer.array();
    }

    public KeyValueDataSource getDataSource() {
        return this.store;
    }

    public static TrieStoreImpl deserialize(byte[] bytes) {
        return deserialize(bytes, 0, new HashMapDB());
    }

    public static TrieStoreImpl deserialize(byte[] bytes, int offset, KeyValueDataSource ds) {
        int current = offset;
        current += Short.BYTES; // version

        int nkeys = readInt(bytes, current);
        current += Integer.BYTES;

        for (int k = 0; k < nkeys; k++) {
            int lkey = readInt(bytes, current);
            current += Integer.BYTES;
            if (lkey > bytes.length - current) {
                throw new IllegalArgumentException(String.format(
                        "Left bytes are too short for key expected:%d actual:%d total:%d",
                        lkey, bytes.length - current, bytes.length));
            }
            byte[] key = Arrays.copyOfRange(bytes, current, current + lkey);
            current += lkey;

            int lvalue = readInt(bytes, current);
            current += Integer.BYTES;
            if (lvalue > bytes.length - current) {
                throw new IllegalArgumentException(String.format(
                        "Left bytes are too short for value expected:%d actual:%d total:%d",
                        lvalue, bytes.length - current, bytes.length));
            }
            byte[] value = Arrays.copyOfRange(bytes, current, current + lvalue);
            current += lvalue;
            ds.put(key, value);
        }

        return new TrieStoreImpl(ds);
    }

    // this methods reads a int as dataInputStream + byteArrayInputStream
    private static int readInt(byte[] bytes, int position) {
        int ch1 = bytes[position] & LAST_BYTE_ONLY_MASK;
        int ch2 = bytes[position+1] & LAST_BYTE_ONLY_MASK;
        int ch3 = bytes[position+2] & LAST_BYTE_ONLY_MASK;
        int ch4 = bytes[position+3] & LAST_BYTE_ONLY_MASK;
        if ((ch1 | ch2 | ch3 | ch4) < 0) {
            throw new IllegalArgumentException(
                    String.format("On position %d there are invalid bytes for a short value %s %s %s %s",
                                  position, ch1, ch2, ch3, ch4));
        } else {
            return (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4);
        }
    }
}
