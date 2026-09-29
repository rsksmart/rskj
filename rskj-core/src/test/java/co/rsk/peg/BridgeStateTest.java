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

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.federation.FederationStorageProvider;
import co.rsk.peg.federation.FederationStorageProviderImpl;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.junit.jupiter.api.Assertions;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.LegacyAddress;
import co.rsk.bitcoinj.core.SegwitAddress;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.bouncycastle.util.encoders.Hex;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.SortedMap;

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

        NetworkParameters networkParameters = bridgeConstants.getBtcParams();
        ActivationConfig.ForBlock activations = config.getActivationConfig().forBlock(0L);

        BridgeStorageProvider bridgeStorageProvider = new BridgeStorageProvider(repository, networkParameters, activations);
        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);
        FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

        int btcBlockchainBestChainHeight = 42;
        long nextPegoutCreationBlockNumber = bridgeStorageProvider.getNextPegoutHeight().get();
        List<UTXO> activeFederationBtcUTXOs = federationStorageProvider.getNewFederationBtcUTXOs(networkParameters, activations);
        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = bridgeStorageProvider.getPegoutsWaitingForSignatures();
        ReleaseRequestQueue releaseRequestQueue = bridgeStorageProvider.getReleaseRequestQueue();
        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = bridgeStorageProvider.getPegoutsWaitingForConfirmations();

        BridgeState state = new BridgeState(btcBlockchainBestChainHeight, nextPegoutCreationBlockNumber, activeFederationBtcUTXOs, rskTxsWaitingForSignatures, releaseRequestQueue, pegoutsWaitingForConfirmations, null);

        BridgeState clone = BridgeState.create(bridgeConstants, state.getEncoded(), null);

        Assertions.assertNotNull(clone);
        Assertions.assertEquals(42, clone.getBtcBlockchainBestChainHeight());
        Assertions.assertEquals(0, clone.getNextPegoutCreationBlockNumber());
        Assertions.assertTrue(clone.getActiveFederationBtcUTXOs().isEmpty());
        Assertions.assertTrue(clone.getRskTxsWaitingForSignatures().isEmpty());
    }

    /**
     * From RSKIP690 the encoding grows a seventh element holding the whole queue as address
     * strings. Element 3 keeps the old six-element shape so a decoder written against it still
     * parses, and it is filtered to the entries that shape can represent: it stores a bare hash and
     * stamps the P2PKH header on read, so a P2SH destination would come back as an address nobody
     * controls.
     */
    @Test
    void getEncoded_afterRskip690_appendsTheQueueAndFiltersTheLegacyElement() throws IOException {
        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        byte[] pubKeyHash = Hex.decode("f7ee9ab7297134a0ccc76f3d50e94def17488f2c");
        byte[] taprootProgram =
            Hex.decode("4c679657ca8d4aa7e29deaaaba90463a2af9e182012791112634c4d585b324a7");

        ReleaseRequestQueue queue = new ReleaseRequestQueue(new ArrayList<>());
        queue.add(new LegacyAddress(params, pubKeyHash), Coin.COIN, PegTestUtils.createHash3(1));
        queue.add(LegacyAddress.fromP2SHHash(params, pubKeyHash), Coin.COIN, PegTestUtils.createHash3(2));
        queue.add(SegwitAddress.fromHash(params, pubKeyHash), Coin.COIN, PegTestUtils.createHash3(3));
        queue.add(SegwitAddress.fromProgram(params, 1, taprootProgram), Coin.COIN, PegTestUtils.createHash3(4));

        RLPList afterActivation = decodeState(stateWith(queue, ActivationConfigsForTest.all().forBlock(0L)));
        RLPList beforeActivation = decodeState(stateWith(queue, ActivationConfigsForTest.papyrus200().forBlock(0L)));

        Assertions.assertEquals(7, afterActivation.size());
        Assertions.assertEquals(6, beforeActivation.size());

        // element 3 carries only the P2PKH entry, element 7 carries all four
        Assertions.assertEquals(1, countEntries(afterActivation.get(3).getRLPData(), 3));
        Assertions.assertEquals(4, countEntries(afterActivation.get(6).getRLPData(), 3));
    }

    private byte[] stateWith(ReleaseRequestQueue queue, ActivationConfig.ForBlock activations)
        throws IOException {

        return new BridgeState(
            1, 1L, new ArrayList<>(), new TreeMap<>(), queue,
            new PegoutsWaitingForConfirmations(new HashSet<>()), activations).getEncoded();
    }

    private RLPList decodeState(byte[] encoded) {
        return (RLPList) RLP.decode2(encoded).get(0);
    }

    private int countEntries(byte[] serializedQueue, int elementsPerEntry) {
        if (serializedQueue == null || serializedQueue.length == 0) {
            return 0;
        }

        return ((RLPList) RLP.decode2(serializedQueue).get(0)).size() / elementsPerEntry;
    }
}
