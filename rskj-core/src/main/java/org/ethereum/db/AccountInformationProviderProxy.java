package org.ethereum.db;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.AccountInformationProvider;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Iterator;

public class AccountInformationProviderProxy implements AccountInformationProvider {

    Repository repository;

    public AccountInformationProviderProxy(Repository repository) {
        this.repository = repository;
    }
    @Override
    public Coin getBalance(RskAddress addr) {
        return repository.getBalance(addr,false);
    }

    @Nullable
    @Override
    public DataWord getStorageValue(RskAddress addr, DataWord key) {
        return repository.getStorageValue(addr,key,false);
    }

    @Nullable
    @Override
    public byte[] getStorageBytes(RskAddress addr, DataWord key) {
        return repository.getStorageBytes(addr,key,false);
    }

    @Override
    public Iterator<DataWord> getStorageKeys(RskAddress addr) {
        return repository.getStorageKeys(addr);
    }

    @Override
    public int getStorageKeysCount(RskAddress addr) {
        return repository.getStorageKeysCount(addr);
    }

    @Nullable
    @Override
    public byte[] getCode(RskAddress addr) {
        return repository.getCode(addr,false);
    }

    @Override
    public boolean isContract(RskAddress addr) {
        return repository.isContract(addr,false);
    }

    @Override
    public BigInteger getNonce(RskAddress addr) {
        return repository.getNonce(addr,false);
    }
}
