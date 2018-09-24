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

package co.rsk.db;

import co.rsk.crypto.Keccak256;
import co.rsk.panic.PanicProcessor;
import co.rsk.trie.*;
import org.ethereum.datasource.DataSourcePool;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.ContractDetails;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPItem;
import org.ethereum.util.RLPList;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.util.*;

import static org.ethereum.datasource.DataSourcePool.levelDbByName;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.ethereum.util.ByteUtil.toHexString;
import static org.ethereum.util.ByteUtil.wrap;

/**
 * Created by ajlopez on 05/04/2017.
 */
public class ContractDetailsImpl implements ContractDetails {
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    private static final Logger logger = LoggerFactory.getLogger("contractdetails");

    private Trie trie;
    private byte[] code;
    private byte[] address;
    private boolean dirty;
    private boolean deleted;

    private boolean closed;
    private Set<ByteArrayWrapper> keys = new HashSet<>();


    public ContractDetailsImpl(byte[] address, Trie trie, byte[] code, int memoryStorageLimit, String databaseDir) {
        this.address = ByteUtils.clone(address);
        this.trie = trie;
        this.code = ByteUtils.clone(code);

    }

    @Override
    public synchronized void put(DataWord key, DataWord value) {
        logger.trace("put word");

        byte[] keyBytes = key.getData();

        if (value.equals(DataWord.ZERO)) {
            this.trie = this.trie.delete(keyBytes);
            removeKey(keyBytes);
        }
        else {
            this.trie = this.trie.put(keyBytes, value.getNoLeadZeroesData());
            addKey(keyBytes);
        }

        this.setDirty(true);
    }

    @Override
    public synchronized void putBytes(DataWord key, byte[] bytes) {
        logger.trace("put bytes");


        byte[] keyBytes = key.getData();

        if (bytes == null) {
            this.trie = this.trie.delete(keyBytes);
            removeKey(keyBytes);
        }
        else {
            this.trie = this.trie.put(keyBytes, bytes);
            addKey(keyBytes);
        }

        this.setDirty(true);

    }

    @Override
    public synchronized DataWord get(DataWord key) {
        logger.trace("get word");


        byte[] value = null;

        value = this.trie.get(key.getData());

        if (value == null || value.length == 0) {
            return null;
        }

        return new DataWord(value);
    }

    @Override
    public synchronized byte[] getBytes(DataWord key) {
        logger.trace("get bytes");
        return this.trie.get(key.getData());

    }

    @Override
    public byte[] getCode() {
        return ByteUtils.clone(this.code);
    }

    @Override
    public void setCode(byte[] code) {
        this.code = ByteUtils.clone(code);
    }

    @Override
    public synchronized byte[] getStorageHash() {

        this.trie.save();
        byte[] trieHash = this.trie.getHash().getBytes();
        logger.trace("getting contract details trie hash {}, address {}", getHashAsString(trieHash), this.getAddressAsString());
        return trieHash;
    }


    @Override
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    @Override
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    @Override
    public boolean isDeleted() {
        return this.deleted;
    }

    @Override
    public synchronized int getStorageSize() {
        return keys.size();
    }

    @Override
    public synchronized Set<DataWord> getStorageKeys() {
        Set<DataWord> result = new HashSet<>();

        for (ByteArrayWrapper key : keys) {
            result.add(new DataWord(key));
        }

        return result;
    }

    @Override
    public synchronized Map<DataWord, DataWord> getStorage(@Nullable Collection<DataWord> keys) {
        Map<DataWord, DataWord> storage = new HashMap<>();

        if (keys == null) {
            for (ByteArrayWrapper keyBytes : this.keys) {
                DataWord key = new DataWord(keyBytes);
                DataWord value = get(key);

                // we check if the value is not null,
                // cause we keep all historical keys
                if (value != null) {
                    storage.put(key, value);
                }
            }
        } else {
            for (DataWord key : keys) {
                DataWord value = get(key);

                // we check if the value is not null,
                // cause we keep all historical keys
                if (value != null) {
                    storage.put(key, value);
                }
            }
        }

        return storage;
    }

    @Override
    public synchronized Map<DataWord, DataWord> getStorage() {
        return getStorage(null);
    }

    @Override
    public synchronized void setStorage(List<DataWord> storageKeys, List<DataWord> storageValues) {
        for (int i = 0; i < storageKeys.size(); ++i) {
            put(storageKeys.get(i), storageValues.get(i));
        }
    }

    @Override
    public synchronized void setStorage(Map<DataWord, DataWord> storage) {
        for (Map.Entry<DataWord, DataWord> entry : storage.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public byte[] getAddress() {
        return ByteUtils.clone(this.address);
    }

    @Override
    public void setAddress(byte[] address) {
        this.address = ByteUtils.clone(address);
    }


    @Override
    public synchronized ContractDetails getSnapshotTo(byte[] hash) {
        logger.trace("get snapshot");

        this.trie.save();

        ContractDetailsImpl details = new ContractDetailsImpl(this.address, this.trie.getSnapshotTo(new Keccak256(hash)), this.code, 0, "");
        details.keys = new HashSet<>();
        details.keys.addAll(this.keys);

        logger.trace("getting contract details snapshot hash {}, address {}, storage size {}", details.getStorageHashAsString(), details.getAddressAsString(), details.getStorageSize());

        return details;
    }

    @Override
    public boolean isNullObject() {
        return (code==null || code.length==0) && keys.isEmpty();
    }

    public Trie getTrie() {
        return this.trie;
    }


    private void addKey(byte[] key) {
        keys.add(wrap(key));
    }

    private void removeKey(byte[] key) {
        keys.remove(wrap(key));
    }


    private String getDataSourceName() {
        return "details-storage/" + toHexString(address);
    }

    private String getAddressAsString() {
        byte[] addr = this.getAddress();

        if (addr == null) {
            return "";
        }

        return Hex.toHexString(addr);
    }


    private String getStorageHashAsString() {
        return getHashAsString(this.trie.getHash().getBytes());
    }

    private static String getHashAsString(byte[] hash) {
        if (hash == null) {
            return "";
        }

        return Hex.toHexString(hash);
    }
}
