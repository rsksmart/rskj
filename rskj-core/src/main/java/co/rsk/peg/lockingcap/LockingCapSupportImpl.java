package co.rsk.peg.lockingcap;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.BridgeIllegalArgumentException;
import co.rsk.peg.lockingcap.constants.LockingCapConstants;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LockingCapSupportImpl implements LockingCapSupport {

    private static final Logger logger = LoggerFactory.getLogger(LockingCapSupportImpl.class);
    private final LockingCapStorageProvider storageProvider;
    private final ActivationConfig.ForBlock activations;
    private final LockingCapConstants constants;
    private final SignatureCache signatureCache;

    public LockingCapSupportImpl(
        LockingCapStorageProvider storageProvider,
        ActivationConfig.ForBlock activations,
        LockingCapConstants constants,
        SignatureCache signatureCache
    ) {
        this.storageProvider = storageProvider;
        this.activations = activations;
        this.constants = constants;
        this.signatureCache = signatureCache;
    }

    @Override
    public Optional<Coin> getLockingCap() {
        Optional<Coin> lockingCap = storageProvider.getLockingCap(activations);

        // Before returning the locking cap, check if it was already set
        if (!lockingCap.isPresent() && activations.isActive(ConsensusRule.RSKIP134)) {
            // Set the initial Locking Cap value
            logger.debug("[getLockingCap] {}", "Setting initial Locking Cap value");
            storageProvider.setLockingCap(constants.getInitialValue());
        }
        return storageProvider.getLockingCap(activations);
    }

    @Override
    public boolean increaseLockingCap(Transaction tx, Coin newLockingCap) throws BridgeIllegalArgumentException {
        if (newLockingCap.getValue() <= 0) {
            logger.warn("[increaseLockingCap] {} {}", "Locking Cap must be greater than zero. Value attempted: ", newLockingCap.value);
            throw new BridgeIllegalArgumentException("Locking Cap must be greater than zero");
        }

        // Only pre-configured addresses can modify Locking Cap
        AddressBasedAuthorizer authorizer = constants.getIncreaseAuthorizer();
        if (!authorizer.isAuthorized(tx, signatureCache)) {
            logger.warn("[increaseLockingCap] {} {}", "Not authorized address tried to increase Locking Cap. Address: ", tx.getSender(signatureCache));
            return false;
        }

        // New Locking Cap must be bigger than current Locking Cap
        Optional<Coin> currentLockingCap = getLockingCap();

        if (!currentLockingCap.isPresent()) {
            logger.warn("[increaseLockingCap] {}", "Current Locking Cap is not set since RSKIP134 is not active");
            return false;
        }

        Coin lockingCap = currentLockingCap.get();

        if (newLockingCap.compareTo(lockingCap) < 0) {
            logger.warn("[increaseLockingCap] {} {}", "Attempted value doesn't increase Locking Cap. Value attempted: ", newLockingCap.value);
            return false;
        }

        Coin maxLockingCapVoteValueAllowed = lockingCap.multiply(constants.getIncrementsMultiplier());
        if (newLockingCap.compareTo(maxLockingCapVoteValueAllowed) > 0) {
            logger.warn("[increaseLockingCap] {} {}", "Attempted value increases Locking Cap above its limit. Value attempted: ", newLockingCap.value);
            return false;
        }

        storageProvider.setLockingCap(newLockingCap);
        logger.info("[increaseLockingCap] {} {}", "Increased locking cap: ", newLockingCap.value);

        return true;
    }

    @Override
    public void save() {
        storageProvider.save(activations);
    }
}
