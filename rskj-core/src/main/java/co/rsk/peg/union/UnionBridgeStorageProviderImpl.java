package co.rsk.peg.union;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.storage.StorageAccessor;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

public class UnionBridgeStorageProviderImpl implements UnionBridgeStorageProvider {

    private static final RskAddress EMPTY_ADDRESS = new RskAddress(new byte[20]);
    private final StorageAccessor bridgeStorageAccessor;

    private RskAddress unionBridgeAddress;
    private Coin unionBridgeLockingCap;
    private co.rsk.core.Coin weiTransferredToUnionBridge;

    public UnionBridgeStorageProviderImpl(StorageAccessor bridgeStorageAccessor) {
        this.bridgeStorageAccessor = bridgeStorageAccessor;
    }

    @Override
    public void save(ActivationConfig.ForBlock activations) {
        if (!activations.isActive(ConsensusRule.RSKIP502)) {
            return;
        }

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
                BridgeSerializationUtils::serializeCoin
            );
        }

        if (nonNull(weiTransferredToUnionBridge)) {
            bridgeStorageAccessor.saveToRepository(
                UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
                weiTransferredToUnionBridge,
                BridgeSerializationUtils::serializeRskCoin
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

    @Override
    public void setLockingCap(Coin lockingCap) {
        if (lockingCap != null && lockingCap.isZero()) {
            throw new IllegalArgumentException("Union Bridge Locking Cap cannot be zero");
        }

        this.unionBridgeLockingCap = lockingCap;
    }

    @Override
    public Optional<Coin> getLockingCap(ForBlock activations) {
        if (!activations.isActive(ConsensusRule.RSKIP502)) {
            return Optional.empty();
        }

        return Optional.ofNullable(unionBridgeLockingCap).or(
            () -> Optional.ofNullable(bridgeStorageAccessor.getFromRepository(
                UnionBridgeStorageIndexKey.UNION_BRIDGE_LOCKING_CAP.getKey(),
                BridgeSerializationUtils::deserializeCoin
            ))
        );
    }

    @Override
    public void setWeiTransferredToUnionBridge(co.rsk.core.Coin weiTransferred) {
        if (nonNull(weiTransferred) && weiTransferred.compareTo(co.rsk.core.Coin.ZERO) < 0) {
            throw new IllegalArgumentException("Wei transferred to Union Bridge cannot be negative");
        }
        this.weiTransferredToUnionBridge = weiTransferred;
    }

    @Override
    public Optional<co.rsk.core.Coin> getWeiTransferredToUnionBridge(ForBlock activations) {
        if (!activations.isActive(ConsensusRule.RSKIP502)) {
            return Optional.empty();
        }

        return Optional.ofNullable(weiTransferredToUnionBridge).or(
            () -> Optional.ofNullable(bridgeStorageAccessor.getFromRepository(
                UnionBridgeStorageIndexKey.WEIS_TRANSFERRED_TO_UNION_BRIDGE.getKey(),
                BridgeSerializationUtils::deserializeRskCoin
            ))
        );
    }
}
