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

import org.ethereum.vm.DataWord;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface ContractDetails {

    void put(DataWord key, DataWord value);

    void putBytes(DataWord key, byte[] bytes);

    DataWord get(DataWord key);

    byte[] getBytes(DataWord key);

    byte[] getCode();

    void setCode(byte[] code);

    void setDirty(boolean dirty);

    void setDeleted(boolean deleted);

    boolean isDirty();

    boolean isDeleted();

    // We can't support getStorageSize() anymore for proxys
    int getStorageSize();

    // We can't support getStorageKeys() anymore for proxys
    Set<DataWord> getStorageKeys();


    Map<DataWord,byte[]> getStorage(@Nullable Collection<DataWord> keys);

    // We can't support getStorage() anymore because we will not store the keys
    // we only store hashed keys.
    Map<DataWord, byte[]> getStorage();

    void setStorage(Map<DataWord, byte[]> storage);

    byte[] getAddress();

    void setAddress(byte[] address);

    String toString();

    boolean isNullObject();
}
