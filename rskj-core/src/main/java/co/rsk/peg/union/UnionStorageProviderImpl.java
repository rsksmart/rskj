package co.rsk.peg.union;

import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.StorageAccessor;
import java.util.Optional;

public class UnionStorageProviderImpl implements UnionStorageProvider {

    private final StorageAccessor bridgeStorageAccessor;
    private RskAddress unionBridgeContractAddress;

    public UnionStorageProviderImpl(StorageAccessor bridgeStorageAccessor) {
        this.bridgeStorageAccessor = bridgeStorageAccessor;
    }

    @Override
    public void save() {
        if (unionBridgeContractAddress == null) {
            return;
        }

        bridgeStorageAccessor.saveToRepository(
            UnionStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
            unionBridgeContractAddress,
            BridgeSerializationUtils::serializeRskAddress
        );
    }

    @Override
    public void setUnionAddress(RskAddress unionAddress) {
        this.unionBridgeContractAddress = unionAddress;
    }

    @Override
    public Optional<RskAddress> getUnionAddress() {
        return Optional.ofNullable(unionBridgeContractAddress).or(
            () -> Optional.ofNullable(bridgeStorageAccessor.getFromRepository(
                UnionStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
                BridgeSerializationUtils::deserializeRskAddress
            ))
        );
    }
}
