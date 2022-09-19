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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PersistentRocksDbDataSource extends RocksDbDataSource {

    public PersistentRocksDbDataSource(String name, String databaseDir) {
        super(name, databaseDir);
    }

    @Override
    protected boolean getOptionCreateIfMissing() {
        return true;
    }

    @Override
    protected void createRequiredDirectories(Path dbPath) throws IOException {
        Files.createDirectories(dbPath.getParent());
    }

    @Override
    protected boolean skipWriteOp() {
        return false;
    }

}
