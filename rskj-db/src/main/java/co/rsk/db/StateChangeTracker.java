package co.rsk.db;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * This keeps track of changes happening to the blockchain in memory, with regards to a base snapshot.
 * Each instance is single-threaded but multiple copies can be used independently.
 */
public class StateChangeTracker {
    private final StateSnapshot baseSnapshot;
    private final TrieMapper trieMapper;
    // consider having a list of commands instead of changes, deletes, etc.
    private final Map<TrieKey, TrieValue> changes = new HashMap<>();

    // TODO(mc) create a constructor that receives a BlockchainChangeTrackerImpl and performs a deep copy
    public StateChangeTracker(StateSnapshot baseSnapshot, TrieMapper trieMapper) {
        this.baseSnapshot = Objects.requireNonNull(baseSnapshot);
        this.trieMapper = Objects.requireNonNull(trieMapper);
    }

    public boolean hasAccountData(Address address) {
        TrieKey trieKey = trieMapper.addressToAccountKey(address);
        return changes.containsKey(trieKey) || baseSnapshot.findAccount(address).isPresent();
    }

    // consider creating accounts with balance only
    public AccountChangeTracker getOrCreateAccount(Address address) {
        return new MyAccountChangeTracker(address);
    }

    public Map<TrieKey, TrieValue> getChanges() {
        return new HashMap<>(changes);
    }

    private class MyAccountChangeTracker implements AccountChangeTracker {
        private final Address address;
        private final TrieKey accountKey;
        private final TrieKey codeKey;

        public MyAccountChangeTracker(Address address) {
            this.address = Objects.requireNonNull(address);
            this.accountKey = Objects.requireNonNull(trieMapper.addressToAccountKey(address));
            this.codeKey = Objects.requireNonNull(trieMapper.accountToCodeKey(getAccount()));
        }

        @Override
        public void addBalance(Coin value) {
            Account account = getAccount();
            TrieValue trieValue = trieMapper.accountToValue(account.getBalance().add(value), account.getNonce());
            changes.put(accountKey, trieValue);
        }

        @Override
        public void increaseNonce() {
            Account account = getAccount();
            TrieValue trieValue = trieMapper.accountToValue(account.getBalance(), account.getNonce().next());
            changes.put(accountKey, trieValue);
        }

        @Override
        public void saveCode(/* TODO Nullable */ EvmBytecode code) {
            // TODO update codeHash
            TrieValue trieValue = trieMapper.codeToValue(code);
            changes.put(codeKey, trieValue);
        }

        @Override
        public Coin getBalance() {
            return getAccount().getBalance();
        }

        @Override
        public Nonce getNonce() {
            return getAccount().getNonce();
        }

        @Override
        public Optional<EvmBytecode> getCode() {
            TrieValue currentCode = changes.get(codeKey);
            if (currentCode != null) {
                return Optional.of(trieMapper.valueToCode(currentCode));
            }

            return baseSnapshot.findCode(getAccount());
        }

        private Account getAccount() {
            TrieValue currentValue = changes.get(accountKey);
            if (currentValue != null) {
                return trieMapper.valueToAccount(currentValue);
            }

            return baseSnapshot.findAccount(address).orElse(emptyAccount());
        }
    }

    // TODO this doesn't belong here
    private static Account emptyAccount() {
        return new Account() {
            @Override
            public Coin getBalance() {
                return Coin.ZERO;
            }

            @Override
            public Nonce getNonce() {
                return Nonce.ZERO;
            }
        };
    }
}
