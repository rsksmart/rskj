package co.rsk.peg.union;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.StorageAccessor;
import java.util.Optional;

public class UnionBridgeStorageProviderImpl implements UnionBridgeStorageProvider {

    private final StorageAccessor bridgeStorageAccessor;

    private RskAddress unionBridgeAddress;
    private Coin unionBridgeLockingCap;
    private Coin weisTransferredToUnionBridge;
    private Boolean unionBridgeRequestEnabled;
    private Boolean unionBridgeReleaseEnabled;

    public UnionBridgeStorageProviderImpl(StorageAccessor bridgeStorageAccessor) {
        this.bridgeStorageAccessor = bridgeStorageAccessor;
    }

    @Override
    public void save() {
        saveUnionBridgeAddress();
        saveLockingCap();
        saveWeisTransferredToUnionBridge();
        saveRequestEnabled();
        saveReleaseEnabled();
    }

    private void saveReleaseEnabled() {
        if (nonNull(unionBridgeReleaseEnabled)) {
            bridgeStorageAccessor.saveToRepository(
                UnionBridgeStorageIndexKey.UNION_BRIDGE_RELEASE_ENABLED.getKey(),
                unionBridgeReleaseEnabled,
                BridgeSerializationUtils::serializeBoolean
            );
        }
    }

    private void saveRequestEnabled() {
        if (isNull(unionBridgeRequestEnabled)) {
            return;
        }

        bridgeStorageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
            unionBridgeRequestEnabled,
            BridgeSerializationUtils::serializeBoolean
        );
    }

    private void saveWeisTransferredToUnionBridge() {
        if (isNull(weisTransferredToUnionBridge)) {
            return;
        }

        bridgeStorageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
            weisTransferredToUnionBridge,
            BridgeSerializationUtils::serializeRskCoin
        );
    }

    private void saveLockingCap() {
        if (isNull(unionBridgeLockingCap)) {
            return;
        }

        bridgeStorageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
            unionBridgeLockingCap,
            BridgeSerializationUtils::serializeRskCoin
        );
    }

    private void saveUnionBridgeAddress() {
        if (isNull(unionBridgeAddress)) {
            return;
        }

        bridgeStorageAccessor.saveToRepository(
            UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            unionBridgeAddress,
            BridgeSerializationUtils::serializeRskAddress
        );
    }

    @Override
    public void setAddress(RskAddress address) {
        this.unionBridgeAddress = address;
    }

    @Override
    public Optional<RskAddress> getAddress() {
        return Optional.ofNullable(unionBridgeAddress).or(
            () -> Optional.ofNullable(
                bridgeStorageAccessor.getFromRepository(
                    UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
                    BridgeSerializationUtils::deserializeRskAddress
                )
            )
        );
    }

    @Override
    public void setLockingCap(Coin lockingCap) {
        if (lockingCap != null && lockingCap.equals(Coin.ZERO)) {
            throw new IllegalArgumentException("Union Bridge Locking Cap cannot be zero");
        }

        this.unionBridgeLockingCap = lockingCap;
    }

    @Override
    public Optional<Coin> getLockingCap() {
        return Optional.ofNullable(unionBridgeLockingCap).or(
            () -> Optional.ofNullable(bridgeStorageAccessor.getFromRepository(
                UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
                BridgeSerializationUtils::deserializeRskCoin
            ))
        );
    }

    @Override
    public Optional<Coin> getWeisTransferredToUnionBridge() {
        return Optional.ofNullable(weisTransferredToUnionBridge).or(() -> Optional.ofNullable(
            bridgeStorageAccessor.getFromRepository(
                UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
                BridgeSerializationUtils::deserializeRskCoin
            )
        ));
    }

    @Override
    public void increaseWeisTransferredToUnionBridge(Coin amountRequested) {
        if (isNull(amountRequested) || amountRequested.compareTo(Coin.ZERO) < 0) {
            throw new IllegalArgumentException("Amount requested to Union Bridge cannot be null or negative");
        }
        Coin currentWeisTransferredToUnionBridge = getWeisTransferredToUnionBridge().orElse(Coin.ZERO);
        this.weisTransferredToUnionBridge = currentWeisTransferredToUnionBridge.add(amountRequested);
    }

    @Override
    public void decreaseWeisTransferredToUnionBridge(Coin amountToRelease) {
        if (isNull(amountToRelease) || amountToRelease.compareTo(Coin.ZERO) < 0) {
            throw new IllegalArgumentException("Amount released cannot be null or negative");
        }
        Coin currentWeisTransferredToUnionBridge = getWeisTransferredToUnionBridge().orElse(Coin.ZERO);
        Coin updatedWeisTransferred = currentWeisTransferredToUnionBridge.subtract(amountToRelease);

        if (updatedWeisTransferred.compareTo(Coin.ZERO) < 0) {
            throw new IllegalArgumentException("Cannot decrease weis transferred to Union Bridge below zero");
        }
        this.weisTransferredToUnionBridge = updatedWeisTransferred;
    }

    public void setUnionBridgeRequestEnabled(boolean enabled) {
        this.unionBridgeRequestEnabled = enabled;
    }

    public Optional<Boolean> isUnionBridgeRequestEnabled() {
        return Optional.ofNullable(unionBridgeRequestEnabled)
            .or(() -> Optional.ofNullable(bridgeStorageAccessor.getFromRepository(
                    UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
                    BridgeSerializationUtils::deserializeBoolean
                )));
    }

    public void setUnionBridgeReleaseEnabled(boolean enabled) {
        this.unionBridgeReleaseEnabled = enabled;
    }

    public Optional<Boolean> isUnionBridgeReleaseEnabled() {
        return Optional.ofNullable(unionBridgeReleaseEnabled)
            .or(() -> Optional.ofNullable(bridgeStorageAccessor.getFromRepository(
                UnionBridgeStorageIndexKey.UNION_BRIDGE_RELEASE_ENABLED.getKey(),
                BridgeSerializationUtils::deserializeBoolean
            )));
    }
}
