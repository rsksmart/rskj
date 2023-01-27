/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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
package org.ethereum.util;

import co.rsk.RskContext;
import co.rsk.core.Wallet;
import co.rsk.db.HashMapBlocksIndex;
import co.rsk.db.StateRootHandler;
import co.rsk.db.StateRootsStore;
import co.rsk.db.StateRootsStoreImpl;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;

import java.nio.file.Path;

/**
 * This context overrides every persistent database access with a non-persistent one.
 * It is the closest to a production context that one can use for testing.
 */
public class RskTestContext extends RskContext {
    public RskTestContext(String[] args) {
        super(args, false, true);
    }

    @Override
    protected ReceiptStore buildReceiptStore() {
        return new ReceiptStoreImpl(new HashMapDB());
    }

    @Override
    protected StateRootsStore buildStateRootsStore() {
        return new StateRootsStoreImpl(new HashMapDB());
    }

    @Override
    protected BlockStore buildBlockStore() {
        return new IndexedBlockStore(getBlockFactory(), new HashMapDB(), new HashMapBlocksIndex());
    }

    @Override
    protected TrieStore buildTrieStore(Path trieStorePath) {
        return new TrieStoreImpl(new HashMapDB());
    }

    @Override
    protected StateRootHandler buildStateRootHandler() {
        return new StateRootHandler(getRskSystemProperties().getActivationConfig(), new StateRootsStoreImpl(new HashMapDB()));
    }

    @Override
    protected Wallet buildWallet() {
        return new Wallet(new HashMapDB());
    }

    @Override
    protected KeyValueDataSource buildBlocksBloomDataSource() {
        return new HashMapDB();
    }
}
