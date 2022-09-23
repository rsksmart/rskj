/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

package org.ethereum.datasource;

import org.ethereum.db.ByteArrayWrapper;
import org.iq80.leveldb.Options;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public class LevelDbDataSourceReadonly extends LevelDbDataSource implements ReadonlyDbDataSource {

    public static LevelDbDataSourceReadonly create(String name, String databaseDir) {
        return new LevelDbDataSourceReadonly(name, databaseDir);
    }

    protected LevelDbDataSourceReadonly(String name, String databaseDir) {
        super(name, databaseDir, LoggerFactory.getLogger("db-readonly"));
    }

    @Override
    public void customiseOptions(Options options) {
        options.createIfMissing(false);
    }

    @Override
    public void createRequiredDirectories(Path dbPath) {
        // nothing to do
    }

    @Override
    public byte[] put(byte[] key, byte[] value) {
        throw exceptionOnWriteOp();
    }

    @Override
    public void delete(byte[] key) {
        throw exceptionOnWriteOp();
    }

    @Override
    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> deleteKeys) {
        throw exceptionOnWriteOp();
    }

    @Override
    public ReadOnlyException exceptionOnWriteOp() {
        return new ReadOnlyException("database is readonly");
    }

}
