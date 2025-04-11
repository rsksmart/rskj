package co.rsk.peg.union;

import static java.util.Objects.isNull;

import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.StorageAccessor;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

public class UnionBridgeStorageProviderImpl implements UnionBridgeStorageProvider {

    private static final RskAddress EMPTY_ADDRESS = new RskAddress(new byte[20]);

    private final StorageAccessor bridgeStorageAccessor;

    private RskAddress unionBridgeAddress;

    public UnionBridgeStorageProviderImpl(StorageAccessor bridgeStorageAccessor) {
        this.bridgeStorageAccessor = bridgeStorageAccessor;
    }

    @Override
    public void save(ActivationConfig.ForBlock activations) {
        if(!activations.isActive(ConsensusRule.RSKIP502) || isNull(unionBridgeAddress) || unionBridgeAddress.equals(EMPTY_ADDRESS)){
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
            () -> Optional.ofNullable(bridgeStorageAccessor.getFromRepository(
                UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
                BridgeSerializationUtils::deserializeRskAddress
            ))
        );
    }
}
