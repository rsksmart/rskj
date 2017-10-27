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

import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import co.rsk.db.RepositoryImpl;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by ajlopez on 16/04/2017.
 */
public class BridgeStateTest {
    @Test
    public void recreateFromEmptyStorageProvider() throws IOException {
        Repository repository = new RepositoryImpl(new TrieStoreImpl(new HashMapDB()));
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        BridgeState state = new BridgeState(42, provider);

        BridgeState clone = BridgeState.create(state.getEncoded());

        Assert.assertNotNull(clone);
        Assert.assertEquals(42, clone.getBtcBlockchainBestChainHeight());
        Assert.assertTrue(clone.getBtcTxHashesAlreadyProcessed().isEmpty());
        Assert.assertTrue(clone.getActiveFederationBtcUTXOs().isEmpty());
        Assert.assertTrue(clone.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(clone.getRskTxsWaitingForSignatures().isEmpty());
    }
}
