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
import co.rsk.db.MutableTrieImpl;
import co.rsk.db.StateRootHandler;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.*;

import java.util.HashMap;

/**
 * This context overrides every persistent database access with a non-persistent one.
 * It is the closest to a production context that one can use for testing.
 */
public class RskTestContext extends RskContext {
    public RskTestContext(String[] args) {
        super(args);
    }

    @Override
    protected ReceiptStore buildReceiptStore() {
        return new ReceiptStoreImpl(new HashMapDB());
    }

    @Override
    protected BlockStore buildBlockStore() {
        return new IndexedBlockStore(getBlockFactory(), new HashMap<>(), new HashMapDB(), null);
    }

    @Override
    protected Repository buildRepository() {
        return new MutableRepository(new MutableTrieImpl(new Trie(new TrieStoreImpl(new HashMapDB()))));
    }

    @Override
    protected StateRootHandler buildStateRootHandler() {
        return new StateRootHandler(getRskSystemProperties().getActivationConfig(), getTrieConverter(), new HashMapDB(), new HashMap<>());
    }

    @Override
    protected Wallet buildWallet() {
        return new Wallet(new HashMapDB());
    }
}
