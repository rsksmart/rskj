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

import org.ethereum.datasource.KeyValueDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generic interface for Ethereum database
 *
 * LevelDB key/value pair DB implementation will be used.
 * Choice must be made between:
 * Pure Java: https://github.com/dain/leveldb
 * JNI binding: https://github.com/fusesource/leveldbjni
 */
public class DatabaseImpl implements Database {

    private static final Logger logger = LoggerFactory.getLogger("db");
    private String name;

    private KeyValueDataSource keyValueDataSource;

    public DatabaseImpl(KeyValueDataSource keyValueDataSource) {
        this.keyValueDataSource = keyValueDataSource;
    }


    public DatabaseImpl(String name) {

        keyValueDataSource.setName(name);
        keyValueDataSource.init();
    }


    @Override
    public byte[] get(byte[] key) {
        return keyValueDataSource.get(key);
    }

    @Override
    public void put(byte[] key, byte[] value) {

        if (logger.isDebugEnabled()) {
            logger.debug("put: key: [{}], value: [{}]",
                    Hex.toHexString(key),
                    Hex.toHexString(value));
        }
        keyValueDataSource.put(key, value);
    }

    @Override
    public void delete(byte[] key) {
        if (logger.isDebugEnabled()) {
            logger.debug("delete: key: [{}]");
        }

        keyValueDataSource.delete(key);
    }

    public KeyValueDataSource getDb() {
        return this.keyValueDataSource;
    }

    @Override
    public void init(){
        keyValueDataSource.init();
    }

    @Override
    public void close() {

        keyValueDataSource.close();
    }

    public <T> List<T> dumpKeys(Function<byte[], T> mapper) {
        return keyValueDataSource.keys()
                .stream()
                .map(mapper)
                .collect(Collectors.toList());
    }
}