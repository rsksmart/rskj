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
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.LockWhitelistEntry;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.peg.whitelist.UnlimitedWhiteListEntry;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;

import java.io.IOException;
import java.util.*;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;

/**
 * Provides an object oriented facade of the bridge contract memory.
 * @see co.rsk.remasc.RemascStorageProvider
 * @author ajlopez
 * @author Oscar Guindzberg
 */
public class BridgeStorageProvider {
    private static final DataWord NEW_FEDERATION_BTC_UTXOS_KEY = DataWord.fromString("newFederationBtcUTXOs");
    private static final DataWord OLD_FEDERATION_BTC_UTXOS_KEY = DataWord.fromString("oldFederationBtcUTXOs");
    private static final DataWord BTC_TX_HASHES_ALREADY_PROCESSED_KEY = DataWord.fromString("btcTxHashesAP");
    private static final DataWord RELEASE_REQUEST_QUEUE = DataWord.fromString("releaseRequestQueue");
    private static final DataWord RELEASE_TX_SET = DataWord.fromString("releaseTransactionSet");
    private static final DataWord RSK_TXS_WAITING_FOR_SIGNATURES_KEY = DataWord.fromString("rskTxsWaitingFS");
    private static final DataWord NEW_FEDERATION_KEY = DataWord.fromString("newFederation");
    private static final DataWord OLD_FEDERATION_KEY = DataWord.fromString("oldFederation");
    private static final DataWord PENDING_FEDERATION_KEY = DataWord.fromString("pendingFederation");
    private static final DataWord FEDERATION_ELECTION_KEY = DataWord.fromString("federationElection");
    private static final DataWord LOCK_ONE_OFF_WHITELIST_KEY = DataWord.fromString("lockWhitelist");
    private static final DataWord LOCK_UNLIMITED_WHITELIST_KEY = DataWord.fromString("unlimitedLockWhitelist");
    private static final DataWord FEE_PER_KB_KEY = DataWord.fromString("feePerKb");
    private static final DataWord FEE_PER_KB_ELECTION_KEY = DataWord.fromString("feePerKbElection");
    private static final DataWord LOCKING_CAP_KEY = DataWord.fromString("lockingCap");
    private static final DataWord RELEASE_REQUEST_QUEUE_WITH_TXHASH = DataWord.fromString("releaseRequestQueueWithTxHash");
    private static final DataWord RELEASE_TX_SET_WITH_TXHASH = DataWord.fromString("releaseTransactionSetWithTxHash");

    // Version keys and versions
    private static final DataWord NEW_FEDERATION_FORMAT_VERSION = DataWord.fromString("newFederationFormatVersion");
    private static final DataWord OLD_FEDERATION_FORMAT_VERSION = DataWord.fromString("oldFederationFormatVersion");
    private static final DataWord PENDING_FEDERATION_FORMAT_VERSION = DataWord.fromString("pendingFederationFormatVersion");
    private static final Integer FEDERATION_FORMAT_VERSION_MULTIKEY = 1000;

    private final Repository repository;
    private final RskAddress contractAddress;
    private final NetworkParameters networkParameters;
    private final ActivationConfig.ForBlock activations;

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

    private Coin lockingCap;

    private HashMap<DataWord, Optional<Integer>> storageVersion;

    private HashMap<Sha256Hash, Long> btcTxHashesToSave;

    private Map<Sha256Hash, CoinbaseInformation> coinbaseInformationMap;

    public BridgeStorageProvider(Repository repository, RskAddress contractAddress, BridgeConstants bridgeConstants, ActivationConfig.ForBlock activations) {
        this.repository = repository;
        this.contractAddress = contractAddress;
        this.networkParameters = bridgeConstants.getBtcParams();
        this.activations = activations;
        this.storageVersion = new HashMap<>();
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

    public Optional<Long> getHeightIfBtcTxhashIsAlreadyProcessed(Sha256Hash btcTxHash) throws IOException {
        Map<Sha256Hash, Long> processed = getBtcTxHashesAlreadyProcessed();
        if (processed.containsKey(btcTxHash)) {
            return Optional.of(processed.get(btcTxHash));
        }

        if (!activations.isActive(RSKIP134)) {
            return Optional.empty();
        }

        if (btcTxHashesToSave == null) {
            btcTxHashesToSave = new HashMap<>();
        }

        if (btcTxHashesToSave.containsKey(btcTxHash)) {
            return Optional.of(btcTxHashesToSave.get(btcTxHash));
        }

        Optional<Long> height = getFromRepository(getStorageKeyForBtcTxHashAlreadyProcessed(btcTxHash), BridgeSerializationUtils::deserializeOptionalLong);
        if (!height.isPresent()) {
            return height;
        }

        btcTxHashesToSave.put(btcTxHash, height.get());
        return height;
    }

    public void setHeightBtcTxhashAlreadyProcessed(Sha256Hash btcTxHash, long height) throws IOException {
        if (activations.isActive(RSKIP134)) {
            if (btcTxHashesToSave == null) {
                btcTxHashesToSave = new HashMap<>();
            }
            btcTxHashesToSave.put(btcTxHash, height);
        } else {
            getBtcTxHashesAlreadyProcessed().put(btcTxHash, height);
        }
    }

    public void saveHeightBtcTxHashAlreadyProcessed() {
        if (btcTxHashesToSave == null) {
            return;
        }

        btcTxHashesToSave.forEach((btcTxHash, height) ->
            safeSaveToRepository(getStorageKeyForBtcTxHashAlreadyProcessed(btcTxHash), height, BridgeSerializationUtils::serializeLong)
        );
    }

    private Map<Sha256Hash, Long> getBtcTxHashesAlreadyProcessed() throws IOException {
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

        List<ReleaseRequestQueue.Entry> entries = new ArrayList<>();

        entries.addAll(getFromRepository(
                RELEASE_REQUEST_QUEUE,
                data -> BridgeSerializationUtils.deserializeReleaseRequestQueue(data, networkParameters)
                )
        );

        if (!activations.isActive(RSKIP146)) {
            releaseRequestQueue = new ReleaseRequestQueue(entries);
            return releaseRequestQueue;
        }

        entries.addAll(getFromRepository(
                RELEASE_REQUEST_QUEUE_WITH_TXHASH,
                data -> BridgeSerializationUtils.deserializeReleaseRequestQueue(data, networkParameters, true)
                )
        );

        releaseRequestQueue = new ReleaseRequestQueue(entries);

        return releaseRequestQueue;
    }

    public void saveReleaseRequestQueue() {
        if (releaseRequestQueue == null) {
            return;
        }

        safeSaveToRepository(RELEASE_REQUEST_QUEUE, releaseRequestQueue, BridgeSerializationUtils::serializeReleaseRequestQueue);

        if(activations.isActive(RSKIP146)) {
            safeSaveToRepository(RELEASE_REQUEST_QUEUE_WITH_TXHASH, releaseRequestQueue, BridgeSerializationUtils::serializeReleaseRequestQueueWithTxHash);
        }
    }

    public ReleaseTransactionSet getReleaseTransactionSet() throws IOException {
        if (releaseTransactionSet != null) {
            return releaseTransactionSet;
        }

        Set<ReleaseTransactionSet.Entry> entries = new HashSet<>(getFromRepository(RELEASE_TX_SET,
                data -> BridgeSerializationUtils.deserializeReleaseTransactionSet(data, networkParameters).getEntries()));

        if (!activations.isActive(RSKIP146)) {
            releaseTransactionSet = new ReleaseTransactionSet(entries);
            return releaseTransactionSet;
        }

        entries.addAll(getFromRepository(
                RELEASE_TX_SET_WITH_TXHASH,
                data -> BridgeSerializationUtils.deserializeReleaseTransactionSet(data, networkParameters, true).getEntries()));

        releaseTransactionSet = new ReleaseTransactionSet(entries);

        return releaseTransactionSet;
    }

    public void saveReleaseTransactionSet() {
        if (releaseTransactionSet == null) {
            return;
        }

        safeSaveToRepository(RELEASE_TX_SET, releaseTransactionSet, BridgeSerializationUtils::serializeReleaseTransactionSet);

        if (activations.isActive(RSKIP146)) {
            safeSaveToRepository(RELEASE_TX_SET_WITH_TXHASH, releaseTransactionSet, BridgeSerializationUtils::serializeReleaseTransactionSetWithTxHash);
        }
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

        Optional<Integer> storageVersion = getStorageVersion(NEW_FEDERATION_FORMAT_VERSION);

        newFederation = safeGetFromRepository(NEW_FEDERATION_KEY,
                data ->
                        data == null
                        ? null
                        : deserializeFederationAccordingToVersion(data, storageVersion)
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

        RepositorySerializer<Federation> serializer = BridgeSerializationUtils::serializeFederationOnlyBtcKeys;

        if (activations.isActive(RSKIP123)) {
            saveStorageVersion(NEW_FEDERATION_FORMAT_VERSION, FEDERATION_FORMAT_VERSION_MULTIKEY);
            serializer = BridgeSerializationUtils::serializeFederation;
        }

        safeSaveToRepository(NEW_FEDERATION_KEY, newFederation, serializer);
    }

    public Federation getOldFederation() {
        if (oldFederation != null || shouldSaveOldFederation) {
            return oldFederation;
        }

        Optional<Integer> storageVersion = getStorageVersion(OLD_FEDERATION_FORMAT_VERSION);

        oldFederation = safeGetFromRepository(OLD_FEDERATION_KEY,
                data -> data == null
                        ? null
                        : deserializeFederationAccordingToVersion(data, storageVersion)
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
            RepositorySerializer<Federation> serializer = BridgeSerializationUtils::serializeFederationOnlyBtcKeys;

            if (activations.isActive(RSKIP123)) {
                saveStorageVersion(OLD_FEDERATION_FORMAT_VERSION, FEDERATION_FORMAT_VERSION_MULTIKEY);
                serializer = BridgeSerializationUtils::serializeFederation;
            }

            safeSaveToRepository(OLD_FEDERATION_KEY, oldFederation, serializer);
        }
    }

    public PendingFederation getPendingFederation() {
        if (pendingFederation != null || shouldSavePendingFederation) {
            return pendingFederation;
        }

        Optional<Integer> storageVersion = getStorageVersion(PENDING_FEDERATION_FORMAT_VERSION);

        pendingFederation = safeGetFromRepository(PENDING_FEDERATION_KEY,
                data -> data == null
                        ? null :
                        deserializePendingFederationAccordingToVersion(data, storageVersion)
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
            RepositorySerializer<PendingFederation> serializer = BridgeSerializationUtils::serializePendingFederationOnlyBtcKeys;

            if (activations.isActive(RSKIP123)) {
                saveStorageVersion(PENDING_FEDERATION_FORMAT_VERSION, FEDERATION_FORMAT_VERSION_MULTIKEY);
                serializer = BridgeSerializationUtils::serializePendingFederation;
            }

            safeSaveToRepository(PENDING_FEDERATION_KEY, pendingFederation, serializer);
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

        List<OneOffWhiteListEntry> oneOffEntries = lockWhitelist.getAll(OneOffWhiteListEntry.class);
        safeSaveToRepository(LOCK_ONE_OFF_WHITELIST_KEY, Pair.of(oneOffEntries, lockWhitelist.getDisableBlockHeight()), BridgeSerializationUtils::serializeOneOffLockWhitelist);

        if (activations.isActive(RSKIP87)) {
            List<UnlimitedWhiteListEntry> unlimitedEntries = lockWhitelist.getAll(UnlimitedWhiteListEntry.class);
            safeSaveToRepository(LOCK_UNLIMITED_WHITELIST_KEY, unlimitedEntries, BridgeSerializationUtils::serializeUnlimitedLockWhitelist);
        }
    }

    public LockWhitelist getLockWhitelist() {
        if (lockWhitelist != null) {
            return lockWhitelist;
        }

        Pair<HashMap<Address, OneOffWhiteListEntry>, Integer> oneOffWhitelistAndDisableBlockHeightData =
                safeGetFromRepository(LOCK_ONE_OFF_WHITELIST_KEY,
                        data -> BridgeSerializationUtils.deserializeOneOffLockWhitelistAndDisableBlockHeight(data, networkParameters));
        if (oneOffWhitelistAndDisableBlockHeightData == null) {
            lockWhitelist = new LockWhitelist(new HashMap<>());
            return lockWhitelist;
        }

        Map<Address, LockWhitelistEntry> whitelistedAddresses = new HashMap<>();

        whitelistedAddresses.putAll(oneOffWhitelistAndDisableBlockHeightData.getLeft());

        if (activations.isActive(RSKIP87)) {
            whitelistedAddresses.putAll(safeGetFromRepository(LOCK_UNLIMITED_WHITELIST_KEY,
                    data -> BridgeSerializationUtils.deserializeUnlimitedLockWhitelistEntries(data, networkParameters)));
        }

        lockWhitelist = new LockWhitelist(whitelistedAddresses, oneOffWhitelistAndDisableBlockHeightData.getRight());

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

    public void saveLockingCap() {
        if (activations.isActive(RSKIP134)) {
            safeSaveToRepository(LOCKING_CAP_KEY, this.getLockingCap(), BridgeSerializationUtils::serializeCoin);
        }
    }

    public void setLockingCap(Coin lockingCap) {
        this.lockingCap = lockingCap;
    }

    public Coin getLockingCap() {
        if (activations.isActive(RSKIP134)) {
            if (this.lockingCap == null) {
                this.lockingCap = safeGetFromRepository(LOCKING_CAP_KEY, BridgeSerializationUtils::deserializeCoin);
            }
            return this.lockingCap;
        }
        return null;
    }

    public CoinbaseInformation getCoinbaseInformation(Sha256Hash blockHash) {
        if (!activations.isActive(RSKIP143)) {
            return null;
        }

        if (coinbaseInformationMap == null) {
            coinbaseInformationMap = new HashMap<>();
        }

        if (coinbaseInformationMap.containsKey(blockHash)) {
            return coinbaseInformationMap.get(blockHash);
        }

        CoinbaseInformation coinbaseInformation =
                safeGetFromRepository(getStorageKeyForCoinbaseInformation(blockHash), BridgeSerializationUtils::deserializeCoinbaseInformation);
        coinbaseInformationMap.put(blockHash, coinbaseInformation);

        return coinbaseInformation;
    }

    public void setCoinbaseInformation(Sha256Hash blockHash, CoinbaseInformation data) {
        if (!activations.isActive(RSKIP143)) {
            return;
        }

        if (coinbaseInformationMap == null) {
            coinbaseInformationMap = new HashMap<>();
        }

        coinbaseInformationMap.put(blockHash, data);
    }

    private void saveCoinbaseInformations() {
        if (!activations.isActive(RSKIP143)) {
            return;
        }

        if (coinbaseInformationMap == null || coinbaseInformationMap.size() == 0) {
            return;
        }
        coinbaseInformationMap.forEach((Sha256Hash blockHash, CoinbaseInformation data) ->
            safeSaveToRepository(getStorageKeyForCoinbaseInformation(blockHash), data, BridgeSerializationUtils::serializeCoinbaseInformation));
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

        saveLockingCap();

        saveHeightBtcTxHashAlreadyProcessed();

        saveCoinbaseInformations();
    }

    private DataWord getStorageKeyForBtcTxHashAlreadyProcessed(Sha256Hash btcTxHash) {
        return DataWord.fromLongString("btcTxHashAP-" + btcTxHash.toString());
    }

    private DataWord getStorageKeyForCoinbaseInformation(Sha256Hash btcTxHash) {
        return DataWord.fromLongString("coinbaseInformation-" + btcTxHash.toString());
    }

    private Optional<Integer> getStorageVersion(DataWord versionKey) {
        if (!storageVersion.containsKey(versionKey)) {
            Optional<Integer> version = safeGetFromRepository(versionKey, data -> {
                if (data == null || data.length == 0) {
                    return Optional.empty();
                }

                return Optional.of(BridgeSerializationUtils.deserializeInteger(data));
            });

            storageVersion.put(versionKey, version);
            return version;
        }

        return storageVersion.get(versionKey);
    }

    private void saveStorageVersion(DataWord versionKey, Integer version) {
        safeSaveToRepository(versionKey, version, BridgeSerializationUtils::serializeInteger);
        storageVersion.put(versionKey, Optional.of(version));
    }

    private Federation deserializeFederationAccordingToVersion(byte[] data, Optional<Integer> version) {
        if (!version.isPresent()) {
            return BridgeSerializationUtils.deserializeFederationOnlyBtcKeys(data, networkParameters);
        }

        // Assume this is the multi-key version
        return BridgeSerializationUtils.deserializeFederation(data, networkParameters);
    }

    private PendingFederation deserializePendingFederationAccordingToVersion(byte[] data, Optional<Integer> version) {
        if (!version.isPresent()) {
            return BridgeSerializationUtils.deserializePendingFederationOnlyBtcKeys(data);
        }

        // Assume this is the multi-key version
        return BridgeSerializationUtils.deserializePendingFederation(data);
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
