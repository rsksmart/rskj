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

import co.rsk.panic.PanicProcessor;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
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
    private static final Logger logger = LoggerFactory.getLogger("triestore");
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    private static final String PANIC_TOPIC = "triestore";
    private static final String ERROR_CREATING_STORE = "Error creating trie store";

    // a key value data source to use
    private KeyValueDataSource store;

    // internal variables, count of saves and retrieves
    private int saveCount = 0;
    private int retrieveCount = 0;

    public TrieStoreImpl(KeyValueDataSource store) {
        this.store = store;
    }

    /**
     * save saves a Trie to the store
     * @param trie
     */
    @Override
    public void save(Trie trie) {
        this.saveCount++;
        this.store.put(trie.getHash().getBytes(), trie.toMessage());

        if (trie.hasLongValue()) {
            this.saveCount++;
            this.store.put(trie.getValueHash(), trie.getValue());
        }
    }

    @Override
    public int getSaveCount() { return this.saveCount; }

    /**
     * retrieve retrieves a Trie instance from store, using hash a key
     *
     * @param hash  the hash to retrieve
     *
     * @return  the retrieved Trie, null if key does not exist
     */
    @Override
    public Trie retrieve(byte[] hash) {
        this.retrieveCount++;

        byte[] message = this.store.get(hash);

        return TrieImpl.fromMessage(message, this);
    }

    public byte[] retrieveValue(byte[] hash) {
        return this.store.get(hash);
    }

    @Override
    public int getRetrieveCount() { return this.retrieveCount; }

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

    public void copyFrom(TrieStoreImpl originalTrieStore) {
        KeyValueDataSource ds = originalTrieStore.store;

        for (byte[] key : ds.keys()) {
            this.store.put(key, ds.get(key));
        }
    }

    public static TrieStoreImpl deserialize(byte[] bytes) {
        return deserialize(bytes, 0, bytes.length, new HashMapDB());
    }

    public static TrieStoreImpl deserialize(byte[] bytes, int offset, int length, KeyValueDataSource ds) {
        ByteArrayInputStream bstream = new ByteArrayInputStream(bytes, offset, length);
        DataInputStream dstream = new DataInputStream(bstream);

        try {
            dstream.readShort(); // version

            int nkeys = dstream.readInt();

            for (int k = 0; k < nkeys; k++) {
                int lkey = dstream.readInt();
                byte[] key = new byte[lkey];
                if (dstream.read(key) != lkey) {
                    throw new EOFException();
                }

                int lvalue = dstream.readInt();
                byte[] value = new byte[lvalue];
                if (dstream.read(value) != lvalue) {
                    throw new EOFException();
                }

                ds.put(key, value);
            }

            return new TrieStoreImpl(ds);
        }
        catch (IOException ex) {
            logger.error(ERROR_CREATING_STORE, ex);
            panicProcessor.panic(PANIC_TOPIC, ERROR_CREATING_STORE +": " + ex.getMessage());
            throw new TrieSerializationException(ERROR_CREATING_STORE, ex);
        }
    }
}
