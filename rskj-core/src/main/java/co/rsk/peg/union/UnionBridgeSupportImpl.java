package co.rsk.peg.union;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import javax.annotation.Nonnull;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnionBridgeSupportImpl implements UnionBridgeSupport {

    private static final Logger logger = LoggerFactory.getLogger(UnionBridgeSupportImpl.class);

    private static final RskAddress EMPTY_ADDRESS = new RskAddress(new byte[20]);

    private final ActivationConfig.ForBlock activations;
    private final UnionBridgeConstants constants;
    private final UnionBridgeStorageProvider storageProvider;
    private final SignatureCache signatureCache;

    public UnionBridgeSupportImpl(ForBlock activations, UnionBridgeConstants constants, UnionBridgeStorageProvider storageProvider,
        SignatureCache signatureCache) {
        this.activations = activations;
        this.constants = constants;
        this.storageProvider = storageProvider;
        this.signatureCache = signatureCache;
    }

    @Override
    public int setUnionBridgeContractAddressForTestnet(@Nonnull Transaction tx, RskAddress unionBridgeContractAddress) {
        final String SET_UNION_BRIDGE_ADDRESS_TAG = "setUnionBridgeContractAddressForTestnet";

        logger.info("[{}] Setting new union bridge contract address: {}", SET_UNION_BRIDGE_ADDRESS_TAG, unionBridgeContractAddress);

        try {
            // Check if the network is MAINNET as the contract address can only be set in testnet or regtest
            String currentNetworkId = constants.getBtcParams().getId();
            if (currentNetworkId.equals(NetworkParameters.ID_MAINNET)) {
                String baseMessage = String.format("Union Bridge Contract Address can only be set in Testnet and RegTest environments. Current network: %s", currentNetworkId);
                logger.warn("[{}] {}", SET_UNION_BRIDGE_ADDRESS_TAG, baseMessage);
                return UnionResponseCode.ENVIRONMENT_DISABLED.getCode();
            }

            // Check if the caller is authorized to set a new bridge contract address
            AddressBasedAuthorizer authorizer = constants.getChangeAuthorizer();
            if (!authorizer.isAuthorized(tx, signatureCache)) {
                String baseMessage = String.format("Caller is not authorized to update union bridge contract address. Caller address: %s", tx.getSender());
                logger.warn("[{}] {}}", SET_UNION_BRIDGE_ADDRESS_TAG, baseMessage);
                return UnionResponseCode.UNAUTHORIZED_CALLER.getCode();
            }

            // Check if the address is valid
            if (unionBridgeContractAddress == null || unionBridgeContractAddress.equals(EMPTY_ADDRESS)) {
                String baseMessage = "Union Bridge Contract Address cannot be null or empty";
                logger.warn("[{}] {}", SET_UNION_BRIDGE_ADDRESS_TAG, baseMessage);
                return UnionResponseCode.INVALID_VALUE.getCode();
            }

            // Check if the address is already set
            RskAddress currentUnionBridgeAddress = storageProvider.getAddress(activations).orElse(constants.getAddress());
            if (unionBridgeContractAddress.equals(currentUnionBridgeAddress)) {
                String baseMessage = String.format("The given union bridge contract address is already the current address. Current address: %s", currentUnionBridgeAddress);
                logger.warn("[{}] {}", SET_UNION_BRIDGE_ADDRESS_TAG, baseMessage);
                return UnionResponseCode.INVALID_VALUE.getCode();
            }

            storageProvider.setAddress(unionBridgeContractAddress);
            logger.info("[{}] Union Bridge Contract Address has been updated. Previous address: {} New address: {}", SET_UNION_BRIDGE_ADDRESS_TAG, currentUnionBridgeAddress, unionBridgeContractAddress);
            return UnionResponseCode.SUCCESS.getCode();
        } catch (Exception e) {
            logger.error("[{}] Unexpected error setting Union Bridge Contract Address: {}", SET_UNION_BRIDGE_ADDRESS_TAG, e.getMessage(), e);
            return UnionResponseCode.GENERIC_ERROR.getCode();
        }
    }

    @Override
    public void save() {
        storageProvider.save(activations);
    }
}
