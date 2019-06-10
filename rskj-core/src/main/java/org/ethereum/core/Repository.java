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

package org.ethereum.core;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import org.ethereum.vm.DataWord;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Set;

public interface Repository extends AccountInformationProvider {

    MutableTrie getMutableTrie();

    /**
     * Create a new account in the database
     *
     * @param addr of the contract
     * @return newly created account state
     *
     * This method creates an account, but is DOES NOT create a contract.
     * To create a contract, internally the account node is extended with a root node
     * for storage. To avoid creating the root node for storage each time a storage cell
     * is added, we pre-create the storage node when we know the account will become a
     * contract. This is done in setupContract().
     * Note that we can't use the length or existence of the code node for this,
     * because a contract's code can be empty!
     *
     */
    AccountState createAccount(RskAddress addr);

    void setupContract(RskAddress addr);

    /**
     * @param addr - account to check
     * @return - true if account exist,
     *           false otherwise
     */
    boolean isExist(RskAddress addr);

    /**
     * Retrieve an account
     *
     * @param addr of the account
     * @return account state as stored in the database
     */
    AccountState getAccountState(RskAddress addr);

    /**
     * Deletes the account. This is recursive: all storage keys are deleted
     *
     * @param addr of the account
     */
    void delete(RskAddress addr);

    /**
     * Hibernates the account
     *
     * @param addr of the account
     */
    void hibernate(RskAddress addr);

    /**
     * Increase the account nonce of the given account by one
     *
     * @param addr of the account
     * @return new value of the nonce
     */
    BigInteger increaseNonce(RskAddress addr);

    void setNonce(RskAddress addr,BigInteger  nonce);

    /**
     * Store code associated with an account
     *
     * @param addr for the account
     * @param code that will be associated with this account
     */
    void saveCode(RskAddress addr, byte[] code);

    /**
     * get the code associated with an account
     *
     * This method returns null if there is no code at the address.
     * It may return the empty array for contracts that have installed zero code on construction.
     * (not checked)
    */
    @Override
    @Nullable
    byte[] getCode(RskAddress addr);

     // This method can retrieve the code size without actually retrieving the code
    // in some cases.
    int getCodeLength(RskAddress addr);

    /**
     * Put a value in storage of an account at a given key
     *
     * @param addr of the account
     * @param key of the data to store
     * @param value is the data to store
     */
    void addStorageRow(RskAddress addr, DataWord key, DataWord value);

    void addStorageBytes(RskAddress addr, DataWord key, byte[] value);

    @Override
    byte[] getStorageBytes(RskAddress addr, DataWord key);

    /**
     * Add value to the balance of an account
     *
     * @param addr of the account
     * @param value to be added
     * @return new balance of the account
     */
    Coin addBalance(RskAddress addr, Coin value);

    /**
     * @return Returns set of all the account addresses
     */
    Set<RskAddress> getAccountsKeys();

    /**
     * Save a snapshot and start tracking future changes
     *
     * @return the tracker repository
     */
    Repository startTracking();

    void flush();

    void flushNoReconnect();

    /**
     * Store all the temporary changes made
     * to the repository in the actual database
     */
    void commit();

    /**
     * Undo all the changes made so far
     * to a snapshot of the repository
     */
    void rollback();

    void save();

    /**
     * Return to one of the previous snapshots
     * by moving the root.
     *
     * @param root - new root
     */
    void syncToRoot(byte[] root);

    void syncTo(Trie root);

    byte[] getRoot();

    /**
     * @deprecated a repository responsibility isn't getting snapshots to other repositories
     * @see co.rsk.db.RepositoryLocator
     */
    Repository getSnapshotTo(byte[] root);

    void updateAccountState(RskAddress addr, AccountState accountState);

    default void transfer(RskAddress fromAddr, RskAddress toAddr, Coin value) {
        addBalance(fromAddr, value.negate());
        addBalance(toAddr, value);
    }
}
