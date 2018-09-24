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

import org.ethereum.db.ContractDetails;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import javax.annotation.Nullable;
import java.util.*;

public class ContractDetailsImpl implements ContractDetails {
    private static final Logger logger = LoggerFactory.getLogger("contractdetails");

    private Map<DataWord,byte[]> storage;
    private byte[] code;
    private byte[] address;
    private boolean dirty;
    private boolean deleted;
    private boolean closed;

    public static Map<DataWord,byte[]> newStorage() {
        return new HashMap<>();
    }

    public ContractDetailsImpl(byte[] address, Map<DataWord,byte[]> astorage,
                               byte[] code) {
        this.address = ByteUtils.clone(address);
        this.storage = astorage;
        this.code = ByteUtils.clone(code);
    }


    @Override
    public synchronized void put(DataWord key, DataWord value) {
        logger.trace("put word");

        byte[] keyBytes = key.getData();

        if (value.equals(DataWord.ZERO)) {
            storage.remove(key);
        }
        else {
            storage.put(key, value.getByteArrayForStorage());
        }

        this.setDirty(true);
    }

    @Override
    public synchronized void putBytes(DataWord key, byte[] bytes) {
        logger.trace("put bytes");


        byte[] keyBytes = key.getData();

        if (bytes == null) {
            storage.remove(key);
        }
        else {
            storage.put(key, bytes);
        }

        this.setDirty(true);
    }

    @Override
    public synchronized DataWord get(DataWord key) {
        logger.trace("get word");


        byte[] value = storage.get(key);

        if (value == null || value.length == 0) {
            return null;
        }

        return new DataWord(value);
    }

    @Override
    public synchronized byte[] getBytes(DataWord key) {
        logger.trace("get bytes");
        return storage.get(key);

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
        return storage.keySet().size();
    }

    @Override
    public synchronized Set<DataWord> getStorageKeys() {

        return storage.keySet();
    }

    @Override
    public synchronized Map<DataWord, byte[]> getStorage(@Nullable Collection<DataWord> keys) {
        Map<DataWord, byte[]> astorage = new HashMap<>();

        if (keys == null) {
            for (DataWord key: this.getStorageKeys()) {
                byte[] value = storage.get(key);
                if (value != null) {
                    astorage.put(key, value);
                }
            }
        } else {
            for (DataWord key : keys) {
                byte[] value = getBytes(key);
                if (value != null) {
                    astorage.put(key, value);
                }
            }
        }

        return astorage;
    }

    @Override
    public synchronized Map<DataWord, byte[]> getStorage() {
        return getStorage(null);
    }

    @Override
    public synchronized void setStorage(Map<DataWord, byte[]> storage) {
        for (Map.Entry<DataWord, byte[]> entry : storage.entrySet()) {
            putBytes(entry.getKey(), entry.getValue());
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
    public boolean isNullObject() {
        return (code==null || code.length==0) && storage.entrySet().isEmpty();
    }


}
