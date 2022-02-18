package org.ethereum.db;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.Trie;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class WrapperMutableRepository implements Repository {

    private final TrieKeyMapper trieKeyMapper;
    private final Repository mutableRepository;
    private final Set<byte[]> readKeys;
    private final Set<byte[]> writtenKeys;


    public WrapperMutableRepository(Repository mutableRepository) {
        this.trieKeyMapper = new TrieKeyMapper();
        this.mutableRepository = mutableRepository;
        this.readKeys = new HashSet<>();
        this.writtenKeys = new HashSet<>();
    }

    public Set<byte[]> getReadKeys() {
        return readKeys;
    }

    public Set<byte[]> getWrittenKeys() {
        return writtenKeys;
    }


    @Override
    public Coin getBalance(RskAddress addr) {
        readKeys.add(trieKeyMapper.getAccountKey(addr));
        return mutableRepository.getBalance(addr);
    }


    @Nullable
    @Override
    public DataWord getStorageValue(RskAddress addr, DataWord key) {
        readKeys.add(trieKeyMapper.getAccountStorageKey(addr, key));
        return mutableRepository.getStorageValue(addr, key);
    }

    @Nullable
    @Override
    public byte[] getStorageBytes(RskAddress addr, DataWord key) {
        readKeys.add(trieKeyMapper.getAccountStorageKey(addr, key));
        return mutableRepository.getStorageBytes(addr, key);
    }

    @Override
    public Iterator<DataWord> getStorageKeys(RskAddress addr) {
        Iterator<DataWord> keys = mutableRepository.getStorageKeys(addr);
        keys.forEachRemaining(key -> readKeys.add(trieKeyMapper.getAccountStorageKey(addr, key)));
        return mutableRepository.getStorageKeys(addr);
    }

    @Override
    public int getStorageKeysCount(RskAddress addr) {
        //TODO(JULI): Fijarte esto
        Iterator<DataWord> keys = mutableRepository.getStorageKeys(addr);
        keys.forEachRemaining(key -> readKeys.add(trieKeyMapper.getAccountStorageKey(addr, key)));
        return mutableRepository.getStorageKeysCount(addr);
    }

    @Nullable
    @Override
    public byte[] getCode(RskAddress addr) {
        if (isExist(addr)) {
            readKeys.add(trieKeyMapper.getCodeKey(addr));
        }

        return mutableRepository.getCode(addr);
    }

    @Override
    public boolean isContract(RskAddress addr) {
        readKeys.add(trieKeyMapper.getAccountStoragePrefixKey(addr));
        return mutableRepository.isContract(addr);
    }

    @Override
    public BigInteger getNonce(RskAddress addr) {
        readKeys.add(trieKeyMapper.getAccountKey(addr));
        return mutableRepository.getNonce(addr);
    }

    @Override
    public Keccak256 getCodeHashStandard(RskAddress addr) {

        if (isExist(addr) && isContract(addr)) {
            readKeys.add(trieKeyMapper.getCodeKey(addr));
        }

        return mutableRepository.getCodeHashStandard(addr);
    }

    @Override
    public byte[] getRoot() {
        return mutableRepository.getRoot();
    }

    @Override
    public Set<RskAddress> getAccountsKeys() {
        //TODO(JULI): Fijarte esto
        return mutableRepository.getAccountsKeys();
    }

    @Override
    public int getCodeLength(RskAddress addr) {
        AccountState account = getAccountState(addr);
        if (account != null && !account.isHibernated()) {
            readKeys.add(trieKeyMapper.getCodeKey(addr));
        }
        return mutableRepository.getCodeLength(addr);
    }

    @Override
    public Keccak256 getCodeHashNonStandard(RskAddress addr) {
        if (isExist(addr) && isContract(addr)) {
            readKeys.add(trieKeyMapper.getCodeKey(addr));
        }
        return mutableRepository.getCodeHashNonStandard(addr);
    }

    @Override
    public boolean isExist(RskAddress addr) {
        readKeys.add(trieKeyMapper.getAccountKey(addr));
        return mutableRepository.isExist(addr);
    }

    @Override
    public AccountState getAccountState(RskAddress addr) {
        readKeys.add(trieKeyMapper.getAccountKey(addr));
        return mutableRepository.getAccountState(addr);
    }

    @Override
    public Repository startTracking() {
        //TODO(Juli): De alguna manera quedarse con los write/read maps. Creo que lo mejor va a ser hacerlo de afuera.
        return new WrapperMutableRepository(mutableRepository.startTracking());
    }

    @Override
    public Trie getTrie() {
        return mutableRepository.getTrie();
    }

    @Override
    public AccountState createAccount(RskAddress addr) {
        writtenKeys.add(trieKeyMapper.getAccountKey(addr));
        return mutableRepository.createAccount(addr);
    }

    @Override
    public void setupContract(RskAddress addr) {
        writtenKeys.add(trieKeyMapper.getAccountStoragePrefixKey(addr));
        mutableRepository.setupContract(addr);
    }

    @Override
    public void delete(RskAddress addr) {
        writtenKeys.add(trieKeyMapper.getAccountKey(addr));
        mutableRepository.delete(addr);
    }

    @Override
    public void hibernate(RskAddress addr) {
        writtenKeys.add(trieKeyMapper.getAccountKey(addr));
        mutableRepository.hibernate(addr);
    }

    @Override
    public BigInteger increaseNonce(RskAddress addr) {
        writtenKeys.add(trieKeyMapper.getAccountKey(addr));
        return mutableRepository.increaseNonce(addr);
    }

    @Override
    public void setNonce(RskAddress addr, BigInteger nonce) {
        writtenKeys.add(trieKeyMapper.getAccountKey(addr));
        mutableRepository.setNonce(addr, nonce);
    }

    @Override
    public void saveCode(RskAddress addr, byte[] code) {
        writtenKeys.add(trieKeyMapper.getCodeKey(addr));

        if (code != null && code.length != 0 && !isExist(addr)) {
            writtenKeys.add(trieKeyMapper.getAccountKey(addr)); //TODO(JULI): Chequear si hay algun problema que este en written y read
        }

        mutableRepository.saveCode(addr, code);
    }

    @Override
    public void addStorageRow(RskAddress addr, DataWord key, DataWord value) {
        addStorageBytes(addr, key, value.getByteArrayForStorage());
    }

    @Override
    public void addStorageBytes(RskAddress addr, DataWord key, byte[] value) {
        if (!isExist(addr)) {
            writtenKeys.add(trieKeyMapper.getAccountKey(addr));
            writtenKeys.add(trieKeyMapper.getAccountStoragePrefixKey(addr));
        }

        writtenKeys.add(trieKeyMapper.getAccountStorageKey(addr, key));
        mutableRepository.addStorageBytes(addr, key, value);
    }

    @Override
    public Coin addBalance(RskAddress addr, Coin value) {
        writtenKeys.add(trieKeyMapper.getAccountKey(addr));
        return mutableRepository.addBalance(addr, value);
    }

    @Override
    public void commit() {
        mutableRepository.commit();
    }

    @Override
    public void rollback() {
        mutableRepository.rollback();
    }

    @Override
    public void save() {
        mutableRepository.save();
    }

    @Override
    public void updateAccountState(RskAddress addr, AccountState accountState) {
        writtenKeys.add(trieKeyMapper.getAccountKey(addr));
        mutableRepository.updateAccountState(addr, accountState);
    }
}
