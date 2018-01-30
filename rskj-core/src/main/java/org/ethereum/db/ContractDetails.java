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

import co.rsk.core.commons.Keccak256;
import org.ethereum.vm.DataWord;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ContractDetails {

    void put(DataWord key, DataWord value);

    void putBytes(DataWord key, byte[] bytes);

    DataWord get(DataWord key);

    byte[] getBytes(DataWord key);

    byte[] getCode();

    void setCode(byte[] code);

    Keccak256 getStorageHash();

    void decode(byte[] rlpCode);

    void setDirty(boolean dirty);

    void setDeleted(boolean deleted);

    boolean isDirty();

    boolean isDeleted();

    byte[] getEncoded();

    int getStorageSize();

    Set<DataWord> getStorageKeys();

    Map<DataWord,DataWord> getStorage(@Nullable Collection<DataWord> keys);

    Map<DataWord, DataWord> getStorage();

    void setStorage(List<DataWord> storageKeys, List<DataWord> storageValues);

    void setStorage(Map<DataWord, DataWord> storage);

    byte[] getAddress();

    void setAddress(byte[] address);

    String toString();

    void syncStorage();

    ContractDetails getSnapshotTo(Keccak256 hash);

    boolean isNullObject();
}
