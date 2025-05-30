package co.rsk.peg.union;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnionBridgeSupportImpl implements UnionBridgeSupport {

    private static final Logger logger = LoggerFactory.getLogger(UnionBridgeSupportImpl.class);

    private static final RskAddress EMPTY_ADDRESS = new RskAddress(new byte[20]);
    public static final String LOG_PATTERN = "[{}] {}";

    private final ActivationConfig.ForBlock activations;
    private final UnionBridgeConstants constants;
    private final UnionBridgeStorageProvider storageProvider;
    private final SignatureCache signatureCache;

    public UnionBridgeSupportImpl(ActivationConfig.ForBlock activations, UnionBridgeConstants constants, UnionBridgeStorageProvider storageProvider,
        SignatureCache signatureCache) {
        this.activations = activations;
        this.constants = constants;
        this.storageProvider = storageProvider;
        this.signatureCache = signatureCache;
    }

    @Override
    public RskAddress getUnionBridgeContractAddress() {
        if (isCurrentEnvironmentMainnet()) {
            return constants.getAddress();
        }

        return storageProvider.getAddress(activations).orElse(constants.getAddress());
    }

    private boolean isCurrentEnvironmentMainnet() {
        String currentNetworkId = constants.getBtcParams().getId();
        return currentNetworkId.equals(NetworkParameters.ID_MAINNET);
    }

    @Override
    public UnionResponseCode setUnionBridgeContractAddressForTestnet(@Nonnull Transaction tx, RskAddress unionBridgeContractAddress) {
        final String SET_UNION_BRIDGE_ADDRESS_TAG = "setUnionBridgeContractAddressForTestnet";

        logger.info("[{}] Setting new union bridge contract address: {}", SET_UNION_BRIDGE_ADDRESS_TAG, unionBridgeContractAddress);

        // Check if the network is MAINNET as the contract address can only be set in testnet or regtest
        if (isEnvironmentDisabled()) {
            return UnionResponseCode.ENVIRONMENT_DISABLED;
        }

        if (isUnauthorizedCaller(tx)) {
            return UnionResponseCode.UNAUTHORIZED_CALLER;
        }

        if (isInvalidAddress(unionBridgeContractAddress)) {
            return UnionResponseCode.INVALID_VALUE;
        }

        RskAddress currentUnionBridgeAddress = storageProvider.getAddress(activations).orElse(constants.getAddress());
        if (isAddressAlreadyStored(currentUnionBridgeAddress, unionBridgeContractAddress)) {
            return UnionResponseCode.INVALID_VALUE;
        }

        storageProvider.setAddress(unionBridgeContractAddress);
        logger.info("[{}] Union Bridge Contract Address has been updated. Previous address: {} New address: {}", SET_UNION_BRIDGE_ADDRESS_TAG, currentUnionBridgeAddress, unionBridgeContractAddress);
        return UnionResponseCode.SUCCESS;
    }

    private boolean isEnvironmentDisabled() {
        String currentNetworkId = constants.getBtcParams().getId();

        boolean isEnvironmentDisable = currentNetworkId.equals(NetworkParameters.ID_MAINNET);
        if (isEnvironmentDisable) {
            String baseMessage = String.format("Union Bridge Contract Address can only be set in Testnet and RegTest environments. Current network: %s", currentNetworkId);
            logger.warn(LOG_PATTERN, "isEnvironmentDisable", baseMessage);
        }
        return isEnvironmentDisable;
    }

    private boolean isUnauthorizedCaller(Transaction tx) {
        // Check if the caller is isUnauthorizedCaller to set a new bridge contract address
        AddressBasedAuthorizer authorizer = constants.getChangeAuthorizer();
        boolean isUnauthorizedCaller = !authorizer.isAuthorized(tx, signatureCache);
        if (isUnauthorizedCaller) {
            String baseMessage = String.format("Caller is not authorized to update union bridge contract address. Caller address: %s", tx.getSender());
            logger.warn(LOG_PATTERN, "isUnauthorizedCaller", baseMessage);
        }

        return isUnauthorizedCaller;
    }

    private boolean isInvalidAddress(RskAddress unionBridgeContractAddress) {
        // Check if the address is valid
        boolean isInvalidAddress = unionBridgeContractAddress == null || unionBridgeContractAddress.equals(EMPTY_ADDRESS);
        if (isInvalidAddress) {
            String baseMessage = "Union Bridge Contract Address cannot be null or empty";
            logger.warn(LOG_PATTERN, "isInvalidAddress", baseMessage);
        }
        return isInvalidAddress;
    }

    private boolean isAddressAlreadyStored(RskAddress currentUnionBridgeContractAddress, RskAddress newUnionBridgeContractAddress) {
        // Check if the address is already set
        boolean isAddressAlreadyStored = currentUnionBridgeContractAddress.equals(newUnionBridgeContractAddress);
        if (isAddressAlreadyStored) {
            String baseMessage = String.format("The given union bridge contract address is already the current address. Current address: %s", currentUnionBridgeContractAddress);
            logger.warn(LOG_PATTERN, "isAddressAlreadyStored", baseMessage);
        }
        return isAddressAlreadyStored;
    }

    @Override
    public Optional<Coin> getLockingCap() {
        if (!activations.isActive(ConsensusRule.RSKIP502)) {
            return Optional.empty();
        }
        return Optional.of(storageProvider.getLockingCap(activations).orElse(constants.getInitialLockingCap()));
    }

    @Override
    public UnionResponseCode increaseLockingCap(Transaction tx, Coin newCap) {
        final String INCREASE_LOCKING_CAP_TAG = "increaseLockingCap";

        if (isUnauthorizedCaller(tx)) {
            return UnionResponseCode.UNAUTHORIZED_CALLER;
        }

        if (isInvalidLockingCap(newCap)) {
            return UnionResponseCode.INVALID_VALUE;
        }

        storageProvider.setLockingCap(newCap);
        logger.info("[{}] Union Locking Cap has been increased. New value: {}",
            INCREASE_LOCKING_CAP_TAG, newCap.value);
        return UnionResponseCode.SUCCESS;
    }

    private boolean isInvalidLockingCap(Coin newCap) {
        Coin currentLockingCap = storageProvider.getLockingCap(activations)
            .orElse(constants.getInitialLockingCap());

        if (newCap.compareTo(currentLockingCap) < 1) {
            logger.warn(
                "[isInvalidLockingCap] Attempted value doesn't increase Union Locking Cap. Value attempted: {} . currentLockingCap: {}",
                newCap.value, currentLockingCap.value);
            return true;
        }

        Coin maxLockingCapIncreaseAllowed = currentLockingCap.multiply(
            constants.getLockingCapIncrementsMultiplier());
        if (newCap.compareTo(maxLockingCapIncreaseAllowed) > 0) {
            logger.warn(
                "[isInvalidLockingCap] Attempted value tries to increase Union Locking Cap above its limit. Value attempted: {} . maxLockingCapIncreasedAllowed: {}",
                newCap.value, maxLockingCapIncreaseAllowed.value);
            return true;
        }

        return false;
    }

    @Override
    public void save() {
        storageProvider.save(activations);
    }
}
