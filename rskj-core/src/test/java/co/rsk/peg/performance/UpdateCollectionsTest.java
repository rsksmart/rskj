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

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.ReleaseRequestQueue;
import co.rsk.peg.ReleaseTransactionSet;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import java.util.SortedMap;

@Ignore
public class UpdateCollectionsTest extends BridgePerformanceTestCase {
    @Test
    public void updateCollections() throws IOException {
        ExecutionStats stats = new ExecutionStats("updateCollections");

        updateCollections_nothing(stats, 1000);
        updateCollections_buildReleaseTxs(stats, 100);
        updateCollections_confirmTxs(stats, 300);

        BridgePerformanceTest.addStats(stats);
    }

    private void updateCollections_nothing(ExecutionStats stats, int numCases) throws IOException {
        final NetworkParameters parameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        BridgeStorageProviderInitializer storageInitializer = (BridgeStorageProvider provider, Repository repository, int executionIndex) -> {};
        final byte[] updateCollectionsEncoded = Bridge.UPDATE_COLLECTIONS.encode();
        ABIEncoder abiEncoder = (int executionIndex) -> updateCollectionsEncoded;

        executeAndAverage(
                "updateCollections-nothing",
                numCases,
                abiEncoder,
                storageInitializer,
                Helper.getZeroValueRandomSenderTxBuilder(),
                Helper.getRandomHeightProvider(10), stats
        );
    }

    private void updateCollections_buildReleaseTxs(ExecutionStats stats, int numCases) throws IOException {
        final int minUTXOs = 1;
        final int maxUTXOs = 1000;
        final int minMilliBtc = 1;
        final int maxMilliBtc = 1000;
        final int minReleaseRequests = 1;
        final int maxReleaseRequests = 100;
        final int minMilliReleaseBtc = 10;
        final int maxMilliReleaseBtc = 2000;

        final NetworkParameters parameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        BridgeStorageProviderInitializer storageInitializer = (BridgeStorageProvider provider, Repository repository, int executionIndex) -> {
            Random rnd = new Random();
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
                queue.add(new BtcECKey().toAddress(parameters), value);
            }
        };

        final byte[] updateCollectionsEncoded = Bridge.UPDATE_COLLECTIONS.encode();
        ABIEncoder abiEncoder = (int executionIndex) -> updateCollectionsEncoded;

        executeAndAverage(
                "updateCollections-releaseRequests",
                numCases,
                abiEncoder,
                storageInitializer,
                Helper.getZeroValueRandomSenderTxBuilder(),
                Helper.getRandomHeightProvider(10),
                stats
        );
    }

    private void updateCollections_confirmTxs(ExecutionStats stats, int numCases) throws IOException {
        final int minTxsWaitingForSigs = 0;
        final int maxTxsWaitingForSigs = 10;
        final int minReleaseTxs = 1;
        final int maxReleaseTxs = 100;
        final int minBlockNumber = 10;
        final int maxBlockNumber = 100;
        final int minHeight = 50;
        final int maxHeight = 150;
        final int minCentOutput = 1;
        final int maxCentOutput = 100;

        final NetworkParameters parameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        BridgeStorageProviderInitializer storageInitializer = (BridgeStorageProvider provider, Repository repository, int executionIndex) -> {
            Random rnd = new Random();
            SortedMap<Keccak256, BtcTransaction> txsWaitingForSignatures;
            ReleaseTransactionSet txSet;

            try {
                txsWaitingForSignatures = provider.getRskTxsWaitingForSignatures();
            } catch (Exception e) {
                throw new RuntimeException("Unable to gather txs waiting for signatures");
            }

            try {
                txSet = provider.getReleaseTransactionSet();
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
                "updateCollections-releaseTxs",
                numCases,
                abiEncoder,
                storageInitializer,
                Helper.getZeroValueRandomSenderTxBuilder(),
                heightProvider,
                stats
        );
    }
}
