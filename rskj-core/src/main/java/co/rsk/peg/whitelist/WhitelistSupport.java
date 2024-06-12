package co.rsk.peg.whitelist;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;
import java.math.BigInteger;
import org.ethereum.core.Transaction;

/**
 * Interface for the whitelist support. This interface is used to add, remove and get whitelist
 * entries.
 */
public interface WhitelistSupport {

    /**
     * Returns the lock whitelist size, that is, the number of whitelisted addresses
     *
     * @return the lock whitelist size
     */
    int getLockWhitelistSize();

    /**
     * Returns the lock whitelist address stored at the given index, or null if the index is out of
     * bounds
     *
     * @param index the index at which to get the address
     * @return the base58-encoded address stored at the given index, or null if index is out of
     * bounds
     */
    LockWhitelistEntry getLockWhitelistEntryByIndex(int index);

    LockWhitelistEntry getLockWhitelistEntryByAddress(String addressBase58);

    /**
     * Adds the given address to the lock whitelist.
     *
     * @param addressBase58    the base58-encoded address to add to the whitelist
     * @param maxTransferValue the max amount of satoshis enabled to transfer for this address
     * @return 1 SUCCESS, -1 ADDRESS_ALREADY_WHITELISTED, -2 INVALID_ADDRESS_FORMAT,
     * -10 UNAUTHORIZED_CALLER.
     */
    int addOneOffLockWhitelistAddress(Transaction tx, String addressBase58, BigInteger maxTransferValue);

    int addUnlimitedLockWhitelistAddress(Transaction tx, String addressBase58);

    /**
     * Removes the given address from the lock whitelist.
     *
     * @param addressBase58 the base58-encoded address to remove from the whitelist
     * @return 1 SUCCESS, -1 ADDRESS_NOT_EXIST, -2 INVALID_ADDRESS_FORMAT, -10 UNAUTHORIZED_CALLER.
     */
    int removeLockWhitelistAddress(Transaction tx, String addressBase58);

    /**
     * Sets a delay in the BTC best chain to disable lock whitelist
     *
     * @param tx                  current RSK transaction
     * @param disableBlockDelayBI block since current BTC best chain height to disable lock
     *                            whitelist
     * @return 1 SUCCESS, -1 DELAY_ALREADY_SET, -2 DISABLE_BLOCK_DELAY_INVALID, -10 UNAUTHORIZED_CALLER
     */
    int setLockWhitelistDisableBlockDelay(Transaction tx, BigInteger disableBlockDelayBI, int btcBlockchainBestChainHeight);

    boolean verifyLockSenderIsWhitelisted(Address senderBtcAddress, Coin totalAmount, int height);
}
