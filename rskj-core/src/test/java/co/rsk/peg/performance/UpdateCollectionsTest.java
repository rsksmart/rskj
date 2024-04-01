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

package co.rsk.peg.performance;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.ReleaseRequestQueue;
import co.rsk.peg.PegoutsWaitingForConfirmations;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.SortedMap;

@Disabled
class UpdateCollectionsTest extends BridgePerformanceTestCase {

    private int minUTXOs = 1;
    private int maxUTXOs = 1000;
    private int minMilliBtc = 1;
    private int maxMilliBtc = 1000;
    private int minReleaseRequests = 1;
    private int maxReleaseRequests = 100;
    private int minMilliReleaseBtc = 10;
    private int maxMilliReleaseBtc = 2000;

    private int minTxsWaitingForSigs = 0;
    private int maxTxsWaitingForSigs = 10;
    private int minReleaseTxs = 10;
    private int maxReleaseTxs = 100;
    private int minBlockNumber = 10;
    private int maxBlockNumber = 100;
    private int minHeight = 50;
    private int maxHeight = 150;
    private int minCentOutput = 1;
    private int maxCentOutput = 100;

    @Test
    void updateCollections() throws IOException, VMException {
        ExecutionStats stats = new ExecutionStats("updateCollections");

        updateCollections_nothing(stats, 1000);

        minUTXOs = 1;
        maxUTXOs = 1000;
        minMilliBtc = 1;
        maxMilliBtc = 1000;
        minReleaseRequests = 1;
        maxReleaseRequests = 100;
        minMilliReleaseBtc = 10;
        maxMilliReleaseBtc = 2000;
        updateCollections_buildPegoutsWaitingForConfirmations(stats, 100);

        minTxsWaitingForSigs = 0;
        maxTxsWaitingForSigs = 10;
        minReleaseTxs = 1;
        maxReleaseTxs = 100;
        minBlockNumber = 10;
        maxBlockNumber = 100;
        minHeight = 50;
        maxHeight = 150;
        minCentOutput = 1;
        maxCentOutput = 100;
        updateCollections_confirmTxs(stats, 300);

        Assertions.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    @Test
    void updateCollectionsUsingPegoutBatching() throws IOException, VMException {
        constants = Constants.regtest();
        activationConfig = ActivationConfigsForTest.all();

        ExecutionStats stats = new ExecutionStats("updateCollectionsUsingPegoutBatching");

        updateCollections_nothing(stats, 1000);

        minUTXOs = 10;
        maxUTXOs = 1000;
        minMilliBtc = 1;
        maxMilliBtc = 1000;
        minReleaseRequests = 10;
        maxReleaseRequests = 100;
        minMilliReleaseBtc = 10;
        maxMilliReleaseBtc = 2000;
        updateCollections_buildPegoutWaitingForConfirmationsForBatchingPegouts(stats, 100);

        minTxsWaitingForSigs = 0;
        maxTxsWaitingForSigs = 10;
        minReleaseTxs = 10;
        maxReleaseTxs = 100;
        minBlockNumber = 10;
        maxBlockNumber = 100;
        minHeight = 50;
        maxHeight = 150;
        minCentOutput = 1;
        maxCentOutput = 100;
        updateCollections_confirmTxs(stats, 300);

        Assertions.assertTrue(BridgePerformanceTest.addStats(stats));
    }

    private void updateCollections_nothing(ExecutionStats stats, int numCases) throws IOException, VMException {
        final NetworkParameters parameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        final BridgeStorageProvider[] providerArrayWrapper = new BridgeStorageProvider[1];
        final Repository[] repositoryArrayWrapper = new Repository[1];
        BridgeStorageProviderInitializer storageInitializer = (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            providerArrayWrapper[0] = provider;
            repositoryArrayWrapper[0] = repository;
        };
        final byte[] updateCollectionsEncoded = Bridge.UPDATE_COLLECTIONS.encode();
        ABIEncoder abiEncoder = (int executionIndex) -> updateCollectionsEncoded;

        executeAndAverage(
            "updateCollections-nothing",
            numCases,
            abiEncoder,
            storageInitializer,
            Helper.getZeroValueValueTxBuilderFromFedMember(),
            Helper.getRandomHeightProvider(10), stats,
            (environment, executionResult) -> {
                try {
                    Assertions.assertTrue(providerArrayWrapper[0].getReleaseRequestQueue().getEntries().isEmpty());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        );
    }

    private void updateCollections_buildPegoutsWaitingForConfirmations(ExecutionStats stats, int numCases) throws IOException, VMException {
        final NetworkParameters parameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        final BridgeStorageProvider[] providerArrayWrapper = new BridgeStorageProvider[1];
        BridgeStorageProviderInitializer storageInitializer = (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            Random rnd = new Random(numCases);
            List<UTXO> utxos;
            ReleaseRequestQueue queue;

            providerArrayWrapper[0] = provider;

            try {
                utxos = provider.getNewFederationBtcUTXOs();
            } catch (Exception e) {
                throw new RuntimeException("Unable to gather active federation btc utxos");
            }

            try {
                queue = provider.getReleaseRequestQueue();
            } catch (Exception e) {
                throw new RuntimeException("Unable to gather release request queue");
            }

            // Generate some utxos
            int numUTXOs = Helper.randomInRange(minUTXOs, maxUTXOs);

            Script federationScript = BridgeRegTestConstants.getInstance().getGenesisFederation().getP2SHScript();

            for (int i = 0; i < numUTXOs; i++) {
                Sha256Hash hash = Sha256Hash.wrap(HashUtil.sha256(BigInteger.valueOf(rnd.nextLong()).toByteArray()));
                Coin value = Coin.MILLICOIN.multiply(Helper.randomInRange(minMilliBtc, maxMilliBtc));
                utxos.add(new UTXO(hash, 0, value, 1, false, federationScript));
            }

            // Generate some release requests to process
            for (int i = 0; i < Helper.randomInRange(minReleaseRequests, maxReleaseRequests); i++) {
                Coin value = Coin.MILLICOIN.multiply(Helper.randomInRange(minMilliReleaseBtc, maxMilliReleaseBtc));
                queue.add(
                    new BtcECKey().toAddress(parameters),
                    value,
                    null
                );
            }
        };

        final byte[] updateCollectionsEncoded = Bridge.UPDATE_COLLECTIONS.encode();
        ABIEncoder abiEncoder = (int executionIndex) -> updateCollectionsEncoded;

        executeAndAverage(
            "updateCollections-releaseRequests",
            numCases,
            abiEncoder,
            storageInitializer,
            Helper.getZeroValueValueTxBuilderFromFedMember(),
            Helper.getRandomHeightProvider(10),
            stats,
            (environment, executionResult) -> {
                try {
                    Assertions.assertFalse(providerArrayWrapper[0].getReleaseRequestQueue().getEntries().isEmpty());
                } catch (IOException e) {
                    Assertions.fail(e.getMessage());
                }
            }
        );
    }

    private void updateCollections_confirmTxs(ExecutionStats stats, int numCases) throws IOException, VMException {
        BridgeStorageProviderInitializer storageInitializer = (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            Random rnd = new Random(numCases);
            SortedMap<Keccak256, BtcTransaction> txsWaitingForSignatures;
            PegoutsWaitingForConfirmations txSet;

            try {
                txsWaitingForSignatures = provider.getPegoutsWaitingForSignatures();
            } catch (Exception e) {
                throw new RuntimeException("Unable to gather txs waiting for signatures");
            }

            try {
                txSet = provider.getPegoutsWaitingForConfirmations();
            } catch (Exception e) {
                throw new RuntimeException("Unable to gather release tx set");
            }

            // Generate some txs waiting for signatures
            Script genesisFederationScript = bridgeConstants.getGenesisFederation().getP2SHScript();
            for (int i = 0; i < Helper.randomInRange(minTxsWaitingForSigs, maxTxsWaitingForSigs); i++) {
                Keccak256 rskHash = new Keccak256(HashUtil.keccak256(BigInteger.valueOf(rnd.nextLong()).toByteArray()));
                BtcTransaction btcTx = new BtcTransaction(networkParameters);
                Sha256Hash inputHash = Sha256Hash.wrap(HashUtil.sha256(BigInteger.valueOf(rnd.nextLong()).toByteArray()));
                btcTx.addInput(inputHash, 0, genesisFederationScript);
                btcTx.addOutput(Helper.randomCoin(Coin.CENT, minCentOutput, maxCentOutput), new BtcECKey());
                txsWaitingForSignatures.put(rskHash, btcTx);
            }

            // Generate some txs waiting for confirmations
            for (int i = 0; i < Helper.randomInRange(minReleaseTxs, maxReleaseTxs); i++) {
                BtcTransaction btcTx = new BtcTransaction(networkParameters);
                Sha256Hash inputHash = Sha256Hash.wrap(HashUtil.sha256(BigInteger.valueOf(rnd.nextLong()).toByteArray()));
                btcTx.addInput(inputHash, 0, genesisFederationScript);
                btcTx.addOutput(Helper.randomCoin(Coin.CENT, minCentOutput, maxCentOutput), new BtcECKey());
                long blockNumber = Helper.randomInRange(minBlockNumber, maxBlockNumber);
                txSet.add(btcTx, blockNumber);
            }
        };

        final byte[] updateCollectionsEncoded = Bridge.UPDATE_COLLECTIONS.encode();
        ABIEncoder abiEncoder = (int executionIndex) -> updateCollectionsEncoded;
        HeightProvider heightProvider = (int executionIndex) -> Helper.randomInRange(minHeight, maxHeight);

        executeAndAverage(
            "updateCollections-pegoutsWaitingForConfirmations",
            numCases,
            abiEncoder,
            storageInitializer,
            Helper.getZeroValueValueTxBuilderFromFedMember(),
            heightProvider,
            stats
        );
    }

    private void updateCollections_buildPegoutWaitingForConfirmationsForBatchingPegouts(ExecutionStats stats, int numCases) throws IOException, VMException {
        final NetworkParameters parameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        BridgeStorageProviderInitializer storageInitializer = (BridgeStorageProvider provider, Repository repository, int executionIndex, BtcBlockStore blockStore) -> {
            Random rnd = new Random(numCases);
            List<UTXO> utxos;
            ReleaseRequestQueue queue;

            try {
                utxos = provider.getNewFederationBtcUTXOs();
            } catch (Exception e) {
                throw new RuntimeException("Unable to gather active federation btc utxos");
            }

            try {
                queue = provider.getReleaseRequestQueue();
            } catch (Exception e) {
                throw new RuntimeException("Unable to gather release request queue");
            }

            // Generate some utxos
            int numUTXOs = Helper.randomInRange(minUTXOs, maxUTXOs);

            Script federationScript = BridgeRegTestConstants.getInstance().getGenesisFederation().getP2SHScript();

            for (int i = 0; i < numUTXOs; i++) {
                Sha256Hash hash = Sha256Hash.wrap(HashUtil.sha256(BigInteger.valueOf(rnd.nextLong()).toByteArray()));
                Coin value = Coin.MILLICOIN.multiply(Helper.randomInRange(minMilliBtc, maxMilliBtc));
                utxos.add(new UTXO(hash, 0, value, 1, false, federationScript));
            }

            // Generate some release requests to process
            for (int i = 0; i < Helper.randomInRange(minReleaseRequests, maxReleaseRequests); i++) {
                Coin value = Coin.MILLICOIN.multiply(Helper.randomInRange(minMilliReleaseBtc, maxMilliReleaseBtc));
                queue.add(
                    new BtcECKey().toAddress(parameters),
                    value,
                    PegTestUtils.createHash3(rnd.nextInt())
                );
            }
        };

        final byte[] updateCollectionsEncoded = Bridge.UPDATE_COLLECTIONS.encode();
        ABIEncoder abiEncoder = (int executionIndex) -> updateCollectionsEncoded;

        executeAndAverage(
            "updateCollections-releaseRequests",
            numCases,
            abiEncoder,
            storageInitializer,
            Helper.getZeroValueValueTxBuilderFromFedMember(),
            Helper.getRandomHeightProvider(10),
            stats
        );
    }
}
