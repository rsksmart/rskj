/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.peg;

import co.rsk.peg.constants.BridgeConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Created by ajlopez on 16/04/2017.
 */
class BridgeStateTest {
    @Test
    void recreateFromEmptyStorageProvider() throws IOException {
        TestSystemProperties config = new TestSystemProperties();
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));
        BridgeConstants bridgeConstants = config.getNetworkConstants().getBridgeConstants();
        BridgeStorageProvider provider = new BridgeStorageProvider(
                repository, PrecompiledContracts.BRIDGE_ADDR,
                bridgeConstants, config.getActivationConfig().forBlock(0L)
        );

        BridgeState state = new BridgeState(42, provider, null);

        BridgeState clone = BridgeState.create(bridgeConstants, state.getEncoded(), null);

        Assertions.assertNotNull(clone);
        Assertions.assertEquals(42, clone.getBtcBlockchainBestChainHeight());
        Assertions.assertEquals(0, clone.getNextPegoutCreationBlockNumber());
        Assertions.assertTrue(clone.getActiveFederationBtcUTXOs().isEmpty());
        Assertions.assertTrue(clone.getRskTxsWaitingForSignatures().isEmpty());
    }
}
