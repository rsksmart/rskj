package co.rsk.peg.whitelist;

import static co.rsk.peg.whitelist.WhitelistResponseCode.ADDRESS_ALREADY_WHITELISTED;
import static co.rsk.peg.whitelist.WhitelistResponseCode.ADDRESS_NOT_EXIST;
import static co.rsk.peg.whitelist.WhitelistResponseCode.DELAY_ALREADY_SET;
import static co.rsk.peg.whitelist.WhitelistResponseCode.DISABLE_BLOCK_DELAY_INVALID;
import static co.rsk.peg.whitelist.WhitelistResponseCode.INVALID_ADDRESS_FORMAT;
import static co.rsk.peg.whitelist.WhitelistResponseCode.SUCCESS;
import static co.rsk.peg.whitelist.WhitelistResponseCode.UNAUTHORIZED_CALLER;
import static co.rsk.peg.whitelist.WhitelistResponseCode.UNKNOWN_ERROR;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.AddressFormatException;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.peg.vote.AddressBasedAuthorizer;
import co.rsk.peg.whitelist.constants.WhitelistConstants;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the WhitelistSupport interface. Contains all the whitelist business logic.
 */
public class WhitelistSupportImpl implements WhitelistSupport {

    private static final Logger logger = LoggerFactory.getLogger(WhitelistSupportImpl.class);
    private static final String INVALID_ADDRESS_FORMAT_MESSAGE = "invalid address format";
    private final SignatureCache signatureCache;
    private final Context btcContext;
    private final WhitelistConstants whitelistConstants;
    private final WhitelistStorageProvider whitelistStorageProvider;

    public WhitelistSupportImpl(
        WhitelistStorageProvider whitelistStorageProvider,
        WhitelistConstants whitelistConstants,
        SignatureCache signatureCache,
        Context btcContext) {
        this.whitelistStorageProvider = whitelistStorageProvider;
        this.whitelistConstants = whitelistConstants;
        this.signatureCache = signatureCache;
        this.btcContext = btcContext;
    }

    /**
     * Returns the lock whitelist size, that is, the number of whitelisted addresses
     *
     * @return the lock whitelist size
     */
    @Override
    public int getLockWhitelistSize() {
        return whitelistStorageProvider.getLockWhitelist().getSize();
    }

    /**
     * Returns the lock whitelist address stored at the given index, or null if the index is out of
     * bounds
     *
     * @param index the index at which to get the address
     * @return the base58-encoded address stored at the given index, or null if index is out of
     * bounds
     */
    @Override
    public LockWhitelistEntry getLockWhitelistEntryByIndex(int index) {
        List<LockWhitelistEntry> entries = whitelistStorageProvider.getLockWhitelist().getAll();

        if (index < 0 || index >= entries.size()) {
            return null;
        }
        return entries.get(index);
    }

    @Override
    public LockWhitelistEntry getLockWhitelistEntryByAddress(String addressBase58) {
        try {
            Address address = Address.fromBase58(btcContext.getParams(), addressBase58);

            return whitelistStorageProvider.getLockWhitelist().get(address);
        } catch (AddressFormatException e) {
            logger.warn(INVALID_ADDRESS_FORMAT_MESSAGE, e);
            return null;
        }
    }

    /**
     * Adds the given address to the lock whitelist.
     *
     * @param addressBase58    the base58-encoded address to add to the whitelist
     * @param maxTransferValue the max amount of satoshis enabled to transfer for this address
     * @return 1 SUCCESS, -1 ADDRESS_ALREADY_WHITELISTED, -2 INVALID_ADDRESS_FORMAT,
     * -10 UNAUTHORIZED_CALLER, 0 UNKNOWN_ERROR.
     */
    @Override
    public int addOneOffLockWhitelistAddress(Transaction tx, String addressBase58, BigInteger maxTransferValue) {
        try {
            Address address = Address.fromBase58(btcContext.getParams(), addressBase58);
            Coin maxTransferValueCoin = Coin.valueOf(maxTransferValue.longValueExact());
            LockWhitelistEntry entry = new OneOffWhiteListEntry(address, maxTransferValueCoin);

            return this.addLockWhitelistAddress(tx, entry);
        } catch (AddressFormatException e) {
            logger.warn(INVALID_ADDRESS_FORMAT_MESSAGE, e);
            return INVALID_ADDRESS_FORMAT.getCode();
        }
    }

    @Override
    public int addUnlimitedLockWhitelistAddress(Transaction tx, String addressBase58) {
        try {
            Address address = Address.fromBase58(btcContext.getParams(), addressBase58);
            LockWhitelistEntry entry = new UnlimitedWhiteListEntry(address);

            return this.addLockWhitelistAddress(tx, entry);
        } catch (AddressFormatException e) {
            logger.warn(INVALID_ADDRESS_FORMAT_MESSAGE, e);
            return INVALID_ADDRESS_FORMAT.getCode();
        }
    }

    /**
     * Removes the given address from the lock whitelist.
     *
     * @param addressBase58 the base58-encoded address to remove from the whitelist
     * @return 1 SUCCESS, -1 ADDRESS_NOT_EXIST, -2 INVALID_ADDRESS_FORMAT, -10 UNAUTHORIZED_CALLER,
     * 0 UNKNOWN_ERROR.
     */
    @Override
    public int removeLockWhitelistAddress(Transaction tx, String addressBase58) {
        if (isNotAuthorizedLockWhitelistChange(tx)) {
            return UNAUTHORIZED_CALLER.getCode();
        }

        LockWhitelist whitelist = whitelistStorageProvider.getLockWhitelist();
        try {
            Address address = Address.fromBase58(btcContext.getParams(), addressBase58);

            if (!whitelist.remove(address)) {
                return ADDRESS_NOT_EXIST.getCode();
            }
            return SUCCESS.getCode();
        } catch (AddressFormatException e) {
            logger.error("Invalid Address Format error in removeLockWhitelistAddress: {}", e.getMessage());
            return INVALID_ADDRESS_FORMAT.getCode();
        } catch (Exception e) {
            logger.error("Unexpected error in removeLockWhitelistAddress: {}", e.getMessage());
            return UNKNOWN_ERROR.getCode();
        }
    }

    /**
     * Sets a delay in the BTC best chain to disable lock whitelist
     *
     * @param tx                  current RSK transaction
     * @param disableBlockDelayBI block since current BTC best chain height to disable lock
     *                            whitelist
     * @return 1 SUCCESS, -1 DELAY_ALREADY_SET, -2 DISABLE_BLOCK_DELAY_INVALID, -10 UNAUTHORIZED_CALLER
     */
    @Override
    public int setLockWhitelistDisableBlockDelay(Transaction tx, BigInteger disableBlockDelayBI, int btcBlockchainBestChainHeight)
        throws IOException, BlockStoreException {

        if (isNotAuthorizedLockWhitelistChange(tx)) {
            return UNAUTHORIZED_CALLER.getCode();
        }

        LockWhitelist lockWhitelist = whitelistStorageProvider.getLockWhitelist();
        if (lockWhitelist.isDisableBlockSet()) {
            return DELAY_ALREADY_SET.getCode();
        }

        int disableBlockDelay = disableBlockDelayBI.intValueExact();
        if (disableBlockDelay + btcBlockchainBestChainHeight <= btcBlockchainBestChainHeight) {
            return DISABLE_BLOCK_DELAY_INVALID.getCode();
        }

        lockWhitelist.setDisableBlockHeight(btcBlockchainBestChainHeight + disableBlockDelay);

        return SUCCESS.getCode();
    }

    @Override
    public boolean verifyLockSenderIsWhitelisted(Address senderBtcAddress, Coin totalAmount, int height) {
        // If the address is not whitelisted, then return the funds
        // using the exact same utxos sent to us.
        // That is, build a pegout waiting for confirmations and get it in the pegoutWaitingForConfirmations set.
        // Otherwise, transfer SBTC to the sender of the BTC
        // The RSK account to update is the one that matches the pubkey "spent" on the first bitcoin tx input
        LockWhitelist lockWhitelist = whitelistStorageProvider.getLockWhitelist();
        if (!lockWhitelist.isWhitelistedFor(senderBtcAddress, totalAmount, height)) {
            logger.info("Rejecting lock. Address {} is not whitelisted.", senderBtcAddress);
            return false;
        }
        // Consume this whitelisted address
        lockWhitelist.consume(senderBtcAddress);

        return true;
    }

    private Integer addLockWhitelistAddress(Transaction tx, LockWhitelistEntry entry) {
        if (isNotAuthorizedLockWhitelistChange(tx)) {
            return UNAUTHORIZED_CALLER.getCode();
        }

        LockWhitelist whitelist = whitelistStorageProvider.getLockWhitelist();
        try {
            if (whitelist.isWhitelisted(entry.address())) {
                return ADDRESS_ALREADY_WHITELISTED.getCode();
            }
            whitelist.put(entry.address(), entry);
            return SUCCESS.getCode();
        } catch (Exception e) {
            logger.error("Unexpected error in addLockWhitelistAddress: {}", e.getMessage());
            return UNKNOWN_ERROR.getCode();
        }
    }

    private boolean isNotAuthorizedLockWhitelistChange(Transaction tx) {
        AddressBasedAuthorizer authorizer = whitelistConstants.getLockWhitelistChangeAuthorizer();
        return !authorizer.isAuthorized(tx, signatureCache);
    }
}
