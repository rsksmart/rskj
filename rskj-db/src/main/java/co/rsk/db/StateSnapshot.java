package co.rsk.db;

import java.util.Optional;

/**
 * An immutable snapshot of the blockchain state.
 * Implementations rely on the (also immutable) Trie data structure.
 */
public interface StateSnapshot {
    /**
     * Returns the account associated with the address or empty if there isn't one
     */
    Optional<AccountState> findAccountState(Address address);

    /**
     * Returns the code associated with this account or empty if there isn't one
     * TODO consider having an AccountState::getCode method instead
     */
    Optional<EvmBytecode> findCode(Address address);
}
