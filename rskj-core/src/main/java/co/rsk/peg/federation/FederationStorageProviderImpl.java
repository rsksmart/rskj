package co.rsk.peg.federation;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.Script;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.vote.ABICallElection;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static co.rsk.peg.federation.FederationStorageIndexKey.*;
import static co.rsk.peg.federation.FederationFormatVersion.*;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;

public class FederationStorageProviderImpl implements FederationStorageProvider {
    private static final Logger logger = LoggerFactory.getLogger(FederationStorageProviderImpl.class);
    private final StorageAccessor bridgeStorageAccessor;
    private final HashMap<DataWord, Optional<Integer>> storageVersionEntries;

    private List<UTXO> newFederationBtcUTXOs;
    private List<UTXO> oldFederationBtcUTXOs;
    private Federation newFederation;
    private Federation oldFederation;
    private boolean shouldSaveOldFederation = false;

    private PendingFederation pendingFederation;
    private boolean shouldSavePendingFederation = false;

    private Federation proposedFederation;
    private boolean isProposedFederationSet = false;

    private ABICallElection federationElection;

    private Long activeFederationCreationBlockHeight;
    private Long nextFederationCreationBlockHeight; // if -1, then clear value

    private Script lastRetiredFederationP2SHScript;

    private Sha256Hash svpFundTransactionUnsignedHash;
    private boolean isSvpFundTransactionUnsignedHashSet = false;

    public FederationStorageProviderImpl(StorageAccessor bridgeStorageAccessor) {
        this.bridgeStorageAccessor = bridgeStorageAccessor;
        this.storageVersionEntries = new HashMap<>();
    }

    @Override
    public List<UTXO> getNewFederationBtcUTXOs(NetworkParameters networkParameters, ActivationConfig.ForBlock activations) {
        if (newFederationBtcUTXOs != null) {
            return newFederationBtcUTXOs;
        }

        DataWord key = getStorageKeyForNewFederationBtcUtxos(networkParameters, activations);
        newFederationBtcUTXOs = bridgeStorageAccessor.getFromRepository(key, BridgeSerializationUtils::deserializeUTXOList);
        return newFederationBtcUTXOs;
    }

    private DataWord getStorageKeyForNewFederationBtcUtxos(NetworkParameters networkParameters, ActivationConfig.ForBlock activations) {

        if (networkIsTestnet(networkParameters)) {
            return getNewFederationBtcUtxosKeyForTestnet(activations);
        }

        return NEW_FEDERATION_BTC_UTXOS_KEY.getKey();
    }

    private boolean networkIsTestnet(NetworkParameters networkParameters) {
        return networkParameters.getId().equals(NetworkParameters.ID_TESTNET);
    }

    private DataWord getNewFederationBtcUtxosKeyForTestnet(ActivationConfig.ForBlock activations) {
        if (shouldUseGeneralKeyForTestnet(activations)) {
            return NEW_FEDERATION_BTC_UTXOS_KEY.getKey();
        }

        if (shouldUsePreHopKeyForTestnet(activations)) {
            return NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_PRE_HOP.getKey();
        }

        return NEW_FEDERATION_BTC_UTXOS_KEY_FOR_TESTNET_POST_HOP.getKey();
    }

    private boolean shouldUseGeneralKeyForTestnet(ActivationConfig.ForBlock activations) {
        return !activations.isActive(RSKIP284);
    }

    private boolean shouldUsePreHopKeyForTestnet(ActivationConfig.ForBlock activations) {
        return !activations.isActive(RSKIP293);
    }

    @Override
    public List<UTXO> getOldFederationBtcUTXOs() {
        if (oldFederationBtcUTXOs != null) {
            return oldFederationBtcUTXOs;
        }

        oldFederationBtcUTXOs = bridgeStorageAccessor.getFromRepository(OLD_FEDERATION_BTC_UTXOS_KEY.getKey(), BridgeSerializationUtils::deserializeUTXOList);
        return oldFederationBtcUTXOs;
    }

    @Override
    public Federation getNewFederation(FederationConstants federationConstants, ActivationConfig.ForBlock activations) {
        if (newFederation != null) {
            return newFederation;
        }

        Optional<Integer> storageVersion = getStorageVersion(NEW_FEDERATION_FORMAT_VERSION.getKey());

        newFederation = bridgeStorageAccessor.getFromRepository(
            NEW_FEDERATION_KEY.getKey(),
            data -> {
                if (data == null) {
                    return null;
                }
                if (!storageVersion.isPresent()) {
                    return BridgeSerializationUtils.deserializeStandardMultisigFederationOnlyBtcKeys(data, federationConstants.getBtcParams());
                }
                return BridgeSerializationUtils.deserializeFederationAccordingToVersion(data, storageVersion.get(), federationConstants, activations);
            }
        );

        return newFederation;
    }

    private Optional<Integer> getStorageVersion(DataWord versionKey) {
        if (storageVersionEntries.containsKey(versionKey)) {
            return storageVersionEntries.get(versionKey);
        }

        Optional<Integer> version = bridgeStorageAccessor.getFromRepository(versionKey, data -> {
            if (data == null || data.length == 0) {
                return Optional.empty();
            }

            return Optional.of(BridgeSerializationUtils.deserializeInteger(data));
        });
        storageVersionEntries.put(versionKey, version);
        return version;
    }

    @Override
    public void setNewFederation(Federation federation) {
        newFederation = federation;
    }

    @Override
    public Federation getOldFederation(FederationConstants federationConstants, ActivationConfig.ForBlock activations) {
        if (oldFederation != null || shouldSaveOldFederation) {
            return oldFederation;
        }

        Optional<Integer> storageVersion = getStorageVersion(OLD_FEDERATION_FORMAT_VERSION.getKey());

        oldFederation = bridgeStorageAccessor.getFromRepository(
            OLD_FEDERATION_KEY.getKey(),
            data -> {
                if (data == null) {
                    return null;
                }
                if (storageVersion.isPresent()) {
                    return BridgeSerializationUtils.deserializeFederationAccordingToVersion(data, storageVersion.get(), federationConstants, activations);
                }

                return BridgeSerializationUtils.deserializeStandardMultisigFederationOnlyBtcKeys(data, federationConstants.getBtcParams());
            }
        );

        return oldFederation;
    }

    @Override
    public void setOldFederation(Federation federation) {
        shouldSaveOldFederation = true;
        oldFederation = federation;
    }

    @Override
    public PendingFederation getPendingFederation() {
        if (pendingFederation != null || shouldSavePendingFederation) {
            return pendingFederation;
        }

        Optional<Integer> storageVersion = getStorageVersion(PENDING_FEDERATION_FORMAT_VERSION.getKey());

        pendingFederation =
            bridgeStorageAccessor.getFromRepository(PENDING_FEDERATION_KEY.getKey(),
                data -> {
                    if (data == null) {
                        return null;
                    }
                    if (storageVersion.isPresent()) {
                        return PendingFederation.deserialize(data); // Assume this is the multi-key version
                    }

                    return PendingFederation.deserializeFromBtcKeysOnly(data);
                }
            );

        return pendingFederation;
    }

    @Override
    public void setPendingFederation(PendingFederation federation) {
        shouldSavePendingFederation = true;
        pendingFederation = federation;
    }

    @Override
    public Optional<Federation> getProposedFederation(FederationConstants federationConstants, ActivationConfig.ForBlock activations) {
        if (!activations.isActive(RSKIP419)) {
            return Optional.empty();
        }

        if (proposedFederation != null) {
            return Optional.of(proposedFederation);
        }

        // reaching this point means the proposed federation was set to null
        if (isProposedFederationSet) {
            return Optional.empty();
        }

        proposedFederation = bridgeStorageAccessor.getFromRepository(
            PROPOSED_FEDERATION.getKey(),
            data -> {
                if (data == null) {
                    return null;
                }

                Optional<Integer> storageVersion = getStorageVersion(PROPOSED_FEDERATION_FORMAT_VERSION.getKey());
                if (!storageVersion.isPresent()) {
                    String message = "Storage version should be present for non-null proposed federation";
                    logger.warn("[getProposedFederation] {}", message);
                    throw new IllegalStateException(message);
                }
                return BridgeSerializationUtils.deserializeFederationAccordingToVersion(data, storageVersion.get(), federationConstants, activations);
            }
        );

        return Optional.ofNullable(proposedFederation);
    }

    @Override
    public void setProposedFederation(Federation proposedFederation) {
        this.proposedFederation = proposedFederation;
        isProposedFederationSet = true;
    }

    @Override
    public Optional<Federation> getProposedFederation(FederationConstants federationConstants, ActivationConfig.ForBlock activations) {
        if (!activations.isActive(RSKIP419)) {
            return Optional.empty();
        }

        if (proposedFederation != null) {
            return Optional.of(proposedFederation);
        }

        // reaching this point means the proposed federation was set to null
        if (isProposedFederationSet) {
            return Optional.empty();
        }

        proposedFederation = bridgeStorageAccessor.getFromRepository(
            PROPOSED_FEDERATION.getKey(),
            data -> {
                if (data == null) {
                    return null;
                }

                // storage version is always present for non-null proposed federation
                Optional<Integer> storageVersion = getStorageVersion(PROPOSED_FEDERATION_FORMAT_VERSION.getKey());
                return BridgeSerializationUtils.deserializeFederationAccordingToVersion(data, storageVersion.get(), federationConstants, activations);
            }
        );

        return Optional.ofNullable(proposedFederation);
    }

    @Override
    public ABICallElection getFederationElection(AddressBasedAuthorizer authorizer) {
        if (federationElection != null) {
            return federationElection;
        }

        federationElection = bridgeStorageAccessor.getFromRepository(FEDERATION_ELECTION_KEY.getKey(), data -> (data == null)? new ABICallElection(authorizer) : BridgeSerializationUtils.deserializeElection(data, authorizer));
        return federationElection;
    }

    @Override
    public Optional<Long> getActiveFederationCreationBlockHeight(ActivationConfig.ForBlock activations) {
        if (!activations.isActive(RSKIP186)) {
            return Optional.empty();
        }

        if (activeFederationCreationBlockHeight != null) {
            return Optional.of(activeFederationCreationBlockHeight);
        }

        activeFederationCreationBlockHeight = bridgeStorageAccessor.getFromRepository(ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT_KEY.getKey(), BridgeSerializationUtils::deserializeOptionalLong).orElse(null);
        return Optional.ofNullable(activeFederationCreationBlockHeight);
    }

    @Override
    public void setActiveFederationCreationBlockHeight(long activeFederationCreationBlockHeight) {
        this.activeFederationCreationBlockHeight = activeFederationCreationBlockHeight;
    }

    @Override
    public Optional<Long> getNextFederationCreationBlockHeight(ActivationConfig.ForBlock activations) {
        if (!activations.isActive(RSKIP186)) {
            return Optional.empty();
        }

        if (nextFederationCreationBlockHeight != null) {
            return Optional.of(nextFederationCreationBlockHeight);
        }

        nextFederationCreationBlockHeight = bridgeStorageAccessor.getFromRepository(NEXT_FEDERATION_CREATION_BLOCK_HEIGHT_KEY.getKey(), BridgeSerializationUtils::deserializeOptionalLong).orElse(null);
        return Optional.ofNullable(nextFederationCreationBlockHeight);
    }

    @Override
    public void setNextFederationCreationBlockHeight(long nextFederationCreationBlockHeight) {
        this.nextFederationCreationBlockHeight = nextFederationCreationBlockHeight;
    }

    @Override
    public void clearNextFederationCreationBlockHeight() {
        this.nextFederationCreationBlockHeight = -1L;
    }

    @Override
    public Optional<Script> getLastRetiredFederationP2SHScript(ActivationConfig.ForBlock activations) {
        if (!activations.isActive(RSKIP186)) {
            return Optional.empty();
        }

        if (lastRetiredFederationP2SHScript != null) {
            return Optional.of(lastRetiredFederationP2SHScript);
        }

        lastRetiredFederationP2SHScript = bridgeStorageAccessor.getFromRepository(LAST_RETIRED_FEDERATION_P2SH_SCRIPT_KEY.getKey(), BridgeSerializationUtils::deserializeScript);
        return Optional.ofNullable(lastRetiredFederationP2SHScript);
    }

    @Override
    public void setLastRetiredFederationP2SHScript(Script lastRetiredFederationP2SHScript) {
        this.lastRetiredFederationP2SHScript = lastRetiredFederationP2SHScript;
    }

    @Override
    public void setSvpFundTransactionUnsignedHash(Sha256Hash hash) {
        this.svpFundTransactionUnsignedHash = hash;
        isSvpFundTransactionUnsignedHashSet = true;
    }

    @Override
    public void save(NetworkParameters networkParameters, ActivationConfig.ForBlock activations) {
        saveNewFederationBtcUTXOs(networkParameters, activations);
        saveOldFederationBtcUTXOs();

        saveNewFederation(activations);
        saveOldFederation(activations);

        savePendingFederation(activations);
        saveProposedFederation(activations);

        saveFederationElection();

        saveActiveFederationCreationBlockHeight(activations);

        saveNextFederationCreationBlockHeight(activations);

        saveLastRetiredFederationP2SHScript(activations);

        saveSvpFundTransactionUnsignedHash(activations);
    }

    private void saveNewFederationBtcUTXOs(NetworkParameters networkParameters, ActivationConfig.ForBlock activations) {
        if (newFederationBtcUTXOs == null) {
            return;
        }

        DataWord key = getStorageKeyForNewFederationBtcUtxos(networkParameters, activations);
        bridgeStorageAccessor.saveToRepository(key, newFederationBtcUTXOs, BridgeSerializationUtils::serializeUTXOList);
    }

    private void saveOldFederationBtcUTXOs() {
        if (oldFederationBtcUTXOs == null) {
            return;
        }

        bridgeStorageAccessor.saveToRepository(OLD_FEDERATION_BTC_UTXOS_KEY.getKey(), oldFederationBtcUTXOs, BridgeSerializationUtils::serializeUTXOList);
    }

    private void saveNewFederation(ActivationConfig.ForBlock activations) {
        if (newFederation == null) {
            return;
        }

        if (!activations.isActive(RSKIP123)) {
            bridgeStorageAccessor.saveToRepository(NEW_FEDERATION_KEY.getKey(), newFederation, BridgeSerializationUtils::serializeFederationOnlyBtcKeys);
            return;
        }

        saveFederationFormatVersion(NEW_FEDERATION_FORMAT_VERSION.getKey(), newFederation.getFormatVersion());
        bridgeStorageAccessor.saveToRepository(NEW_FEDERATION_KEY.getKey(), newFederation, BridgeSerializationUtils::serializeFederation);
    }

    private void saveOldFederation(ActivationConfig.ForBlock activations) {
        if (!shouldSaveOldFederation) {
            return;
        }

        if (!activations.isActive(RSKIP123)) {
            bridgeStorageAccessor.saveToRepository(OLD_FEDERATION_KEY.getKey(), oldFederation, BridgeSerializationUtils::serializeFederationOnlyBtcKeys);
            return;
        }

        int oldFederationFormatVersion = getOldFederationFormatVersion();
        saveFederationFormatVersion(OLD_FEDERATION_FORMAT_VERSION.getKey(), oldFederationFormatVersion);
        bridgeStorageAccessor.saveToRepository(OLD_FEDERATION_KEY.getKey(), oldFederation, BridgeSerializationUtils::serializeFederation);
    }

    private int getOldFederationFormatVersion() {
        if (oldFederation == null) {
            // assume it is a standard federation to keep backwards compatibility
            return STANDARD_MULTISIG_FEDERATION.getFormatVersion();
        }

        return oldFederation.getFormatVersion();
    }

    private void savePendingFederation(ActivationConfig.ForBlock activations) {
        if (!shouldSavePendingFederation) {
            return;
        }

        byte[] serializedPendingFederation = serializePendingFederation(activations);

        if (activations.isActive(RSKIP123)) {
            // we only need to save the standard part of the fed since the emergency part is constant
            saveFederationFormatVersion(PENDING_FEDERATION_FORMAT_VERSION.getKey(), STANDARD_MULTISIG_FEDERATION.getFormatVersion());
        }

        bridgeStorageAccessor.saveToRepository(PENDING_FEDERATION_KEY.getKey(), serializedPendingFederation);
    }

    private void saveProposedFederation(ActivationConfig.ForBlock activations) {
        if (!activations.isActive(RSKIP419) || !isProposedFederationSet) {
            return;
        }

        // format version is always non-null in a non-null federation
        Integer formatVersion = Optional.ofNullable(proposedFederation)
            .map(Federation::getFormatVersion)
            .orElse(null);

        saveFederationFormatVersion(PROPOSED_FEDERATION_FORMAT_VERSION.getKey(), formatVersion);
        bridgeStorageAccessor.saveToRepository(PROPOSED_FEDERATION.getKey(), proposedFederation, BridgeSerializationUtils::serializeFederation);
    }

    private void saveSvpFundTransactionUnsignedHash(ActivationConfig.ForBlock activations) {

        if (!activations.isActive(RSKIP419) || !isSvpFundTransactionUnsignedHashSet) {
            return;
        }

        byte[] data = Optional.ofNullable(svpFundTransactionUnsignedHash)
            .map(BridgeSerializationUtils::serializeSha256Hash)
            .orElse(null);

        bridgeStorageAccessor.saveToRepository(SVP_FUND_TX_HASH_UNSIGNED.getKey(), data);

    }

    private Integer getProposedFederationFormatVersion() {
        if (proposedFederation == null) {
            return null;
        }

        return proposedFederation.getFormatVersion();
    }

    @Nullable
    private byte[] serializePendingFederation(ActivationConfig.ForBlock activations) {
        if (pendingFederation == null) {
            return null;
        }

        return pendingFederation.serialize(activations);
    }

    private void saveFederationFormatVersion(DataWord versionKey, Integer version) {
        bridgeStorageAccessor.saveToRepository(versionKey, version, BridgeSerializationUtils::serializeInteger);
        storageVersionEntries.put(versionKey, Optional.ofNullable(version));
    }

    private void saveFederationElection() {
        if (federationElection == null) {
            return;
        }

        bridgeStorageAccessor.saveToRepository(FEDERATION_ELECTION_KEY.getKey(), federationElection, BridgeSerializationUtils::serializeElection);
    }

    private void saveActiveFederationCreationBlockHeight(ActivationConfig.ForBlock activations) {
        if (activeFederationCreationBlockHeight == null || !activations.isActive(RSKIP186)) {
            return;
        }

        bridgeStorageAccessor.saveToRepository(ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT_KEY.getKey(), activeFederationCreationBlockHeight, BridgeSerializationUtils::serializeLong);
    }

    private void saveNextFederationCreationBlockHeight(ActivationConfig.ForBlock activations) {
        if (nextFederationCreationBlockHeight == null || !activations.isActive(RSKIP186)) {
            return;
        }

        if (nextFederationCreationBlockHeight == -1L) {
            bridgeStorageAccessor.saveToRepository(NEXT_FEDERATION_CREATION_BLOCK_HEIGHT_KEY.getKey(), null, BridgeSerializationUtils::serializeLong);
            return;
        }

        bridgeStorageAccessor.saveToRepository(NEXT_FEDERATION_CREATION_BLOCK_HEIGHT_KEY.getKey(), nextFederationCreationBlockHeight, BridgeSerializationUtils::serializeLong);
    }

    private void saveLastRetiredFederationP2SHScript(ActivationConfig.ForBlock activations) {
        if (lastRetiredFederationP2SHScript == null || !activations.isActive(RSKIP186)) {
            return;
        }

        bridgeStorageAccessor.saveToRepository(LAST_RETIRED_FEDERATION_P2SH_SCRIPT_KEY.getKey(), lastRetiredFederationP2SHScript, BridgeSerializationUtils::serializeScript);
    }
}
