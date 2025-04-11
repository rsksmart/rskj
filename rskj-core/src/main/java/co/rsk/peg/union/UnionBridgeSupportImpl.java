package co.rsk.peg.union;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.core.RskAddress;
import co.rsk.peg.union.constants.UnionBridgeConstants;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnionBridgeSupportImpl implements UnionBridgeSupport {

    private static final Logger logger = LoggerFactory.getLogger(UnionBridgeSupportImpl.class);


    private final UnionBridgeConstants constants;
    private final UnionBridgeStorageProvider storageProvider;
    private final SignatureCache signatureCache;

    public UnionBridgeSupportImpl(UnionBridgeConstants constants, UnionBridgeStorageProvider storageProvider,
        SignatureCache signatureCache) {
        this.constants = constants;
        this.storageProvider = storageProvider;
        this.signatureCache = signatureCache;
    }

    @Override
    public int setUnionBridgeContractAddressForTestnet(Transaction tx, RskAddress unionBridgeContractAddress) {
        final String SET_UNION_BRIDGE_ADDRESS_TAG = "setUnionBridgeContractAddressForTestnet";
        try {
            // Check environment is not mainnet
            String currentNetworkId = constants.getBtcParams().getId();
            if (currentNetworkId.equals(NetworkParameters.ID_MAINNET)) {
                String baseMessage = String.format("Union Bridge Contract Address can only be set in Testnet and RegTest environments. Current network: %s", currentNetworkId);
                logger.warn("[{}] {}", SET_UNION_BRIDGE_ADDRESS_TAG, baseMessage);
                return UnionResponseCode.ENVIRONMENT_DISABLED.getCode();
            }

            // Only pre-configured addresses can modify Locking Cap
            AddressBasedAuthorizer authorizer = constants.getUnionBridgeChangeAuthorizer();
            if (!authorizer.isAuthorized(tx, signatureCache)) {
                String baseMessage = String.format("An unauthorized caller tried to set Union Bridge Contract Address. Caller address: %s", tx.getSender());
                logger.warn("[{}] {}}", SET_UNION_BRIDGE_ADDRESS_TAG, baseMessage);
                return UnionResponseCode.UNAUTHORIZED_CALLER.getCode();
            }

            // Check address is not already set
            RskAddress currentUnionBridgeAddress = storageProvider.getAddress().orElse(constants.getUnionBridgeAddress());
            if (unionBridgeContractAddress.equals(currentUnionBridgeAddress)) {
                String baseMessage = String.format("Union Bridge Contract Address is already set to the same value. Current address: %s", currentUnionBridgeAddress);
                logger.warn("[{}] {}", SET_UNION_BRIDGE_ADDRESS_TAG, baseMessage);
                return UnionResponseCode.GENERIC_ERROR.getCode();
            }

            storageProvider.setAddress(unionBridgeContractAddress);
            logger.info("[{}] Union Bridge Contract Address set to: {}", SET_UNION_BRIDGE_ADDRESS_TAG, unionBridgeContractAddress);
            return UnionResponseCode.SUCCESS.getCode();
        } catch (Exception e) {
            logger.error("[{}] Unexpected error setting Union Bridge Contract Address: {}", SET_UNION_BRIDGE_ADDRESS_TAG, e.getMessage(), e);
            return UnionResponseCode.GENERIC_ERROR.getCode();
        }
    }

    @Override
    public void save(ForBlock activations) {
        this.storageProvider.save(activations);
    }
}
