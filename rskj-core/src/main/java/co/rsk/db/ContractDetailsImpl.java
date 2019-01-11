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
import co.rsk.trie.*;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.DataSourcePool;
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

import static org.ethereum.core.AccountState.EMPTY_DATA_HASH;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.ethereum.util.ByteUtil.wrap;

/**
 * Created by ajlopez on 05/04/2017.
 */
public class ContractDetailsImpl implements ContractDetails {
    private static final Logger logger = LoggerFactory.getLogger("contractdetails");

    private Trie trie;
    private byte[] code;
    private byte[] address;
    private boolean dirty;
    private boolean deleted;
    private boolean closed;
    private Set<ByteArrayWrapper> keys = new HashSet<>();
    private final TrieStore.Pool trieStorePool;
    private final int memoryStorageLimit;
    private byte[] codeHash;

    public ContractDetailsImpl(byte[] encoded, TrieStore.Pool trieStorePool, int memoryStorageLimit) {
        this.trieStorePool = trieStorePool;
        this.memoryStorageLimit = memoryStorageLimit;
        decode(encoded);
    }

    public ContractDetailsImpl(byte[] address, Trie trie, byte[] code, TrieStore.Pool trieStorePool, int memoryStorageLimit) {
        this.address = ByteUtils.clone(address);
        this.trie = trie;
        this.code = ByteUtils.clone(code);
        this.codeHash = getCodeHash(code);
        this.trieStorePool = trieStorePool;
        this.memoryStorageLimit = memoryStorageLimit;

        if (this.trie == null) {
            this.trie = this.newTrie();
        }
    }

    private byte[] getCodeHash(byte[] code) {
        return code == null ? EMPTY_DATA_HASH : Keccak256Helper.keccak256(code);
    }

    private TrieImpl newTrie() {
        TrieStore store = new ContractStorageStoreFactory(this.trieStorePool).getTrieStore(this.address);
        return new TrieImpl(store, true);
    }

    @Override
    public synchronized void put(DataWord key, DataWord value) {
        logger.trace("put word");

        checkDataSourceIsOpened();

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

        checkDataSourceIsOpened();

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

        checkDataSourceIsOpened();

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

        checkDataSourceIsOpened();

        try {
            return this.trie.get(key.getData());
        }
        catch (RuntimeException ex) {
            logger.error("error in get bytes", ex);
            logger.trace("retrying get bytes");
            checkDataSourceIsOpened();
            return this.trie.get(key.getData());
        }
    }

    @Override
    public byte[] getCode() {
        return ByteUtils.clone(this.code);
    }

    @Override
    public void setCode(byte[] code) {
        this.code = ByteUtils.clone(code);
        this.codeHash = getCodeHash(code);
    }

    @Override
    public synchronized byte[] getStorageHash() {
        checkDataSourceIsOpened();

        this.trie.save();
        byte[] trieHash = this.trie.getHash().getBytes();
        logger.trace("getting contract details trie hash {}, address {}", getHashAsString(trieHash), this.getAddressAsString());
        return trieHash;
    }

    private final void decode(byte[] rlpBytes) {
        ArrayList<RLPElement> rlpData = RLP.decode2(rlpBytes);
        RLPList rlpList = (RLPList) rlpData.get(0);

        RLPItem rlpAddress = (RLPItem) rlpList.get(0);
        RLPItem rlpIsExternalStorage = (RLPItem) rlpList.get(1);
        RLPItem rlpStorage = (RLPItem) rlpList.get(2);
        RLPElement rlpCode = rlpList.get(3);
        RLPList rlpKeys = (RLPList) rlpList.get(4);

        this.address = rlpAddress.getRLPData();

        byte[] root = rlpStorage.getRLPData();
        byte[] external = rlpIsExternalStorage.getRLPData();

        if (external != null && external.length > 0 && external[0] == 1) {
            Keccak256 snapshotHash = new Keccak256(root);
            this.trie = this.newTrie().getSnapshotTo(snapshotHash);
        } else {
            TrieImpl newTrie = this.newTrie();
            TrieImpl tempTrie = (TrieImpl)TrieImpl.deserialize(root);
            newTrie.getStore().copyFrom(tempTrie.getStore());
            this.trie = newTrie.getSnapshotTo(tempTrie.getHash());
        }

        this.code = (rlpCode.getRLPData() == null) ? EMPTY_BYTE_ARRAY : rlpCode.getRLPData();
        this.codeHash = Keccak256Helper.keccak256(code);
        for (RLPElement key : rlpKeys) {
            addKey(key.getRLPData());
        }

        logger.trace("decoding contract details from bytes, hash {}, address {}, storage size {}, has external storage {}", this.getStorageHashAsString(), this.getAddressAsString(), this.getStorageSize(), this.hasExternalStorage());
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
    public byte[] getEncoded() {
        logger.trace("getting contract details as bytes, hash {}, address {}, storage size {}, has external storage {}", this.getStorageHashAsString(), this.getAddressAsString(), this.getStorageSize(), this.hasExternalStorage());

        byte[] rlpAddress = RLP.encodeElement(address);
        byte[] rlpIsExternalStorage = RLP.encodeByte((byte) 1);

        // Serialize the full trie, or only the root hash if external storage is used
        byte[] rlpStorage = RLP.encodeElement(this.trie.getHash().getBytes());

        byte[] rlpCode = RLP.encodeElement(this.code);
        byte[] rlpKeys = RLP.encodeSet(this.keys);

        return RLP.encodeList(rlpAddress, rlpIsExternalStorage, rlpStorage, rlpCode, rlpKeys);
    }

    @VisibleForTesting
    public byte[] getEncodedOldFormat() {
        logger.trace("getting contract details as bytes, hash {}, address {}, storage size {}, has external storage {}", this.getStorageHashAsString(), this.getAddressAsString(), this.getStorageSize(), this.hasExternalStorage());

        byte[] rlpAddress = RLP.encodeElement(address);
        byte[] rlpIsExternalStorage = RLP.encodeByte((byte) 0);

        // Serialize the full trie
        byte[] rlpStorage = RLP.encodeElement(this.trie.serialize());

        byte[] rlpCode = RLP.encodeElement(this.code);
        byte[] rlpKeys = RLP.encodeSet(this.keys);

        return RLP.encodeList(rlpAddress, rlpIsExternalStorage, rlpStorage, rlpCode, rlpKeys);
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
    public synchronized void syncStorage() {
        String hashString = this.getStorageHashAsString();
        String addressString = this.getAddressAsString();
        logger.trace("syncing storage address {}", addressString);

        if (this.trie.hasStore()) {
            logger.trace("syncing to storage, hash {}, address {}, storage size {}", hashString, addressString, this.getStorageSize());

            this.trie.save();
        }
    }

    @Override
    public synchronized ContractDetails getSnapshotTo(byte[] hash) {
        logger.trace("get snapshot");

        this.trie.save();

        ContractDetailsImpl details = new ContractDetailsImpl(this.address,
                                                              this.trie.getSnapshotTo(new Keccak256(hash)),
                                                              this.code,
                                                              this.trieStorePool,
                                                              this.memoryStorageLimit);
        details.keys = new HashSet<>();
        details.keys.addAll(this.keys);

        DataSourcePool.reserve(getDataSourceName());

        logger.trace("getting contract details snapshot hash {}, address {}, storage size {}, has external storage {}", details.getStorageHashAsString(), details.getAddressAsString(), details.getStorageSize(), details.hasExternalStorage());

        return details;
    }

    @Override
    public boolean isNullObject() {
        return (code==null || code.length==0) && keys.isEmpty();
    }

    @Override
    public byte[] getCodeHash() {
        return this.codeHash;
    }

    public Trie getTrie() {
        return this.trie;
    }

    public boolean hasExternalStorage() {
        return true;
    }

    private void addKey(byte[] key) {
        keys.add(wrap(key));
    }

    private void removeKey(byte[] key) {
        keys.remove(wrap(key));
    }

    public String getDataSourceName() {
        return "contracts-storage";
    }

    private String getAddressAsString() {
        byte[] addr = this.getAddress();

        if (addr == null) {
            return "";
        }

        return Hex.toHexString(addr);
    }

    private void checkDataSourceIsOpened() {
        if (!this.closed) {
            return;
        }

        logger.trace("reopening contract details data source");
        TrieStoreImpl newStore = (TrieStoreImpl) trieStorePool.getInstanceFor(getDataSourceName());
        Trie newTrie = newStore.retrieve(this.trie.getHash().getBytes());
        this.trie = newTrie;
        this.closed = false;
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

    public void fixCodeBy(byte[] otherCodeHash) {
        this.code = trieStorePool.getInstanceFor(getDataSourceName()).retrieveValue(otherCodeHash);
        this.codeHash = otherCodeHash;
    }
}
