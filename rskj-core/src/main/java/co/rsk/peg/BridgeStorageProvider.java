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

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Sha3Hash;
import co.rsk.peg.bitcoin.RskAllowUnconfirmedCoinSelector;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Provides an object oriented facade of the bridge contract memory.
 * @see co.rsk.remasc.RemascStorageProvider
 * @author ajlopez
 * @author Oscar Guindzberg
 */
public class BridgeStorageProvider {
    private static final String ACTIVE_FEDERATION_BTC_UTXOS_KEY = "activeFederationBtcUTXOs";
    private static final String RETIRING_FEDERATION_BTC_UTXOS_KEY = "retiringFederationBtcUTXOs";
    private static final String BTC_TX_HASHES_ALREADY_PROCESSED_KEY = "btcTxHashesAP";
    private static final String RSK_TXS_WAITING_FOR_CONFIRMATIONS_KEY = "rskTxsWaitingFC";
    private static final String RSK_TXS_WAITING_FOR_SIGNATURES_KEY = "rskTxsWaitingFS";
    private static final String BRIDGE_ACTIVE_FEDERATION_KEY = "bridgeActiveFederation";
    private static final String BRIDGE_RETIRING_FEDERATION_KEY = "bridgeRetiringFederation";
    private static final String BRIDGE_PENDING_FEDERATION_KEY = "bridgePendingFederation";

    private static final NetworkParameters networkParameters = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();

    private Repository repository;
    private String contractAddress;

    private Map<Sha256Hash, Long> btcTxHashesAlreadyProcessed;
    // RSK release txs follow these steps: First, they are waiting for RSK confirmations, then they are waiting for federators' signatures,
    // then they are waiting for broadcasting in the bitcoin network (a tx is kept in this state for a while, even if already broadcasted, giving the chance to federators to rebroadcast it just in case),
    // then they are removed from contract's memory.
    // key = rsk tx hash, value = btc tx
    private SortedMap<Sha3Hash, BtcTransaction> rskTxsWaitingForConfirmations;
    // key = rsk tx hash, value = btc tx
    private SortedMap<Sha3Hash, BtcTransaction> rskTxsWaitingForSignatures;

    private List<UTXO> activeFederationBtcUTXOs;
    private List<UTXO> retiringFederationBtcUTXOs;

    private Federation activeFederation;
    private Federation retiringFederation;
    private PendingFederation pendingFederation;

    private BridgeConstants bridgeConstants;
    private Context btcContext;

    public BridgeStorageProvider(Repository repository, String contractAddress) {
        this.repository = repository;
        this.contractAddress = contractAddress;
        bridgeConstants = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        btcContext = new Context(bridgeConstants.getBtcParams());
    }

    public List<UTXO> getActiveFederationBtcUTXOs() throws IOException {
        if (activeFederationBtcUTXOs != null)
            return activeFederationBtcUTXOs;

        DataWord address = new DataWord(ACTIVE_FEDERATION_BTC_UTXOS_KEY.getBytes(StandardCharsets.UTF_8));

        byte[] data = repository.getStorageBytes(Hex.decode(contractAddress), address);

        activeFederationBtcUTXOs = BridgeSerializationUtils.deserializeUTXOList(data);

        return activeFederationBtcUTXOs;
    }

    public void saveActiveFederationBtcUTXOs() throws IOException {
        if (activeFederationBtcUTXOs == null)
            return;

        byte[] data = BridgeSerializationUtils.serializeUTXOList(activeFederationBtcUTXOs);

        DataWord address = new DataWord(ACTIVE_FEDERATION_BTC_UTXOS_KEY.getBytes(StandardCharsets.UTF_8));

        repository.addStorageBytes(Hex.decode(contractAddress), address, data);
    }

    public List<UTXO> getRetiringFederationBtcUTXOs() throws IOException {
        if (retiringFederationBtcUTXOs != null)
            return retiringFederationBtcUTXOs;

        DataWord address = new DataWord(RETIRING_FEDERATION_BTC_UTXOS_KEY.getBytes(StandardCharsets.UTF_8));

        byte[] data = repository.getStorageBytes(Hex.decode(contractAddress), address);

        retiringFederationBtcUTXOs = BridgeSerializationUtils.deserializeUTXOList(data);

        return retiringFederationBtcUTXOs;
    }

    public void saveRetiringFederationBtcUTXOs() throws IOException {
        if (retiringFederationBtcUTXOs == null)
            return;

        byte[] data = BridgeSerializationUtils.serializeUTXOList(retiringFederationBtcUTXOs);

        DataWord address = new DataWord(RETIRING_FEDERATION_BTC_UTXOS_KEY.getBytes(StandardCharsets.UTF_8));

        repository.addStorageBytes(Hex.decode(contractAddress), address, data);
    }

    public Map<Sha256Hash, Long> getBtcTxHashesAlreadyProcessed() throws IOException {
        if (btcTxHashesAlreadyProcessed != null)
            return btcTxHashesAlreadyProcessed;

        DataWord address = new DataWord(BTC_TX_HASHES_ALREADY_PROCESSED_KEY.getBytes(StandardCharsets.UTF_8));

        byte[] data = repository.getStorageBytes(Hex.decode(contractAddress), address);

        btcTxHashesAlreadyProcessed = BridgeSerializationUtils.deserializeMapOfHashesToLong(data);

        return btcTxHashesAlreadyProcessed;
    }

    public void saveBtcTxHashesAlreadyProcessed() {
        if (btcTxHashesAlreadyProcessed == null)
            return;

        byte[] data = BridgeSerializationUtils.serializeMapOfHashesToLong(btcTxHashesAlreadyProcessed);

        DataWord address = new DataWord(BTC_TX_HASHES_ALREADY_PROCESSED_KEY.getBytes(StandardCharsets.UTF_8));

        repository.addStorageBytes(Hex.decode(contractAddress), address, data);
    }

    public SortedMap<Sha3Hash, BtcTransaction> getRskTxsWaitingForConfirmations() throws IOException {
        if (rskTxsWaitingForConfirmations != null)
            return rskTxsWaitingForConfirmations;

        DataWord address = new DataWord(RSK_TXS_WAITING_FOR_CONFIRMATIONS_KEY.getBytes(StandardCharsets.UTF_8));

        byte[] data = repository.getStorageBytes(Hex.decode(contractAddress), address);

        rskTxsWaitingForConfirmations = BridgeSerializationUtils.deserializeMap(data, networkParameters, true);

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

        rskTxsWaitingForSignatures = BridgeSerializationUtils.deserializeMap(data, networkParameters, false);

        return rskTxsWaitingForSignatures;
    }

    public void saveRskTxsWaitingForSignatures() {
        if (rskTxsWaitingForSignatures == null)
            return;

        byte[] data = BridgeSerializationUtils.serializeMap(rskTxsWaitingForSignatures);

        DataWord address = new DataWord(RSK_TXS_WAITING_FOR_SIGNATURES_KEY.getBytes(StandardCharsets.UTF_8));

        repository.addStorageBytes(Hex.decode(contractAddress), address, data);
    }

    public Federation getActiveFederation() {
        if (activeFederation != null)
            return activeFederation;

        DataWord address = new DataWord(BRIDGE_ACTIVE_FEDERATION_KEY.getBytes(StandardCharsets.UTF_8));

        byte[] data = repository.getStorageBytes(Hex.decode(contractAddress), address);

        if (data == null)
            return null;

        activeFederation = BridgeSerializationUtils.deserializeFederation(data, btcContext);

        return activeFederation;
    }

    public void setActiveFederation(Federation federation) {
        activeFederation = federation;
    }

    /**
     * Save the active federation
     * Only saved if a federation was set with BridgeStorageProvider::setActiveFederation
     */
    public void saveActiveFederation() {
        if (activeFederation == null)
            return;

        byte[] data = BridgeSerializationUtils.serializeFederation(activeFederation);

        DataWord address = new DataWord(BRIDGE_ACTIVE_FEDERATION_KEY.getBytes(StandardCharsets.UTF_8));

        repository.addStorageBytes(Hex.decode(contractAddress), address, data);
    }

    public Federation getRetiringFederation() {
        if (retiringFederation != null)
            return retiringFederation;

        DataWord address = new DataWord(BRIDGE_RETIRING_FEDERATION_KEY.getBytes(StandardCharsets.UTF_8));

        byte[] data = repository.getStorageBytes(Hex.decode(contractAddress), address);

        if (data == null)
            return null;

        retiringFederation = BridgeSerializationUtils.deserializeFederation(data, btcContext);

        return retiringFederation;
    }

    public void setRetiringFederation(Federation federation) {
        retiringFederation = federation;
    }

    /**
     * Save the retiring federation
     */
    public void saveRetiringFederation() {
        DataWord address = new DataWord(BRIDGE_RETIRING_FEDERATION_KEY.getBytes(StandardCharsets.UTF_8));

        if (retiringFederation == null) {
            repository.addStorageBytes(Hex.decode(contractAddress), address, null);
            return;
        }

        byte[] data = BridgeSerializationUtils.serializeFederation(retiringFederation);


        repository.addStorageBytes(Hex.decode(contractAddress), address, data);
    }



    public PendingFederation getPendingFederation() {
        if (pendingFederation != null)
            return pendingFederation;

        DataWord address = new DataWord(BRIDGE_PENDING_FEDERATION_KEY.getBytes(StandardCharsets.UTF_8));

        byte[] data = repository.getStorageBytes(Hex.decode(contractAddress), address);

        if (data == null)
            return null;

        pendingFederation = BridgeSerializationUtils.deserializePendingFederation(data);

        return pendingFederation;
    }

    public void setPendingFederation(PendingFederation federation) {
        pendingFederation = federation;
    }

    /**
     * Save the new (pending) federations
     * Only saved if a pending federation was set with BridgeStorageProvider::setPendingFederation
     */
    public void savePendingFederation() {
        if (pendingFederation == null)
            return;

        byte[] data = BridgeSerializationUtils.serializePendingFederation(pendingFederation);

        DataWord address = new DataWord(BRIDGE_PENDING_FEDERATION_KEY.getBytes(StandardCharsets.UTF_8));

        repository.addStorageBytes(Hex.decode(contractAddress), address, data);
    }

    public void save() throws IOException {
        saveActiveFederationBtcUTXOs();
        saveRetiringFederationBtcUTXOs();
        saveBtcTxHashesAlreadyProcessed();
        saveRskTxsWaitingForConfirmations();
        saveRskTxsWaitingForSignatures();
        saveActiveFederation();
        savePendingFederation();
    }
}
