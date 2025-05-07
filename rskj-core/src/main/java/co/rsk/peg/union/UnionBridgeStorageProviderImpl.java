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
        if (isNull(unionBridgeAddress) || !activations.isActive(ConsensusRule.RSKIP502)) {
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
        if (address != null && address.equals(EMPTY_ADDRESS)) {
            throw new IllegalArgumentException("Union Bridge address cannot be empty");
        }

        this.unionBridgeAddress = address;
    }

    @Override
    public Optional<RskAddress> getAddress(ActivationConfig.ForBlock activations) {
        if (!activations.isActive(ConsensusRule.RSKIP502)) {
            return Optional.empty();
        }

        return Optional.ofNullable(unionBridgeAddress).or(
            () -> Optional.ofNullable(bridgeStorageAccessor.getFromRepository(
                UnionBridgeStorageIndexKey.UNION_BRIDGE_CONTRACT_ADDRESS.getKey(),
                BridgeSerializationUtils::deserializeRskAddress
            ))
        );
    }
}
