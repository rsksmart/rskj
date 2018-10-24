package co.rsk.db;

import java.util.Optional;

/**
 * This allows altering an account and tracking those changes. It is stored in memory until persisted.
 */
public interface AccountChangeTracker {
    /**
     * Adds an arbitrary balance to this account.
     * @deprecated use {@link #transferTo(AccountChangeTracker, Coin)} to ensure coins are not created or destroyed.
     */
    void addBalance(Coin value);

    /**
     * Transfers balance from this account to the receiver.
     */
    default void transferTo(AccountChangeTracker receiver, Coin value) {
        if (this.getBalance().compareTo(value) < 0) {
            throw new IllegalArgumentException("Can't transfer more value than available");
        }

        this.addBalance(value.negate());
        receiver.addBalance(value);
    }

    /**
     * Increase this account nonce, usually after executing a transaction.
     * Consider encapsulating that behavior and avoiding this method that might be misused.
     */
    void increaseNonce();

    /**
     * Save an arbitrary code to this account.
     * Consider encapsulating the contract creation and avoiding this method that might be misused.
     */
    void saveCode(EvmBytecode code);

//    void setStorageValue(co.rsk.core.RskAddress to, org.ethereum.vm.DataWord key, org.ethereum.vm.DataWord value);
//
//    // How do we invalidate objects when the underlying change tracker dies?
//    void delete(co.rsk.core.RskAddress addr);




    // consider inheriting these from Account, but also take into account that the Account object shouldn't change

    /**
     * The current account balance, including pending changes.
     */
    Coin getBalance();

    /**
     * The current account nonce, including pending changes.
     */
    Nonce getNonce();

    /**
     * The current account code, including pending changes.
     */
    Optional<EvmBytecode> getCode();
}
