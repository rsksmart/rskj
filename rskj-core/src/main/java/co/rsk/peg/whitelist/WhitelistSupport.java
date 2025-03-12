package co.rsk.peg.whitelist;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.Coin;
import java.math.BigInteger;
import java.util.Optional;

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
     * Returns the lock whitelist entry stored at the given index, or an empty Optional if the index is out of
     * bounds
     *
     * @param index the index at which to get the entry
     * @return the whitelist entry stored at the given index, or an empty Optional if index is out of
     * bounds
     */
    Optional<LockWhitelistEntry> getLockWhitelistEntryByIndex(int index);

    /**
     * Returns the lock whitelist entry for a given address, or an empty Optional if the address is not whitelisted
     *
     * @param addressBase58 the address in base58 format to search for
     * @return the whitelist entry for the given address, or an empty Optional if the addrres is not whitelisted
     */
    Optional<LockWhitelistEntry> getLockWhitelistEntryByAddress(String addressBase58);

    /**
     * Adds the given address to the lock whitelist, allowing peg-ins up to certain max value
     *
     * @param tx the RSK transaction where the call was made
     * @param addressBase58    the base58-encoded address to add to the whitelist
     * @param maxTransferValue the max amount of satoshis enabled to transfer for this address
     * @return 1 SUCCESS, -1 ADDRESS_ALREADY_WHITELISTED, -2 INVALID_ADDRESS_FORMAT,
     * -10 UNAUTHORIZED_CALLER.
     */
    int addOneOffLockWhitelistAddress(Transaction tx, String addressBase58, BigInteger maxTransferValue);

    /**
     * Adds the given address to the lock whitelist, allowing unlimited peg-in value
     *
     * @param tx the RSK transaction where the call was made
     * @param addressBase58    the base58-encoded address to add to the whitelist
     * @return 1 SUCCESS, -1 ADDRESS_ALREADY_WHITELISTED, -2 INVALID_ADDRESS_FORMAT,
     * -10 UNAUTHORIZED_CALLER.
     */
    int addUnlimitedLockWhitelistAddress(Transaction tx, String addressBase58);

    /**
     * Removes the given address from the lock whitelist.
     *
     * @param tx the RSK transaction where the call was made
     * @param addressBase58 the base58-encoded address to remove from the whitelist
     * @return 1 SUCCESS, -1 ADDRESS_NOT_EXIST, -2 INVALID_ADDRESS_FORMAT, -10 UNAUTHORIZED_CALLER.
     */
    int removeLockWhitelistAddress(Transaction tx, String addressBase58);

    /**
     * Sets a delay in the BTC best chain to disable lock whitelist
     *
     * @param tx the RSK transaction where the call was made
     * @param disableBlockDelayBI block since current BTC best chain height to disable lock whitelist
     * @param btcBlockchainBestChainHeight current btc blockchain best chain height
     * @return 1 SUCCESS, -1 DELAY_ALREADY_SET, -2 DISABLE_BLOCK_DELAY_INVALID, -10 UNAUTHORIZED_CALLER
     */
    int setLockWhitelistDisableBlockDelay(Transaction tx, BigInteger disableBlockDelayBI, int btcBlockchainBestChainHeight);

    /**
     * Checks that a given address is whitelisted and authorized to perform peg-in operations
     *
     * @param senderBtcAddress the Bitcoin address to check if it's whitelisted
     * @param totalAmount the amount that is trying to peg
     * @param height the Bitcoin network height where the peg-in transaction was included
     * @return true if sender is authorized to peg-in the corresponding amount, false otherwise
     */
    boolean verifyLockSenderIsWhitelisted(Address senderBtcAddress, Coin totalAmount, int height);

    /**
     * Saves the whitelisted addresses to the storage
     */
    void save();
}
