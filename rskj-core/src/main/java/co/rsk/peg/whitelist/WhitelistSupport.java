package co.rsk.peg.whitelist;

import java.math.BigInteger;
import org.ethereum.core.Transaction;

/**
 * Interface for the whitelist support. This interface is used to add, remove and get whitelist
 * entries.
 */
public interface WhitelistSupport {

    int getLockWhitelistSize();

    LockWhitelistEntry getLockWhitelistEntryByIndex(int index);

    LockWhitelistEntry getLockWhitelistEntryByAddress(String addressBase58);

    int addOneOffLockWhitelistAddress(Transaction tx, String addressBase58, BigInteger maxTransferValue);

    int addUnlimitedLockWhitelistAddress(Transaction tx, String addressBase58);

    int removeLockWhitelistAddress(Transaction tx, String addressBase58);

    int setLockWhitelistDisableBlockDelay(Transaction tx, BigInteger disableBlockDelayBI);
}
