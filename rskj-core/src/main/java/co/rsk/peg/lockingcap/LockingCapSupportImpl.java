package co.rsk.peg.lockingcap;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.lockingcap.constants.LockingCapConstants;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import java.util.Optional;
import javax.annotation.Nonnull;
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
    public boolean increaseLockingCap(Transaction tx,@Nonnull Coin newLockingCap) throws LockingCapIllegalArgumentException {
        final String INCREASE_LOCKING_CAP_TAG = "increaseLockingCap";

        if (newLockingCap.getValue() <= 0) {
            String baseMessage = String.format("Locking Cap must be greater than zero. Value attempted: %d", newLockingCap.value);
            logger.warn("[{}] {}}", INCREASE_LOCKING_CAP_TAG, baseMessage);
            throw new LockingCapIllegalArgumentException(baseMessage);
        }

        // Only pre-configured addresses can modify Locking Cap
        AddressBasedAuthorizer authorizer = constants.getIncreaseAuthorizer();
        if (!authorizer.isAuthorized(tx, signatureCache)) {
            logger.warn("[{}] An unauthorized address tried to increase Locking Cap. Address: {}", INCREASE_LOCKING_CAP_TAG, tx.getSender(signatureCache));
            return false;
        }

        // New Locking Cap must be bigger than current Locking Cap
        Optional<Coin> currentLockingCap = getLockingCap();

        if (!currentLockingCap.isPresent()) {
            logger.warn("[{}] Current Locking Cap is not set since RSKIP134 is not active. Value attempted: {}", INCREASE_LOCKING_CAP_TAG, newLockingCap.value);
            return false;
        }

        Coin lockingCap = currentLockingCap.get();

        if (newLockingCap.compareTo(lockingCap) < 0) {
            logger.warn("[{}] Attempted value doesn't increase Locking Cap. Value attempted: {}", INCREASE_LOCKING_CAP_TAG, newLockingCap.value);
            return false;
        }

        Coin maxLockingCapVoteValueAllowed = lockingCap.multiply(constants.getIncrementsMultiplier());
        if (newLockingCap.compareTo(maxLockingCapVoteValueAllowed) > 0) {
            logger.warn("[{}] Attempted value tries to increase Locking Cap above its limit. Value attempted: {} . maxLockingCapVoteValueAllowed: {}", INCREASE_LOCKING_CAP_TAG, newLockingCap.value, maxLockingCapVoteValueAllowed.value);
            return false;
        }

        storageProvider.setLockingCap(newLockingCap);
        logger.info("[{}] Increased locking cap: {}", INCREASE_LOCKING_CAP_TAG, newLockingCap.value);

        return true;
    }

    @Override
    public void save() {
        storageProvider.save(activations);
    }
}
