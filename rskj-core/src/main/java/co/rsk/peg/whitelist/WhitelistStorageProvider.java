package co.rsk.peg.whitelist;

import co.rsk.bitcoinj.core.NetworkParameters;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

/**
 * Interface for storage access for whitelisting.
 */
public interface WhitelistStorageProvider {

    void save(ActivationConfig.ForBlock activations);

    LockWhitelist getLockWhitelist(ActivationConfig.ForBlock activations, NetworkParameters networkParameters);
}
