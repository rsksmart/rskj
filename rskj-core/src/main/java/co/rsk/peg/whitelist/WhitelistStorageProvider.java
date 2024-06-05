package co.rsk.peg.whitelist;

/**
 * Interface for storage access for whitelisting.
 */
public interface WhitelistStorageProvider {

    void save();

    LockWhitelist getLockWhitelist();
}
