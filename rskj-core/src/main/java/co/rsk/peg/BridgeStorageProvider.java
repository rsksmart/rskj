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

import co.rsk.config.BridgeConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Sha3Hash;
import co.rsk.peg.bitcoin.RskAllowUnconfirmedCoinSelector;
import org.apache.commons.lang3.tuple.Pair;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.wallet.Wallet;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * Provides an object oriented facade of the bridge contract memory.
 * @see co.rsk.remasc.RemascStorageProvider
 * Created by ajlopez on 6/7/2016.
 */
public class BridgeStorageProvider {
    private static final String BTC_UTXOS_KEY = "btcUTXOs";
    private static final String BTC_TX_HASHES_ALREADY_PROCESSED_KEY = "btcTxHashesAP";
    private static final String RSK_TXS_WAITING_FOR_CONFIRMATIONS_KEY = "rskTxsWaitingFC";
    private static final String RSK_TXS_WAITING_FOR_SIGNATURES_KEY = "rskTxsWaitingFS";
    private static final String RSK_TXS_WAITING_FOR_BROADCASTING_KEY = "rskTxsWaitingFB";

    private static final NetworkParameters networkParameters = RskSystemProperties.RSKCONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();

    private Repository repository;
    private String contractAddress;

    private SortedSet<Sha256Hash> btcTxHashesAlreadyProcessed;
    // RSK release txs follow these steps: First, they are waiting for RSK confirmations, then they are waiting for federators' signatures,
    // then they are waiting for broadcasting in the bitcoin network (a tx is kept in this state for a while, even if already broadcasted, giving the chance to federators to rebroadcast it just in case),
    // then they are removed from contract's memory.
    // key = rsk tx hash, value = btc tx
    private SortedMap<Sha3Hash, BtcTransaction> rskTxsWaitingForConfirmations;
    // key = rsk tx hash, value = btc tx
    private SortedMap<Sha3Hash, BtcTransaction> rskTxsWaitingForSignatures;
    // key = rsk tx hash, value = btc tx and block when it was ready for broadcasting
    private SortedMap<Sha3Hash, Pair<BtcTransaction, Long>> rskTxsWaitingForBroadcasting;

    private List<UTXO> btcUTXOs;
    private Wallet btcWallet;

    private BridgeConstants bridgeConstants;
    private Context btcContext;

    public BridgeStorageProvider(Repository repository, String contractAddress) {
        this.repository = repository;
        this.contractAddress = contractAddress;
        bridgeConstants = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        btcContext = new Context(bridgeConstants.getBtcParams());
    }

    public Wallet getWallet() throws IOException {
        if (btcWallet != null)
            return btcWallet;

        List<UTXO> btcUTXOs = this.getBtcUTXOs();

        RskUTXOProvider utxoProvider = new RskUTXOProvider(bridgeConstants.getBtcParams(), btcUTXOs);

        btcWallet = new BridgeBtcWallet(btcContext, bridgeConstants);
        btcWallet.setUTXOProvider(utxoProvider);
        btcWallet.addWatchedAddress(bridgeConstants.getFederationAddress(), bridgeConstants.getFederationAddressCreationTime());
        btcWallet.setCoinSelector(new RskAllowUnconfirmedCoinSelector());
//      Oscar: Comment out these setting since we now have our own bitcoinj wallet and we disabled these features
//      I leave the code here just in case we decide to rollback to use the full original bitcoinj Wallet
//        "btcWallet.setKeyChainGroupLookaheadSize(1);"
//        "btcWallet.setKeyChainGroupLookaheadThreshold(0);"
//        "btcWallet.setAcceptRiskyTransactions(true);"
        return btcWallet;
    }

    public List<UTXO> getBtcUTXOs() throws IOException {
        if (btcUTXOs!= null)
            return btcUTXOs;

        DataWord address = new DataWord(BTC_UTXOS_KEY.getBytes(StandardCharsets.UTF_8));

        byte[] data = repository.getStorageBytes(Hex.decode(contractAddress), address);

        btcUTXOs = BridgeSerializationUtils.deserializeList(data);

        return btcUTXOs;
    }

    public void saveBtcUTXOs() throws IOException {
        if (btcUTXOs == null)
            return;

        byte[] data = BridgeSerializationUtils.serializeList(btcUTXOs);

        DataWord address = new DataWord(BTC_UTXOS_KEY.getBytes(StandardCharsets.UTF_8));

        repository.addStorageBytes(Hex.decode(contractAddress), address, data);
    }

    public SortedSet<Sha256Hash> getBtcTxHashesAlreadyProcessed() throws IOException {
        if (btcTxHashesAlreadyProcessed != null)
            return btcTxHashesAlreadyProcessed;

        DataWord address = new DataWord(BTC_TX_HASHES_ALREADY_PROCESSED_KEY.getBytes(StandardCharsets.UTF_8));

        byte[] data = repository.getStorageBytes(Hex.decode(contractAddress), address);

        btcTxHashesAlreadyProcessed = BridgeSerializationUtils.deserializeSet(data);

        return btcTxHashesAlreadyProcessed;
    }

    public void saveBtcTxHashesAlreadyProcessed() {
        if (btcTxHashesAlreadyProcessed == null)
            return;

        byte[] data = BridgeSerializationUtils.serializeSet(btcTxHashesAlreadyProcessed);

        DataWord address = new DataWord(BTC_TX_HASHES_ALREADY_PROCESSED_KEY.getBytes(StandardCharsets.UTF_8));

        repository.addStorageBytes(Hex.decode(contractAddress), address, data);
    }

    public SortedMap<Sha3Hash, BtcTransaction> getRskTxsWaitingForConfirmations() throws IOException {
        if (rskTxsWaitingForConfirmations != null)
            return rskTxsWaitingForConfirmations;

        DataWord address = new DataWord(RSK_TXS_WAITING_FOR_CONFIRMATIONS_KEY.getBytes(StandardCharsets.UTF_8));

        byte[] data = repository.getStorageBytes(Hex.decode(contractAddress), address);

        rskTxsWaitingForConfirmations = BridgeSerializationUtils.deserializeMap(data, networkParameters);

        return rskTxsWaitingForConfirmations;
    }

    public void saveRskTxsWaitingForConfirmations() {
        if (rskTxsWaitingForConfirmations == null)
            return;

        byte[] data = BridgeSerializationUtils.serializeMap(rskTxsWaitingForConfirmations);

        DataWord address = new DataWord(RSK_TXS_WAITING_FOR_CONFIRMATIONS_KEY.getBytes(StandardCharsets.UTF_8));

        repository.addStorageBytes(Hex.decode(contractAddress), address, data);
    }

    public SortedMap<Sha3Hash, BtcTransaction> getRskTxsWaitingForSignatures() throws IOException {
        if (rskTxsWaitingForSignatures != null)
            return rskTxsWaitingForSignatures;

        DataWord address = new DataWord(RSK_TXS_WAITING_FOR_SIGNATURES_KEY.getBytes(StandardCharsets.UTF_8));

        byte[] data = repository.getStorageBytes(Hex.decode(contractAddress), address);

        rskTxsWaitingForSignatures = BridgeSerializationUtils.deserializeMap(data, networkParameters);

        return rskTxsWaitingForSignatures;
    }

    public void saveRskTxsWaitingForSignatures() {
        if (rskTxsWaitingForSignatures == null)
            return;

        byte[] data = BridgeSerializationUtils.serializeMap(rskTxsWaitingForSignatures);

        DataWord address = new DataWord(RSK_TXS_WAITING_FOR_SIGNATURES_KEY.getBytes(StandardCharsets.UTF_8));

        repository.addStorageBytes(Hex.decode(contractAddress), address, data);
    }

    public SortedMap<Sha3Hash, Pair<BtcTransaction, Long>> getRskTxsWaitingForBroadcasting() throws IOException {
        if (rskTxsWaitingForBroadcasting != null)
            return rskTxsWaitingForBroadcasting;

        DataWord address = new DataWord(RSK_TXS_WAITING_FOR_BROADCASTING_KEY.getBytes(StandardCharsets.UTF_8));

        byte[] data = repository.getStorageBytes(Hex.decode(contractAddress), address);

        rskTxsWaitingForBroadcasting = BridgeSerializationUtils.deserializePairMap(data, networkParameters);

        return rskTxsWaitingForBroadcasting;
    }

    public void saveRskTxsWaitingForBroadcasting() {
        if (rskTxsWaitingForBroadcasting == null)
            return;

        byte[] data = BridgeSerializationUtils.serializePairMap(rskTxsWaitingForBroadcasting);

        DataWord address = new DataWord(RSK_TXS_WAITING_FOR_BROADCASTING_KEY.getBytes(StandardCharsets.UTF_8));

        repository.addStorageBytes(Hex.decode(contractAddress), address, data);
    }

    public void save() throws IOException {
        saveBtcUTXOs();
        saveBtcTxHashesAlreadyProcessed();
        saveRskTxsWaitingForConfirmations();
        saveRskTxsWaitingForSignatures();
        saveRskTxsWaitingForBroadcasting();
    }


}
