package co.rsk.peg.union;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.StorageAccessor;
import java.util.Optional;

public class UnionBridgeStorageProviderImpl implements UnionBridgeStorageProvider {

    private static final RskAddress EMPTY_ADDRESS = new RskAddress(new byte[20]);
    private final StorageAccessor bridgeStorageAccessor;

    private RskAddress unionBridgeAddress;
    private Coin unionBridgeLockingCap;
    private Coin weisTransferredToUnionBridge;
    private Boolean unionBridgeRequestEnabled;

    public UnionBridgeStorageProviderImpl(StorageAccessor bridgeStorageAccessor) {
        this.bridgeStorageAccessor = bridgeStorageAccessor;
    }

    @Override
    public void save() {
        if (!isNull(unionBridgeAddress)) {
            bridgeStorageAccessor.saveToRepository(
                UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
                unionBridgeAddress,
                BridgeSerializationUtils::serializeRskAddress
            );
        }

        if (!isNull(unionBridgeLockingCap)) {
            bridgeStorageAccessor.saveToRepository(
                UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
                unionBridgeLockingCap,
                BridgeSerializationUtils::serializeRskCoin
            );
        }

        if (nonNull(weisTransferredToUnionBridge)) {
            bridgeStorageAccessor.saveToRepository(
                UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
                weisTransferredToUnionBridge,
                BridgeSerializationUtils::serializeRskCoin
            );
        }
        if (nonNull(unionBridgeRequestEnabled)) {
            bridgeStorageAccessor.saveToRepository(
                UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
                unionBridgeRequestEnabled? 1L : 0L,
                BridgeSerializationUtils::serializeLong
            );
        }
    }

    @Override
    public void setAddress(RskAddress address) {
        if (address != null && address.equals(EMPTY_ADDRESS)) {
            throw new IllegalArgumentException("Union Bridge address cannot be empty");
        }

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
            .or(() -> bridgeStorageAccessor.getFromRepository(
                UnionBridgeStorageIndexKey.UNION_BRIDGE_REQUEST_ENABLED.getKey(),
                BridgeSerializationUtils::deserializeOptionalLong).map(value -> value == 1L));
    }
}
