package org.ethereum.db;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.ReadWrittenKeysTracker;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.Trie;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Set;

public class WrapperMutableRepository implements Repository {

    private final TrieKeyMapper trieKeyMapper;
    private final Repository mutableRepository;
    private final ReadWrittenKeysTracker readWrittenKeysTracker;


    public WrapperMutableRepository(Repository mutableRepository, ReadWrittenKeysTracker readWrittenKeysTracker) {
        this.readWrittenKeysTracker = readWrittenKeysTracker;
        this.trieKeyMapper = new TrieKeyMapper();
        this.mutableRepository = mutableRepository;
    }

    @Override
    public Coin getBalance(RskAddress addr) {
        readWrittenKeysTracker.addNewReadKey(new ByteArrayWrapper(trieKeyMapper.getAccountKey(addr)));
        return mutableRepository.getBalance(addr);
    }


    @Nullable
    @Override
    public DataWord getStorageValue(RskAddress addr, DataWord key) {
        readWrittenKeysTracker.addNewReadKey(new ByteArrayWrapper(trieKeyMapper.getAccountStorageKey(addr, key)));
        return mutableRepository.getStorageValue(addr, key);
    }

    @Nullable
    @Override
    public byte[] getStorageBytes(RskAddress addr, DataWord key) {
        readWrittenKeysTracker.addNewReadKey(new ByteArrayWrapper(trieKeyMapper.getAccountStorageKey(addr, key)));
        return mutableRepository.getStorageBytes(addr, key);
    }

    @Override
    public Iterator<DataWord> getStorageKeys(RskAddress addr) {
        Iterator<DataWord> keys = mutableRepository.getStorageKeys(addr);
        keys.forEachRemaining(key -> readWrittenKeysTracker.addNewReadKey(new ByteArrayWrapper(trieKeyMapper.getAccountStorageKey(addr, key))));
        return mutableRepository.getStorageKeys(addr);
    }

    @Override
    public int getStorageKeysCount(RskAddress addr) {
        //TODO(JULI): Fijarte esto
        Iterator<DataWord> keys = mutableRepository.getStorageKeys(addr);
        keys.forEachRemaining(key -> readWrittenKeysTracker.addNewReadKey(new ByteArrayWrapper(trieKeyMapper.getAccountStorageKey(addr, key))));
        return mutableRepository.getStorageKeysCount(addr);
    }

    @Nullable
    @Override
    public byte[] getCode(RskAddress addr) {
        if (isExist(addr)) {
            readWrittenKeysTracker.addNewReadKey(new ByteArrayWrapper(trieKeyMapper.getCodeKey(addr)));
        }

        return mutableRepository.getCode(addr);
    }

    @Override
    public boolean isContract(RskAddress addr) {
        readWrittenKeysTracker.addNewReadKey(new ByteArrayWrapper(trieKeyMapper.getAccountStoragePrefixKey(addr)));
        return mutableRepository.isContract(addr);
    }

    @Override
    public BigInteger getNonce(RskAddress addr) {
        readWrittenKeysTracker.addNewReadKey(new ByteArrayWrapper(trieKeyMapper.getAccountKey(addr)));
        return mutableRepository.getNonce(addr);
    }

    @Override
    public Keccak256 getCodeHashStandard(RskAddress addr) {

        if (isExist(addr) && isContract(addr)) {
            readWrittenKeysTracker.addNewReadKey(new ByteArrayWrapper(trieKeyMapper.getCodeKey(addr)));
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
            readWrittenKeysTracker.addNewReadKey(new ByteArrayWrapper(trieKeyMapper.getCodeKey(addr)));
        }
        return mutableRepository.getCodeLength(addr);
    }

    @Override
    public Keccak256 getCodeHashNonStandard(RskAddress addr) {
        if (isExist(addr) && isContract(addr)) {
            readWrittenKeysTracker.addNewReadKey(new ByteArrayWrapper(trieKeyMapper.getCodeKey(addr)));
        }
        return mutableRepository.getCodeHashNonStandard(addr);
    }

    @Override
    public boolean isExist(RskAddress addr) {
        readWrittenKeysTracker.addNewReadKey(new ByteArrayWrapper(trieKeyMapper.getAccountKey(addr)));
        return mutableRepository.isExist(addr);
    }

    @Override
    public AccountState getAccountState(RskAddress addr) {
        readWrittenKeysTracker.addNewReadKey(new ByteArrayWrapper(trieKeyMapper.getAccountKey(addr)));
        return mutableRepository.getAccountState(addr);
    }

    @Override
    public Repository startTracking() {
        return new WrapperMutableRepository(mutableRepository.startTracking(), readWrittenKeysTracker);
    }

    @Override
    public Trie getTrie() {
        return mutableRepository.getTrie();
    }

    @Override
    public AccountState createAccount(RskAddress addr) {
        readWrittenKeysTracker.addNewWrittenKey(new ByteArrayWrapper(trieKeyMapper.getAccountKey(addr)));
        return mutableRepository.createAccount(addr);
    }

    @Override
    public void setupContract(RskAddress addr) {
        readWrittenKeysTracker.addNewWrittenKey(new ByteArrayWrapper(trieKeyMapper.getAccountStoragePrefixKey(addr)));
        mutableRepository.setupContract(addr);
    }

    @Override
    public void delete(RskAddress addr) {
        //TODO(JULI): Check if it has to be deleted from the writtenKeys
        readWrittenKeysTracker.addNewWrittenKey(new ByteArrayWrapper(trieKeyMapper.getAccountKey(addr)));
        mutableRepository.delete(addr);
    }

    @Override
    public void hibernate(RskAddress addr) {
        readWrittenKeysTracker.addNewWrittenKey(new ByteArrayWrapper(trieKeyMapper.getAccountKey(addr)));
        mutableRepository.hibernate(addr);
    }

    @Override
    public BigInteger increaseNonce(RskAddress addr) {
        readWrittenKeysTracker.addNewWrittenKey(new ByteArrayWrapper(trieKeyMapper.getAccountKey(addr)));
        return mutableRepository.increaseNonce(addr);
    }

    @Override
    public void setNonce(RskAddress addr, BigInteger nonce) {
        readWrittenKeysTracker.addNewWrittenKey(new ByteArrayWrapper(trieKeyMapper.getAccountKey(addr)));
        mutableRepository.setNonce(addr, nonce);
    }

    @Override
    public void saveCode(RskAddress addr, byte[] code) {
        readWrittenKeysTracker.addNewWrittenKey(new ByteArrayWrapper(trieKeyMapper.getCodeKey(addr)));

        if (code != null && code.length != 0 && !isExist(addr)) {
            readWrittenKeysTracker.addNewWrittenKey(new ByteArrayWrapper(trieKeyMapper.getAccountKey(addr))); //TODO(JULI): Chequear si hay algun problema que este en written y read
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
            readWrittenKeysTracker.addNewWrittenKey(new ByteArrayWrapper(trieKeyMapper.getAccountKey(addr)));
            readWrittenKeysTracker.addNewWrittenKey(new ByteArrayWrapper(trieKeyMapper.getAccountStoragePrefixKey(addr)));
        }

        readWrittenKeysTracker.addNewWrittenKey(new ByteArrayWrapper(trieKeyMapper.getAccountStorageKey(addr, key)));
        mutableRepository.addStorageBytes(addr, key, value);
    }

    @Override
    public Coin addBalance(RskAddress addr, Coin value) {
        readWrittenKeysTracker.addNewWrittenKey(new ByteArrayWrapper(trieKeyMapper.getAccountKey(addr)));
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
        readWrittenKeysTracker.addNewWrittenKey(new ByteArrayWrapper(trieKeyMapper.getAccountKey(addr)));
        mutableRepository.updateAccountState(addr, accountState);
    }
}
