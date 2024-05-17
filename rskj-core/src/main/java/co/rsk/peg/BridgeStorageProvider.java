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
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.flyover.FlyoverFederationInformation;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.LockWhitelistEntry;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.peg.whitelist.UnlimitedWhiteListEntry;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.util.*;
import static co.rsk.peg.BridgeStorageIndexKey.*;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;

/**
 * Provides an object oriented facade of the bridge contract memory.
 * @see co.rsk.peg.BridgeStorageProvider
 * @author ajlopez
 * @author Oscar Guindzberg
 */
public class BridgeStorageProvider {

    // Dummy value to use when saving key only indexes
    private static final byte TRUE_VALUE = (byte) 1;

    private final Repository repository;
    private final RskAddress contractAddress;
    private final NetworkParameters networkParameters;
    private final ActivationConfig.ForBlock activations;

    private Map<Sha256Hash, Long> btcTxHashesAlreadyProcessed;

    // RSK pegouts txs follow these steps: First, they are waiting for coin selection (releaseRequestQueue),
    // then they are waiting for enough confirmations on the RSK network (pegoutsWaitingForConfirmations),
    // then they are waiting for federators' signatures (pegoutsWaitingForSignatures),
    // then they are logged into the block that has them as completely signed for btc release
    // and are removed from pegoutsWaitingForSignatures.
    // key = rsk tx hash, value = btc tx
    private ReleaseRequestQueue releaseRequestQueue;
    private PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations;
    private SortedMap<Keccak256, BtcTransaction> pegoutsWaitingForSignatures;

    private LockWhitelist lockWhitelist;

    private Coin lockingCap;

    private HashMap<Sha256Hash, Long> btcTxHashesToSave;

    private Map<Sha256Hash, CoinbaseInformation> coinbaseInformationMap;
    private Map<Integer, Sha256Hash> btcBlocksIndex;

    private Keccak256 flyoverDerivationHash;
    private Sha256Hash flyoverBtcTxHash;
    private FlyoverFederationInformation flyoverFederationInformation;
    private FlyoverFederationInformation flyoverRetiringFederationInformation;
    private long receiveHeadersLastTimestamp = 0;

    private Long nextPegoutHeight;

    private Set<Sha256Hash> pegoutTxSigHashes;

    public BridgeStorageProvider(
        Repository repository,
        RskAddress contractAddress,
        NetworkParameters networkParameters,
        ActivationConfig.ForBlock activations) {
        this.repository = repository;
        this.contractAddress = contractAddress;
        this.networkParameters = networkParameters;
        this.activations = activations;
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

    public PegoutsWaitingForConfirmations getPegoutsWaitingForConfirmations() throws IOException {
        if (pegoutsWaitingForConfirmations != null) {
            return pegoutsWaitingForConfirmations;
        }

        Set<PegoutsWaitingForConfirmations.Entry> entries = new HashSet<>(getFromRepository(PEGOUTS_WAITING_FOR_CONFIRMATIONS,
                data -> BridgeSerializationUtils.deserializePegoutsWaitingForConfirmations(data, networkParameters).getEntries()));

        if (!activations.isActive(RSKIP146)) {
            pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(entries);
            return pegoutsWaitingForConfirmations;
        }

        entries.addAll(getFromRepository(
            PEGOUTS_WAITING_FOR_CONFIRMATIONS_WITH_TXHASH_KEY,
                data -> BridgeSerializationUtils.deserializePegoutsWaitingForConfirmations(data, networkParameters, true).getEntries()));

        pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(entries);

        return pegoutsWaitingForConfirmations;
    }

    public void savePegoutsWaitingForConfirmations() {
        if (pegoutsWaitingForConfirmations == null) {
            return;
        }

        safeSaveToRepository(PEGOUTS_WAITING_FOR_CONFIRMATIONS, pegoutsWaitingForConfirmations, BridgeSerializationUtils::serializePegoutsWaitingForConfirmations);

        if (activations.isActive(RSKIP146)) {
            safeSaveToRepository(PEGOUTS_WAITING_FOR_CONFIRMATIONS_WITH_TXHASH_KEY, pegoutsWaitingForConfirmations, BridgeSerializationUtils::serializePegoutsWaitingForConfirmationsWithTxHash);
        }
    }

    public SortedMap<Keccak256, BtcTransaction> getPegoutsWaitingForSignatures() throws IOException {
        if (pegoutsWaitingForSignatures != null) {
            return pegoutsWaitingForSignatures;
        }

        pegoutsWaitingForSignatures = getFromRepository(
            PEGOUTS_WAITING_FOR_SIGNATURES,
                data -> BridgeSerializationUtils.deserializeMap(data, networkParameters, false)
        );
        return pegoutsWaitingForSignatures;
    }

    public void savePegoutsWaitingForSignatures() {
        if (pegoutsWaitingForSignatures == null) {
            return;
        }

        safeSaveToRepository(PEGOUTS_WAITING_FOR_SIGNATURES, pegoutsWaitingForSignatures, BridgeSerializationUtils::serializeMap);
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

    public Optional<Sha256Hash> getBtcBestBlockHashByHeight(int height) {
        if (!activations.isActive(RSKIP199)) {
            return Optional.empty();
        }

        DataWord storageKey = getStorageKeyForBtcBlockIndex(height);
        Sha256Hash blockHash = safeGetFromRepository(storageKey, BridgeSerializationUtils::deserializeSha256Hash);
        if (blockHash != null) {
            return Optional.of(blockHash);
        }

        return Optional.empty();
    }

    public void setBtcBestBlockHashByHeight(int height, Sha256Hash blockHash) {
        if (!activations.isActive(RSKIP199)) {
            return;
        }

        if (btcBlocksIndex == null) {
            btcBlocksIndex = new HashMap<>();
        }

        btcBlocksIndex.put(height, blockHash);
    }

    private void saveBtcBlocksIndex() {
        if (btcBlocksIndex != null) {
            btcBlocksIndex.forEach((Integer height, Sha256Hash blockHash) -> {
                DataWord storageKey = getStorageKeyForBtcBlockIndex(height);
                safeSaveToRepository(storageKey, blockHash, BridgeSerializationUtils::serializeSha256Hash);
            });
        }
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

    public boolean isFlyoverDerivationHashUsed(Sha256Hash btcTxHash, Keccak256 flyoverDerivationHash) {
        if (!activations.isActive(RSKIP176) || btcTxHash == null || flyoverDerivationHash == null) {
            return false;
        }

        byte[] data = repository.getStorageBytes(
            contractAddress,
            getStorageKeyForFlyoverHash(btcTxHash, flyoverDerivationHash)
        );

        return data != null &&
            data.length == 1 &&
            data[0] == TRUE_VALUE;
    }

    public void markFlyoverDerivationHashAsUsed(Sha256Hash btcTxHash, Keccak256 flyoverDerivationHash) {
        if (activations.isActive(RSKIP176)) {
            flyoverBtcTxHash = btcTxHash;
            this.flyoverDerivationHash = flyoverDerivationHash;
        }
    }

    private void saveFlyoverDerivationHash() {
        if (flyoverDerivationHash == null || flyoverBtcTxHash == null) {
            return;
        }

        repository.addStorageBytes(
            contractAddress,
            getStorageKeyForFlyoverHash(
                flyoverBtcTxHash,
                flyoverDerivationHash
            ),
            new byte[]{TRUE_VALUE}
        );
    }

    public Optional<FlyoverFederationInformation> getFlyoverFederationInformation(byte[] flyoverFederationRedeemScriptHash) {
        if (!activations.isActive(RSKIP176)) {
            return Optional.empty();
        }

        if (flyoverFederationRedeemScriptHash == null || flyoverFederationRedeemScriptHash.length == 0) {
            return Optional.empty();
        }

        FlyoverFederationInformation flyoverFederationInformation = this.safeGetFromRepository(
            getStorageKeyForFlyoverFederationInformation(flyoverFederationRedeemScriptHash),
            data -> BridgeSerializationUtils.deserializeFlyoverFederationInformation(data, flyoverFederationRedeemScriptHash)
        );
        if (flyoverFederationInformation == null) {
            return Optional.empty();
        }

        return Optional.of(flyoverFederationInformation);
    }

    public void setFlyoverFederationInformation(FlyoverFederationInformation flyoverFederationInformation) {
        if (activations.isActive(RSKIP176)) {
            this.flyoverFederationInformation = flyoverFederationInformation;
        }
    }

    private void saveFlyoverFederationInformation() {
        if (flyoverFederationInformation == null) {
            return;
        }

        safeSaveToRepository(
            getStorageKeyForFlyoverFederationInformation(
                flyoverFederationInformation.getFlyoverFederationRedeemScriptHash()
            ),
            flyoverFederationInformation,
            BridgeSerializationUtils::serializeFlyoverFederationInformation
        );
    }

    public void setFlyoverRetiringFederationInformation(FlyoverFederationInformation flyoverRetiringFederationInformation) {
        if (activations.isActive(RSKIP293)) {
            this.flyoverRetiringFederationInformation = flyoverRetiringFederationInformation;
        }
    }

    private void saveFlyoverRetiringFederationInformation() {
        if (flyoverRetiringFederationInformation == null) {
            return;
        }

        safeSaveToRepository(
            getStorageKeyForFlyoverFederationInformation(
                flyoverRetiringFederationInformation.getFlyoverFederationRedeemScriptHash()
            ),
            flyoverRetiringFederationInformation,
            BridgeSerializationUtils::serializeFlyoverFederationInformation
        );
    }

    public Optional<Long> getReceiveHeadersLastTimestamp() {
        if (activations.isActive(RSKIP200)) {
            return safeGetFromRepository(
                RECEIVE_HEADERS_TIMESTAMP,
                BridgeSerializationUtils::deserializeOptionalLong
            );
        }

        return Optional.empty();
    }

    public void setReceiveHeadersLastTimestamp(Long timeInMillis) {
        if (activations.isActive(RSKIP200)) {
            receiveHeadersLastTimestamp = timeInMillis;
        }
    }

    public void saveReceiveHeadersLastTimestamp() {
        if (activations.isActive(RSKIP200) && this.receiveHeadersLastTimestamp > 0) {
            safeSaveToRepository(RECEIVE_HEADERS_TIMESTAMP, this.receiveHeadersLastTimestamp, BridgeSerializationUtils::serializeLong);
        }
    }

    public Optional<Long> getNextPegoutHeight() {
        if (!activations.isActive(RSKIP271)) {
            return Optional.empty();
        }

        if (nextPegoutHeight == null) {
            nextPegoutHeight = safeGetFromRepository(NEXT_PEGOUT_HEIGHT_KEY, BridgeSerializationUtils::deserializeOptionalLong).orElse(0L);
        }

        return Optional.of(nextPegoutHeight);
    }

    public void setNextPegoutHeight(long nextPegoutHeight) {
        this.nextPegoutHeight = nextPegoutHeight;
    }
    
    protected void saveNextPegoutHeight() {
        if (nextPegoutHeight == null || !activations.isActive(RSKIP271)) {
            return;
        }
        
        safeSaveToRepository(NEXT_PEGOUT_HEIGHT_KEY, nextPegoutHeight, BridgeSerializationUtils::serializeLong);
    }

    protected int getReleaseRequestQueueSize() throws IOException {
        return getReleaseRequestQueue().getEntries().size();
    }

    public boolean hasPegoutTxSigHash(Sha256Hash sigHash) {
        if (!activations.isActive(RSKIP379) || sigHash == null){
            return false;
        }

        byte[] data = repository.getStorageBytes(
            contractAddress,
            getStorageKeyForPegoutTxSigHash(sigHash)
        );

        return data != null &&
           data.length == 1 &&
           data[0] == TRUE_VALUE;
    }

    public void setPegoutTxSigHash(Sha256Hash sigHash) {
        if (!activations.isActive(RSKIP379) || sigHash == null) {
            return;
        }

        if (hasPegoutTxSigHash(sigHash)){
            throw new IllegalStateException(String.format("Given pegout tx sigHash %s already exists in the index. Index entries are considered unique.", sigHash));
        }

        if (pegoutTxSigHashes == null){
            pegoutTxSigHashes = new HashSet<>();
        }

        pegoutTxSigHashes.add(sigHash);
    }

    protected void savePegoutTxSigHashes() {
        if (!activations.isActive(RSKIP379) || pegoutTxSigHashes == null) {
            return;
        }

        pegoutTxSigHashes.forEach(pegoutTxSigHash -> repository.addStorageBytes(
            contractAddress,
            getStorageKeyForPegoutTxSigHash(
                pegoutTxSigHash
            ),
            new byte[]{TRUE_VALUE}
        ));
    }

    public void save() throws IOException {
        saveBtcTxHashesAlreadyProcessed();

        saveReleaseRequestQueue();
        savePegoutsWaitingForConfirmations();
        savePegoutsWaitingForSignatures();

        saveLockWhitelist();

        saveLockingCap();

        saveHeightBtcTxHashAlreadyProcessed();

        saveCoinbaseInformations();

        saveBtcBlocksIndex();

        saveFlyoverDerivationHash();
        saveFlyoverFederationInformation();
        saveFlyoverRetiringFederationInformation();

        saveReceiveHeadersLastTimestamp();

        saveNextPegoutHeight();

        savePegoutTxSigHashes();
    }

    private DataWord getStorageKeyForBtcTxHashAlreadyProcessed(Sha256Hash btcTxHash) {
        return BTC_TX_HASH_AP.getCompoundKey("-", btcTxHash.toString());
    }

    private DataWord getStorageKeyForCoinbaseInformation(Sha256Hash btcTxHash) {
        return COINBASE_INFORMATION.getCompoundKey("-", btcTxHash.toString());
    }

    private DataWord getStorageKeyForBtcBlockIndex(Integer height) {
        return BTC_BLOCK_HEIGHT.getCompoundKey("-", height.toString());
    }

    private DataWord getStorageKeyForFlyoverHash(Sha256Hash btcTxHash, Keccak256 derivationHash) {
        return FAST_BRIDGE_HASH_USED_IN_BTC_TX.getCompoundKey("-", btcTxHash.toString() + derivationHash.toString());
    }

    private DataWord getStorageKeyForFlyoverFederationInformation(byte[] flyoverFederationRedeemScriptHash) {
        return FAST_BRIDGE_FEDERATION_INFORMATION.getCompoundKey("-", Hex.toHexString(flyoverFederationRedeemScriptHash));
    }

    private DataWord getStorageKeyForPegoutTxSigHash(Sha256Hash sigHash) {
        return PEGOUT_TX_SIG_HASH.getCompoundKey("-", sigHash.toString());
    }

    private <T> T safeGetFromRepository(BridgeStorageIndexKey keyAddress, RepositoryDeserializer<T> deserializer) {
        return safeGetFromRepository(keyAddress.getKey(), deserializer);
    }

    private <T> T safeGetFromRepository(DataWord keyAddress, RepositoryDeserializer<T> deserializer) {
        try {
            return getFromRepository(keyAddress, deserializer);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to get from repository: " + keyAddress, ioe);
        }
    }

    private <T> T getFromRepository(BridgeStorageIndexKey keyAddress, RepositoryDeserializer<T> deserializer) throws IOException {
        return getFromRepository(keyAddress.getKey(), deserializer);
    }

    private <T> T getFromRepository(DataWord keyAddress, RepositoryDeserializer<T> deserializer) throws IOException {
        byte[] data = repository.getStorageBytes(contractAddress, keyAddress);
        return deserializer.deserialize(data);
    }

    private <T> void safeSaveToRepository(BridgeStorageIndexKey addressKey, T object, RepositorySerializer<T> serializer) {
        safeSaveToRepository(addressKey.getKey(), object, serializer);
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
