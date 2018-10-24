package co.rsk.db;

import java.util.*;

/**
 * This keeps track of changes happening to the blockchain in memory, with regards to a base snapshot.
 * Each instance is single-threaded but multiple copies can be used independently.
 */
public class StateChangeTracker {
    private final StateSnapshot baseSnapshot;
    private final Map<Address, AccountChangeTracker> accountChangeTrackers = new HashMap<>();
    private final List<StateChangeCommand> changeCommands = new ArrayList<>();

    // TODO(mc) create a constructor that receives a StateChangeTracker and performs a deep copy
    public StateChangeTracker(StateSnapshot baseSnapshot) {
        this.baseSnapshot = Objects.requireNonNull(baseSnapshot);
    }

    public boolean hasAccountData(Address address) {
        return accountChangeTrackers.containsKey(address) || baseSnapshot.findAccountState(address).isPresent();
    }

    // consider only creating accounts with balance
    public AccountChangeTracker getOrCreateAccount(Address address) {
        return accountChangeTrackers.computeIfAbsent(
                address,
                a -> new MyAccountChangeTracker(
                        a,
                        baseSnapshot.findAccountState(a).orElse(AccountState.emptyAccount()),
                        baseSnapshot.findCode(a).orElse(null)
                )
        );
    }

    public List<StateChangeCommand> getChanges() {
        return Collections.unmodifiableList(changeCommands);
    }

    private class MyAccountChangeTracker implements AccountChangeTracker {
        private final Address address;

        private AccountState accountState;
        // TODO Nullable
        private EvmBytecode code;

        public MyAccountChangeTracker(Address address, AccountState accountState, /* TODO Nullable */ EvmBytecode code) {
            this.address = Objects.requireNonNull(address);
            this.accountState = Objects.requireNonNull(accountState);
            this.code = code;
        }

        @Override
        public void addBalance(Coin value) {
            accountState = new AccountState(accountState.getBalance().add(value), accountState.getNonce());
            changeCommands.add(r -> r.saveAccountState(address, accountState));
        }

        @Override
        public void increaseNonce() {
            accountState = new AccountState(accountState.getBalance(), accountState.getNonce().next());
            changeCommands.add(r -> r.saveAccountState(address, accountState));
        }

        @Override
        public void saveCode(/* TODO Nullable */ EvmBytecode newCode) {
            // TODO update codeHash
            code = newCode;
            changeCommands.add(r -> r.saveCode(address, code));
        }

        @Override
        public Coin getBalance() {
            return accountState.getBalance();
        }

        @Override
        public Nonce getNonce() {
            return accountState.getNonce();
        }

        @Override
        public Optional<EvmBytecode> getCode() {
            return Optional.ofNullable(code);
        }
    }
}
