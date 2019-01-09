/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.vm.program;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.db.ContractDetails;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.invoke.ProgramInvoke;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/*
 * A Storage is a proxy class for Repository. It encapsulates a repository providing tracing services.
 * It is only used by Program.
 * It does not provide any other functionality different from tracing.
 */
public class Storage implements Repository {

    private final Repository repository;

    public Storage(ProgramInvoke programInvoke) {
        this.repository = programInvoke.getRepository();
    }

    @Override
    public AccountState createAccount(RskAddress addr) {
        return repository.createAccount(addr);
    }

    @Override
    public boolean isExist(RskAddress addr) {
        return repository.isExist(addr);
    }

    @Override
    public AccountState getAccountState(RskAddress addr) {
        return repository.getAccountState(addr);
    }

    @Override
    public void delete(RskAddress addr) {
        repository.delete(addr);
    }

    @Override
    public void hibernate(RskAddress addr) {
        repository.hibernate(addr);
    }

    @Override
    public BigInteger increaseNonce(RskAddress addr) {
        return repository.increaseNonce(addr);
    }

    @Override
    public BigInteger getNonce(RskAddress addr) {
        return repository.getNonce(addr);
    }

    @Override
    public ContractDetails getContractDetails(RskAddress addr) {
        return repository.getContractDetails(addr);
    }

    @Override
    public void saveCode(RskAddress addr, byte[] code) {
        repository.saveCode(addr, code);
    }

    @Override
    public byte[] getCode(RskAddress addr) {
        return repository.getCode(addr);
    }

    @Override
    public void addStorageRow(RskAddress addr, DataWord key, DataWord value) {
        repository.addStorageRow(addr, key, value);
    }

    @Override
    public void addStorageBytes(RskAddress addr, DataWord key, byte[] value) {
        repository.addStorageBytes(addr, key, value);
    }

    @Override
    public DataWord getStorageValue(RskAddress addr, DataWord key) {
        return repository.getStorageValue(addr, key);
    }

    @Override
    public byte[] getStorageBytes(RskAddress addr, DataWord key) {
        return repository.getStorageBytes(addr, key);
    }

    @Override
    public Coin getBalance(RskAddress addr) {
        return repository.getBalance(addr);
    }

    @Override
    public Coin addBalance(RskAddress addr, Coin value) {
        return repository.addBalance(addr, value);
    }

    @Override
    public Set<RskAddress> getAccountsKeys() {
        return repository.getAccountsKeys();
    }

    @Override
    public void dumpState(Block block, long gasUsed, int txNumber, byte[] txHash) {
        repository.dumpState(block, gasUsed, txNumber, txHash);
    }

    @Override
    public Repository startTracking() {
        return repository.startTracking();
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public void flushNoReconnect() {
        throw new UnsupportedOperationException();
    }


    @Override
    public void commit() {
        repository.commit();
    }

    @Override
    public void rollback() {
        repository.rollback();
    }

    @Override
    public void syncToRoot(byte[] root) {
        repository.syncToRoot(root);
    }

    @Override
    public void updateBatch(Map<RskAddress, AccountState> accountStates, Map<RskAddress, ContractDetails> contractDetails) {
        repository.updateBatch(accountStates, contractDetails);
    }

    @Override
    public byte[] getRoot() {
        return repository.getRoot();
    }

    @Override
    public void loadAccount(RskAddress addr, Map<RskAddress, AccountState> cacheAccounts, Map<RskAddress, ContractDetails> cacheDetails) {
        repository.loadAccount(addr, cacheAccounts, cacheDetails);
    }

    @Override
    public Repository getSnapshotTo(byte[] root) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateContractDetails(RskAddress addr, ContractDetails contractDetails) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAccountState(RskAddress addr, AccountState accountState) {
        throw new UnsupportedOperationException();
    }


}
