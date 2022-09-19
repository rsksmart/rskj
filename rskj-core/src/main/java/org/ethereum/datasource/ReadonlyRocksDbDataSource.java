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

import java.nio.file.Path;

public class ReadonlyRocksDbDataSource extends RocksDbDataSource implements ReadonlyDbDataSource {

    // TODO:I test

    public ReadonlyRocksDbDataSource(String name, String databaseDir) {
        super(name, databaseDir);
    }

    @Override
    public boolean getOptionCreateIfMissing() {
        return false;
    }

    @Override
    public void createRequiredDirectories(Path dbPath) {
        // nothing to do
    }

    @Override
    protected boolean skipWriteOp() throws ReadOnlyException {
        checkExceptionOnWrite();
        // otherwise just skip
        return true;
    }

    @Override
    public void checkExceptionOnWrite() throws ReadOnlyException {
        throw new ReadOnlyException("database is readonly");
    }

}
