/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.db;

import co.rsk.panic.PanicProcessor;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieImpl;
import org.apache.commons.collections4.MapUtils;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;

import java.util.*;

import static java.util.Collections.unmodifiableMap;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * @author Roman Mandeleil
 * @since 24.06.2014
 */
public class ContractDetailsCacheImpl implements ContractDetails {

    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private Map<DataWord, DataWord> storage = new HashMap<>();
    private Map<DataWord, byte[]> bytesStorage = new HashMap<>();

    ContractDetails origContract;

    private byte[] code;

    private boolean dirty = false;
    private boolean deleted = false;


    public ContractDetailsCacheImpl(ContractDetails origContract) {
        this.origContract = origContract;
        this.code = origContract != null ? origContract.getCode() : EMPTY_BYTE_ARRAY;
    }

    @Override
    public void put(DataWord key, DataWord value) {
        storage.put(key, value);
        this.setDirty(true);
    }

    @Override
    public void putBytes(DataWord key, byte[] value) {
        bytesStorage.put(key, value);
        this.setDirty(true);
    }

    @Override
    public DataWord get(DataWord key) {

        DataWord value = storage.get(key);
        if (value != null) {
            value = value.clone();
        } else{
            if (origContract == null) {
                return null;
            }

            value = origContract.get(key);
            storage.put(key.clone(), value == null ? DataWord.ZERO.clone() : value.clone());
        }

        if (value == null || value.isZero()) {
            return null;
        } else {
            return value;
        }
    }

    @Override
    public byte[] getBytes(DataWord key) {

        byte[] value = bytesStorage.get(key);
        if (value != null) {
            value = value.clone();
        } else{
            if (origContract == null) {
                return null;
            }

            value = origContract.getBytes(key);
            bytesStorage.put(key.clone(), value == null ? null : value.clone());
        }

        if (value == null) {
            return null;
        } else {
            return value;
        }
    }

    @Override
    public byte[] getCode() {
        return code;
    }

    @Override
    public void setCode(byte[] code) {
        this.code = code;
    }

    @Override
    public byte[] getStorageHash() { // todo: unsupported
        Trie storageTrie = new TrieImpl(null, true);

        for (DataWord key : storage.keySet()) {

            DataWord value = storage.get(key);

            storageTrie = storageTrie.put(key.getData(),
                    RLP.encodeElement(value.getNoLeadZeroesData()));
        }

        for (DataWord key : bytesStorage.keySet()) {
            byte[] value = bytesStorage.get(key);

            storageTrie = storageTrie.put(key.getData(),
                    RLP.encodeElement(value));
        }

        return storageTrie.getHash().getBytes();
    }

    @Override
    public void decode(byte[] rlpCode) {
        panicProcessor.panic("contractdetailscacheimpl", "Decode method should not be invoked.");
        throw new UnsupportedOperationException("No decode option during cache state");
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
        return dirty;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public byte[] getEncoded() {
        panicProcessor.panic("contractdetailscacheimpl", "getEncoded method should not be invoked.");
        throw new UnsupportedOperationException("No getEncoded option during cache state");
    }

    @Override
    public Map<DataWord, DataWord> getStorage() {
        return unmodifiableMap(storage);
    }

    @Override
    public Map<DataWord, DataWord> getStorage(Collection<DataWord> keys) {
        if (keys == null) {
            return getStorage();
        }

        Map<DataWord, DataWord> result = new HashMap<>();
        for (DataWord key : keys) {
            result.put(key, storage.get(key));
        }
        return unmodifiableMap(result);
    }

    @Override
    public int getStorageSize() {
        return (origContract == null)
                ? storage.size()
                : origContract.getStorageSize();
    }

    @Override
    public Set<DataWord> getStorageKeys() {
        return (origContract == null)
                ? storage.keySet()
                : origContract.getStorageKeys();
    }

    @Override
    public void setStorage(List<DataWord> storageKeys, List<DataWord> storageValues) {

        for (int i = 0; i < storageKeys.size(); ++i){

            DataWord key   = storageKeys.get(i);
            DataWord value = storageValues.get(i);

            if (value.isZero()) {
                storage.put(key, null);
            }
        }

    }

    @Override
    public void setStorage(Map<DataWord, DataWord> storage) {
        this.storage = storage;
    }

    @Override
    public byte[] getAddress() {
         return (origContract == null) ? null : origContract.getAddress();
    }

    @Override
    public void setAddress(byte[] address) {
        if (origContract != null) {
            origContract.setAddress(address);
        }
    }

    @Override
    public ContractDetails clone() {

        ContractDetailsCacheImpl contractDetails = new ContractDetailsCacheImpl(origContract);

        Object storageClone = ((HashMap<DataWord, DataWord>)storage).clone();

        contractDetails.setCode(this.getCode());
        contractDetails.setStorage( (HashMap<DataWord, DataWord>) storageClone);
        //WARNING bytesStorage is not cloned. Is this a bug?
        return contractDetails;
    }

    @Override
    public String toString() {

        String ret = "  Code: " + Hex.toHexString(code) + "\n";
        ret += "  Storage: " + getStorage().toString();

        return ret;
    }

    @Override
    public void syncStorage() {
        if (origContract != null) {
            origContract.syncStorage();
        }
    }

    public void commit(){

        if (origContract == null) {
            return;
        }

        for (DataWord key : storage.keySet()) {
            origContract.put(key, storage.get(key));
        }

        for (DataWord key : bytesStorage.keySet()) {
            origContract.putBytes(key, bytesStorage.get(key));
        }

        origContract.setCode(code);
        origContract.setDirty(this.dirty || origContract.isDirty());
    }


    @Override
    public ContractDetails getSnapshotTo(byte[] hash) {
        throw new UnsupportedOperationException("No snapshot option during cache state");
    }

    @Override
    public boolean isNullObject() {
        return origContract.isNullObject() && (MapUtils.isEmpty(storage));
    }

    public ContractDetails getOriginalContractDetails() {
        return this.origContract;
    }

    public void setOriginalContractDetails(ContractDetails contractDetails) {
        this.origContract = contractDetails;
    }
}

