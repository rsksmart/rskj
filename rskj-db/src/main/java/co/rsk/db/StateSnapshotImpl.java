package co.rsk.db;

import java.util.Objects;
import java.util.Optional;

// this talks business language (e.g. addresses), so it might belong to the other module
// consider exposing all methods related to an account in a separate class
public class StateSnapshotImpl implements StateSnapshot {
    private final Trie trie;
    private final TrieMapper trieMapper;

    public StateSnapshotImpl(Trie trie, TrieMapper trieMapper) {
        this.trie = Objects.requireNonNull(trie);
        this.trieMapper = Objects.requireNonNull(trieMapper);
    }

    @Override
    public Optional<AccountState> findAccountState(Address address) {
        return trie.find(trieMapper.addressToAccountKey(address)).map(trieMapper::valueToAccount);
    }

    @Override
    public Optional<EvmBytecode> findCode(Address address) {
        return trie.find(trieMapper.addressToCodeKey(address)).map(trieMapper::valueToCode);
    }

//
//    // GETTERS
//
//    // 1. consider using a Nonce class
//    // 2. consider doing getAccountState().getNonce()
//    BigInteger getNonce(co.rsk.core.RskAddress from);
//
//    // 1. consider doing getAccountState().getBalance()
//    co.rsk.core.Coin getBalance(co.rsk.core.RskAddress from);
//
//    // 1. consider doing getAccountState().isPresent()
//    boolean accountExists(co.rsk.core.RskAddress addr);
//
//    // 1. consider doing getContractDetails().getCode()
//    // 2. consider using an EvmBytecode class
//    byte[] getCode(co.rsk.core.RskAddress from);
//
//    org.ethereum.vm.DataWord getStorageValue(co.rsk.core.RskAddress to, org.ethereum.vm.DataWord key);
//
//
//    // CHANGE TRACKING
//
//    void transfer(co.rsk.core.RskAddress from, co.rsk.core.RskAddress to, co.rsk.core.Coin value);
//
//    // consider not having this method at all. adding balance that comes from who knows where doesn't seem safe
//    void addBalance(co.rsk.core.RskAddress to, co.rsk.core.Coin value);
//
//    // consider encapsulating the contract creation and not providing this method that might misused
//    void increaseNonce(co.rsk.core.RskAddress addr);
//
//    // consider creating accounts with balance only. Moreover, how do we keep track of changes? How do we invalidate objects when the underlying change tracker dies?
//    co.rsk.core.AccountState createAccount(co.rsk.core.RskAddress addr);
//
//    // consider encapsulating the contract creation and not providing this method that might misused
//    // consider using an EvmBytecode class
//    void saveCode(co.rsk.core.RskAddress addr, byte[] code);
//
//    void setStorageValue(co.rsk.core.RskAddress to, org.ethereum.vm.DataWord key, org.ethereum.vm.DataWord value);
//
//    // How do we invalidate objects when the underlying change tracker dies?
//    void delete(co.rsk.core.RskAddress addr);
}
