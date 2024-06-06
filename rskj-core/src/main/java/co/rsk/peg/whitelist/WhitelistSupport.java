package co.rsk.peg.whitelist;

import java.math.BigInteger;
import org.ethereum.core.Transaction;

public interface WhitelistSupport {

    Integer getLockWhitelistSize();

    LockWhitelistEntry getLockWhitelistEntryByIndex(int index);

    LockWhitelistEntry getLockWhitelistEntryByAddress(String addressBase58);

    Integer addOneOffLockWhitelistAddress(Transaction tx, String addressBase58, BigInteger maxTransferValue);

    Integer addUnlimitedLockWhitelistAddress(Transaction tx, String addressBase58);

    Integer removeLockWhitelistAddress(Transaction tx, String addressBase58);

    Integer setLockWhitelistDisableBlockDelay(Transaction tx, BigInteger disableBlockDelayBI);
}
