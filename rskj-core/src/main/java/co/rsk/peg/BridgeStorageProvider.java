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
import co.rsk.config.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.whitelist.LockWhitelist;
import org.ethereum.core.Repository;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.vm.DataWord;

import java.io.IOException;
import java.util.HashMap;
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
    private static final DataWord NEW_FEDERATION_BTC_UTXOS_KEY = new DataWord(TypeConverter.stringToByteArray("newFederationBtcUTXOs"));
    private static final DataWord OLD_FEDERATION_BTC_UTXOS_KEY = new DataWord(TypeConverter.stringToByteArray("oldFederationBtcUTXOs"));
    private static final DataWord BTC_TX_HASHES_ALREADY_PROCESSED_KEY = new DataWord(TypeConverter.stringToByteArray("btcTxHashesAP"));
    private static final DataWord RELEASE_REQUEST_QUEUE = new DataWord(TypeConverter.stringToByteArray("releaseRequestQueue"));
    private static final DataWord RELEASE_TX_SET = new DataWord(TypeConverter.stringToByteArray("releaseTransactionSet"));
    private static final DataWord RSK_TXS_WAITING_FOR_SIGNATURES_KEY = new DataWord(TypeConverter.stringToByteArray("rskTxsWaitingFS"));
    private static final DataWord NEW_FEDERATION_KEY = new DataWord(TypeConverter.stringToByteArray("newFederation"));
    private static final DataWord OLD_FEDERATION_KEY = new DataWord(TypeConverter.stringToByteArray("oldFederation"));
    private static final DataWord PENDING_FEDERATION_KEY = new DataWord(TypeConverter.stringToByteArray("pendingFederation"));
    private static final DataWord FEDERATION_ELECTION_KEY = new DataWord(TypeConverter.stringToByteArray("federationElection"));
    private static final DataWord LOCK_ONE_OFF_WHITELIST_KEY = new DataWord(TypeConverter.stringToByteArray("lockWhitelist"));
    private static final DataWord LOCK_UNLIMITED_WHITELIST_KEY = new DataWord(TypeConverter.stringToByteArray("unlimitedLockWhitelist"));
    private static final DataWord FEE_PER_KB_KEY = new DataWord(TypeConverter.stringToByteArray("feePerKb"));
    private static final DataWord FEE_PER_KB_ELECTION_KEY = new DataWord(TypeConverter.stringToByteArray("feePerKbElection"));

    private final Repository repository;
    private final RskAddress contractAddress;
    private final NetworkParameters networkParameters;
    private final Context btcContext;
    private final BridgeStorageConfiguration bridgeStorageConfiguration;

    private Map<Sha256Hash, Long> btcTxHashesAlreadyProcessed;

    // RSK release txs follow these steps: First, they are waiting for coin selection (releaseRequestQueue),
    // then they are waiting for enough confirmations on the RSK network (releaseTransactionSet),
    // then they are waiting for federators' signatures (rskTxsWaitingForSignatures),
    // then they are logged into the block that has them as completely signed for btc release
    // and are removed from rskTxsWaitingForSignatures.
    // key = rsk tx hash, value = btc tx
    private ReleaseRequestQueue releaseRequestQueue;
    private ReleaseTransactionSet releaseTransactionSet;
    private SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures;

    private List<UTXO> newFederationBtcUTXOs;
    private List<UTXO> oldFederationBtcUTXOs;

    private Federation newFederation;
    private Federation oldFederation;
    private boolean shouldSaveOldFederation = false;
    private PendingFederation pendingFederation;
    private boolean shouldSavePendingFederation = false;

    private ABICallElection federationElection;

    private LockWhitelist lockWhitelist;

    private Coin feePerKb;
    private ABICallElection feePerKbElection;

    public BridgeStorageProvider(Repository repository, RskAddress contractAddress, BridgeConstants bridgeConstants, BridgeStorageConfiguration bridgeStorageConfiguration) {
        this.repository = repository;
        this.contractAddress = contractAddress;
        this.networkParameters = bridgeConstants.getBtcParams();
        this.btcContext = new Context(networkParameters);
        this.bridgeStorageConfiguration = bridgeStorageConfiguration;
    }

    public List<UTXO> getNewFederationBtcUTXOs() throws IOException {
        if (newFederationBtcUTXOs != null) {
            return newFederationBtcUTXOs;
        }

        newFederationBtcUTXOs = getFromRepository(NEW_FEDERATION_BTC_UTXOS_KEY, BridgeSerializationUtils::deserializeUTXOList);
        return newFederationBtcUTXOs;
    }

    public void saveNewFederationBtcUTXOs() throws IOException {
        if (newFederationBtcUTXOs == null) {
            return;
        }

        saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY, newFederationBtcUTXOs, BridgeSerializationUtils::serializeUTXOList);
    }

    public List<UTXO> getOldFederationBtcUTXOs() throws IOException {
        if (oldFederationBtcUTXOs != null) {
            return oldFederationBtcUTXOs;
        }

        oldFederationBtcUTXOs = getFromRepository(OLD_FEDERATION_BTC_UTXOS_KEY, BridgeSerializationUtils::deserializeUTXOList);
        return oldFederationBtcUTXOs;
    }

    public void saveOldFederationBtcUTXOs() throws IOException {
        if (oldFederationBtcUTXOs == null) {
            return;
        }

        saveToRepository(OLD_FEDERATION_BTC_UTXOS_KEY, oldFederationBtcUTXOs, BridgeSerializationUtils::serializeUTXOList);
    }

    public Map<Sha256Hash, Long> getBtcTxHashesAlreadyProcessed() throws IOException {
        if (btcTxHashesAlreadyProcessed != null) {
            return btcTxHashesAlreadyProcessed;
        }

        btcTxHashesAlreadyProcessed = getFromRepository(BTC_TX_HASHES_ALREADY_PROCESSED_KEY, BridgeSerializationUtils::deserializeMapOfHashesToLong);
        return btcTxHashesAlreadyProcessed;
    }

    public void saveBtcTxHashesAlreadyProcessed() {
        if (btcTxHashesAlreadyProcessed == null) {
            return;
        }

        safeSaveToRepository(BTC_TX_HASHES_ALREADY_PROCESSED_KEY, btcTxHashesAlreadyProcessed, BridgeSerializationUtils::serializeMapOfHashesToLong);
    }

    public ReleaseRequestQueue getReleaseRequestQueue() throws IOException {
        if (releaseRequestQueue != null) {
            return releaseRequestQueue;
        }

        releaseRequestQueue = getFromRepository(
                RELEASE_REQUEST_QUEUE,
                data -> BridgeSerializationUtils.deserializeReleaseRequestQueue(data, networkParameters)
        );

        return releaseRequestQueue;
    }

    public void saveReleaseRequestQueue() {
        if (releaseRequestQueue == null) {
            return;
        }

        safeSaveToRepository(RELEASE_REQUEST_QUEUE, releaseRequestQueue, BridgeSerializationUtils::serializeReleaseRequestQueue);
    }

    public ReleaseTransactionSet getReleaseTransactionSet() throws IOException {
        if (releaseTransactionSet != null) {
            return releaseTransactionSet;
        }

        releaseTransactionSet = getFromRepository(
                RELEASE_TX_SET,
                data -> BridgeSerializationUtils.deserializeReleaseTransactionSet(data, networkParameters)
        );

        return releaseTransactionSet;
    }

    public void saveReleaseTransactionSet() {
        if (releaseTransactionSet == null) {
            return;
        }

        safeSaveToRepository(RELEASE_TX_SET, releaseTransactionSet, BridgeSerializationUtils::serializeReleaseTransactionSet);
    }

    public SortedMap<Keccak256, BtcTransaction> getRskTxsWaitingForSignatures() throws IOException {
        if (rskTxsWaitingForSignatures != null) {
            return rskTxsWaitingForSignatures;
        }

        rskTxsWaitingForSignatures = getFromRepository(
                RSK_TXS_WAITING_FOR_SIGNATURES_KEY,
                data -> BridgeSerializationUtils.deserializeMap(data, networkParameters, false)
        );
        return rskTxsWaitingForSignatures;
    }

    public void saveRskTxsWaitingForSignatures() {
        if (rskTxsWaitingForSignatures == null) {
            return;
        }

        safeSaveToRepository(RSK_TXS_WAITING_FOR_SIGNATURES_KEY, rskTxsWaitingForSignatures, BridgeSerializationUtils::serializeMap);
    }

    public Federation getNewFederation() {
        if (newFederation != null) {
            return newFederation;
        }

        newFederation = safeGetFromRepository(NEW_FEDERATION_KEY,
                data ->
                        data == null
                        ? null
                        : BridgeSerializationUtils.deserializeFederation(data, btcContext)
        );
        return newFederation;
    }

    public void setNewFederation(Federation federation) {
        newFederation = federation;
    }

    /**
     * Save the new federation
     * Only saved if a federation was set with BridgeStorageProvider::setNewFederation
     */
    public void saveNewFederation() {
        if (newFederation == null) {
            return;
        }

        safeSaveToRepository(NEW_FEDERATION_KEY, newFederation, BridgeSerializationUtils::serializeFederation);
    }

    public Federation getOldFederation() {
        if (oldFederation != null || shouldSaveOldFederation) {
            return oldFederation;
        }

        oldFederation = safeGetFromRepository(OLD_FEDERATION_KEY,
                data -> data == null
                        ? null
                        : BridgeSerializationUtils.deserializeFederation(data, btcContext)
        );
        return oldFederation;
    }

    public void setOldFederation(Federation federation) {
        shouldSaveOldFederation = true;
        oldFederation = federation;
    }

    /**
     * Save the old federation
     */
    public void saveOldFederation() {
        if (shouldSaveOldFederation) {
            safeSaveToRepository(OLD_FEDERATION_KEY, oldFederation, BridgeSerializationUtils::serializeFederation);
        }
    }

    public PendingFederation getPendingFederation() {
        if (pendingFederation != null || shouldSavePendingFederation) {
            return pendingFederation;
        }

        pendingFederation = safeGetFromRepository(PENDING_FEDERATION_KEY,
                data -> data == null
                        ? null :
                        BridgeSerializationUtils.deserializePendingFederation(data)
        );
        return pendingFederation;
    }

    public void setPendingFederation(PendingFederation federation) {
        shouldSavePendingFederation = true;
        pendingFederation = federation;
    }

    /**
     * Save the pending federation
     */
    public void savePendingFederation() {
        if (shouldSavePendingFederation) {
            safeSaveToRepository(PENDING_FEDERATION_KEY, pendingFederation, BridgeSerializationUtils::serializePendingFederation);
        }
    }

    /**
     * Save the federation election
     */
    public void saveFederationElection() {
        if (federationElection == null) {
            return;
        }

        safeSaveToRepository(FEDERATION_ELECTION_KEY, federationElection, BridgeSerializationUtils::serializeElection);
    }

    public ABICallElection getFederationElection(AddressBasedAuthorizer authorizer) {
        if (federationElection != null) {
            return federationElection;
        }

        federationElection = safeGetFromRepository(FEDERATION_ELECTION_KEY, data -> (data == null)? new ABICallElection(authorizer) : BridgeSerializationUtils.deserializeElection(data, authorizer));
        return federationElection;
    }

    /**
     * Save the lock whitelist
     */
    public void saveLockWhitelist() {
        if (lockWhitelist == null) {
            return;
        }

        safeSaveToRepository(LOCK_ONE_OFF_WHITELIST_KEY, lockWhitelist, BridgeSerializationUtils::serializeOneOffLockWhitelist);

        if (this.bridgeStorageConfiguration.isUnlimitedWhitelistEnabled()) {
            safeSaveToRepository(LOCK_UNLIMITED_WHITELIST_KEY, lockWhitelist, BridgeSerializationUtils::serializeUnlimitedLockWhitelist);
        }
    }

    public LockWhitelist getLockWhitelist() {
        if (lockWhitelist != null) {
            return lockWhitelist;
        }

        byte[] oneOffWhitelistData = safeGetFromRepository(LOCK_ONE_OFF_WHITELIST_KEY, data -> data);
        if (oneOffWhitelistData == null) {
            lockWhitelist = new LockWhitelist(new HashMap<>());
            return lockWhitelist;
        }
        byte[] unlimitedWhitelistData = null;

        if (this.bridgeStorageConfiguration.isUnlimitedWhitelistEnabled()) {
            unlimitedWhitelistData = safeGetFromRepository(LOCK_UNLIMITED_WHITELIST_KEY, data -> data);
        }

        lockWhitelist = BridgeSerializationUtils.deserializeLockWhitelist(oneOffWhitelistData, unlimitedWhitelistData, btcContext.getParams());

        return lockWhitelist;

    }

    public Coin getFeePerKb() {
        if (feePerKb != null) {
            return feePerKb;
        }

        feePerKb = safeGetFromRepository(FEE_PER_KB_KEY, BridgeSerializationUtils::deserializeCoin);
        return feePerKb;
    }

    public void setFeePerKb(Coin feePerKb) {
        this.feePerKb = feePerKb;
    }

    public void saveFeePerKb() {
        if (feePerKb == null) {
            return;
        }

        safeSaveToRepository(FEE_PER_KB_KEY, feePerKb, BridgeSerializationUtils::serializeCoin);
    }

    /**
     * Save the fee per kb election
     */
    public void saveFeePerKbElection() {
        if (feePerKbElection == null) {
            return;
        }

        safeSaveToRepository(FEE_PER_KB_ELECTION_KEY, feePerKbElection, BridgeSerializationUtils::serializeElection);
    }


    public ABICallElection getFeePerKbElection(AddressBasedAuthorizer authorizer) {
        if (feePerKbElection != null) {
            return feePerKbElection;
        }

        feePerKbElection = safeGetFromRepository(FEE_PER_KB_ELECTION_KEY, data -> BridgeSerializationUtils.deserializeElection(data, authorizer));
        return feePerKbElection;
    }

    public void save() throws IOException {
        saveBtcTxHashesAlreadyProcessed();

        saveReleaseRequestQueue();
        saveReleaseTransactionSet();
        saveRskTxsWaitingForSignatures();

        saveNewFederation();
        saveNewFederationBtcUTXOs();

        saveOldFederation();
        saveOldFederationBtcUTXOs();

        savePendingFederation();

        saveFederationElection();

        saveLockWhitelist();

        saveFeePerKb();
        saveFeePerKbElection();
    }

    private <T> T safeGetFromRepository(DataWord keyAddress, RepositoryDeserializer<T> deserializer) {
        try {
            return getFromRepository(keyAddress, deserializer);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to get from repository: " + keyAddress, ioe);
        }
    }

    private <T> T getFromRepository(DataWord keyAddress, RepositoryDeserializer<T> deserializer) throws IOException {
        byte[] data = repository.getStorageBytes(contractAddress, keyAddress);
        return deserializer.deserialize(data);
    }

    private <T> void safeSaveToRepository(DataWord addressKey, T object, RepositorySerializer<T> serializer) {
        try {
            saveToRepository(addressKey, object, serializer);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to save to repository: " + addressKey, ioe);
        }
    }

    private <T> void saveToRepository(DataWord addressKey, T object, RepositorySerializer<T> serializer) throws IOException {
        byte[] data = null;
        if (object != null) {
            data = serializer.serialize(object);
        }
        repository.addStorageBytes(contractAddress, addressKey, data);
    }

    private interface RepositoryDeserializer<T> {
        T deserialize(byte[] data) throws IOException;
    }

    private interface RepositorySerializer<T> {
        byte[] serialize(T object) throws IOException;
    }
}
