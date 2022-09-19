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

package co.rsk.db;

import org.ethereum.datasource.TransientMap;
import org.ethereum.db.IndexedBlockStore;
import org.mapdb.DB;

import java.util.List;
import java.util.Map;

public class ReadonlyMapDBBlocksIndex extends MapDBBlocksIndex {

    // TODO:I Test

    public ReadonlyMapDBBlocksIndex(DB indexDB) {
        super(indexDB);
    }

    @Override
    protected Map<Long, List<IndexedBlockStore.BlockInfo>> wrapIndex(Map<Long, List<IndexedBlockStore.BlockInfo>> aindex) {
        return TransientMap.transientMap(aindex);
    }

    @Override
    protected Map<String, byte[]> wrapMetadata(Map<String, byte[]> ametadata) {
        return TransientMap.transientMap(ametadata);
    }

    @Override
    public void flush() {
        // a read-only mapDB cannot be committed, even if there is nothing to commit
    }

}
