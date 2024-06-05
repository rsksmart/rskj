package co.rsk.peg.whitelist;

public interface WhitelistStorageProvider {

    void saveLockWhitelist();

    LockWhitelist getLockWhitelist();
}
