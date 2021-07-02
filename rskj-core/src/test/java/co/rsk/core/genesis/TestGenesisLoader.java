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

package co.rsk.core.genesis;

import co.rsk.db.StateRootHandler;
import co.rsk.db.StateRootsStoreImpl;
import co.rsk.trie.TrieStore;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.genesis.GenesisLoaderImpl;
import org.ethereum.datasource.HashMapDB;

import java.io.InputStream;
import java.math.BigInteger;

/**
 * This is a GenesisLoader which hardcodes context information. It is here for compatibility with older tests and usage
 * on new code is strongly discouraged.
 */
@Deprecated
public class TestGenesisLoader extends GenesisLoaderImpl {
    public TestGenesisLoader(
            TrieStore trieStore,
            String genesisFile,
            BigInteger initialNonce,
            boolean isRsk,
            boolean useRskip92Encoding,
            boolean isRskip126Enabled) {
        this(
                trieStore,
                GenesisLoaderImpl.class.getResourceAsStream("/genesis/" + genesisFile),
                initialNonce,
                isRsk,
                useRskip92Encoding,
                isRskip126Enabled
        );
    }

    public TestGenesisLoader(
            TrieStore trieStore,
            InputStream resourceAsStream,
            BigInteger initialNonce,
            boolean isRsk,
            boolean useRskip92Encoding,
            boolean isRskip126Enabled) {
        this(
                trieStore,
                ActivationConfigsForTest.regtest(),
                initialNonce,
                resourceAsStream,
                isRsk,
                useRskip92Encoding,
                isRskip126Enabled
        );
    }

    private TestGenesisLoader(
            TrieStore trieStore,
            ActivationConfig activationConfig,
            BigInteger initialNonce,
            InputStream resourceAsStream,
            boolean isRsk,
            boolean useRskip92Encoding,
            boolean isRskip126Enabled) {
        super(
                activationConfig,
                new StateRootHandler(activationConfig, new StateRootsStoreImpl(new HashMapDB())),
                trieStore,
                resourceAsStream,
                initialNonce,
                isRsk,
                useRskip92Encoding,
                isRskip126Enabled
        );
    }
}
