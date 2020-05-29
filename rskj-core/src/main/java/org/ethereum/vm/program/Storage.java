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
import co.rsk.crypto.Keccak256;
import co.rsk.trie.Trie;
import co.rsk.core.types.ints.Uint24;

import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.listener.ProgramListener;
import org.ethereum.vm.program.listener.ProgramListenerAware;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.Set;

/*
 * A Storage is a proxy class for Repository. It encapsulates a repository providing tracing services.
 * It is only used by Program.
 * It does not provide any other functionality different from tracing.
 */
public class Storage implements Repository, ProgramListenerAware {

    private final Repository repository;
    private final RskAddress addr;
    private ProgramListener traceListener;

    public Storage(ProgramInvoke programInvoke) {
        this.addr = new RskAddress(programInvoke.getOwnerAddress());
        this.repository = programInvoke.getRepository();
    }

    @Override
    public void setTraceListener(ProgramListener listener) {
        this.traceListener = listener;
    }

    @Override
    public Trie getTrie() {
        return repository.getTrie();
    }

    @Override
    public AccountState createAccount(RskAddress addr) {
        return repository.createAccount(addr);
    }

    @Override
    public void setupContract(RskAddress addr) {
        repository.setupContract(addr);
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
        if (canListenTrace(addr)) {
            traceListener.onStorageClear();
        }
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
    public void setNonce(RskAddress addr, BigInteger nonce) {
        repository.setNonce(addr, nonce);
    }

    @Override
    public BigInteger getNonce(RskAddress addr) {
        return repository.getNonce(addr);
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
    public int getCodeLength(RskAddress addr) {
        return repository.getCodeLength(addr);
    }

    @Override
    public Keccak256 getCodeHash(RskAddress addr) {
        return repository.getCodeHash(addr);
    }

    @Override
    public boolean isContract(RskAddress addr) {
        return repository.isContract(addr);
    }

    @Override
    public void addStorageRow(RskAddress addr, DataWord key, DataWord value) {
        if (canListenTrace(addr)) {
            traceListener.onStoragePut(key, value);
        }
        repository.addStorageRow(addr, key, value);
    }

    @Override
    public void addStorageBytes(RskAddress addr, DataWord key, byte[] value) {
        if (canListenTrace(addr)) {
            traceListener.onStoragePut(key, value);
        }
        repository.addStorageBytes(addr, key, value);
    }

    private boolean canListenTrace(RskAddress addr) {
        return this.addr.equals(addr) && traceListener != null;
    }

    @Override
    public DataWord getStorageValue(RskAddress addr, DataWord key) {
        return repository.getStorageValue(addr, key);
    }

    @Override
    public Iterator<DataWord> getStorageKeys(RskAddress addr) {
        return repository.getStorageKeys(addr);
    }

    @Override
    public int getStorageKeysCount(RskAddress addr) {
        return repository.getStorageKeysCount(addr);
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
    public Repository startTracking() {
        return repository.startTracking();
    }

    @Override
    public void commit() {
        repository.commit();
    }

    @Override
    public void save() {
        repository.save();
    }

    @Override
    public void rollback() {
        repository.rollback();
    }

    @Override
    public byte[] getRoot() {
        return repository.getRoot();
    }

    @Override
    public void updateAccountState(RskAddress addr, AccountState accountState) {
        throw new UnsupportedOperationException();
    }

    /**  #mish add methods for storage rent
    * (i.e. put/get methods for node valuelength and rent last paid timestamps)
    * These are implemented in MutableRepository
    */

    @Override
    public DataWord getAccountNodeKey(RskAddress addr){
        return repository.getAccountNodeKey(addr);
    }

    // for account state node.. both regular accounts as well as contracts
    @Override
    public Uint24 getAccountNodeValueLength(RskAddress addr){
        return repository.getAccountNodeValueLength(addr);
    }
    
    @Override
    public long getAccountNodeLRPTime(RskAddress addr){

        return repository.getAccountNodeLRPTime(addr);
    }

    // update with lastRentPaidTime. This is an extension of updateAccountState(addr, State)
    @Override
    public void updateAccountNodeWithRent(RskAddress addr, final AccountState accountState, final long newlastRentPaidTime){
        repository.updateAccountNodeWithRent(addr, accountState, newlastRentPaidTime);
    }
    
    // For nodes containing contract code
    
    // Start with key as DataWord for HashMaps. Using `getCodeNodexx` to emphasize this is about the node,
    // rather than the code, and to dinsinguish from prior methods
    @Override
    public DataWord getCodeNodeKey(RskAddress addr){
        return repository.getCodeNodeKey(addr);
    }

    // this returns an Uint24, unlike `getCodeLength()` which returns an int. Same otherwise.
    @Override
    public Uint24 getCodeNodeLength(RskAddress addr){
        return getCodeNodeLength(addr);
    }

    @Override
    public long getCodeNodeLRPTime(RskAddress addr){
        return repository.getCodeNodeLRPTime(addr);
    }

    // update node with rent info (and code) 
    @Override
    public void saveCodeWithRent(RskAddress addr, final byte[] code, final long newlastRentPaidTime){
       repository.saveCodeWithRent(addr, code, newlastRentPaidTime); 
    }
    
    // For nodes containing contract storage

    // start with key for stortage root
    @Override
    public DataWord getStorageRootKey(RskAddress addr){
        return repository.getStorageRootKey(addr);
    }

    //storage root node value is always 0x01
    @Override
    public Uint24 getStorageRootValueLength(RskAddress addr){
        return repository.getStorageRootValueLength(addr);
    }

    @Override
    public long getStorageRootLRPTime(RskAddress addr){
        return repository.getStorageRootLRPTime(addr);
    }

    @Override
    public void updateStorageRootWithRent(RskAddress addr, final byte[] value, final long newlastRentPaidTime){
        repository.updateStorageRootWithRent(addr, value, newlastRentPaidTime);
    }

    // methods for individual storage nodes: addr is not enough, also need the key
    @Override
    public Uint24 getStorageValueLength(RskAddress addr, DataWord key){
        return repository.getStorageValueLength(addr, key);
    }

    @Override
    public long getStorageLRPTime(RskAddress addr, DataWord key){
        return repository.getStorageLRPTime(addr, key);
    }

    @Override
    public void updateStorageWithRent(RskAddress addr,  DataWord key, final byte[] value, final long newlastRentPaidTime){
        repository.updateStorageWithRent(addr, key, value, newlastRentPaidTime);
    }

}
