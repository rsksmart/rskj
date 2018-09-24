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
import org.ethereum.db.ContractDetails;
import org.ethereum.vm.DataWord;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * @author Roman Mandeleil
 * @since 08.09.2014
 */
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
     * This method returns the NULL if there is no code in account.
     * It may return the empty array for contracts that have installed zero code on construction.
     * (not checked)
     * It CAN return null.
    */
     byte[] getCode(RskAddress addr);

     // This method can retrieve the code size without actually retrieving the code
    // in some cases.
    int getCodeLength(RskAddress addr);


    byte[] getCodeHash(RskAddress addr);

    /**
     * Put a value in storage of an account at a given key
     *
     * @param addr of the account
     * @param key of the data to store
     * @param value is the data to store
     */
    void addStorageRow(RskAddress addr, DataWord key, DataWord value);

    void addStorageBytes(RskAddress addr, DataWord key, byte[] value);

    byte[] getStorageBytes(RskAddress addr, DataWord key);

    byte[] getStorageStateRoot(RskAddress addr);

    boolean contractHasStorage(RskAddress addr);

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
     * Dump the full state of the current repository into a file with JSON format
     * It contains all the contracts/account, their attributes and
     *
     * @param block of the current state
     * @param gasUsed the amount of gas used in the block until that point
     * @param txNumber is the number of the transaction for which the dump has to be made
     * @param txHash is the hash of the given transaction.
     * If null, the block state post coinbase reward is dumped.
     */
    void dumpState(Block block, long gasUsed, int txNumber, byte[] txHash);

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

    /**
     * Return to one of the previous snapshots
     * by moving the root.
     *
     * @param root - new root
     */
    void syncToRoot(byte[] root);

    /**
     * Check to see if the current repository has an open connection to the database
     *
     * @return <tt>true</tt> if connection to database is open
     */
    boolean isClosed();

    /**
     * Close the database
     */
    void close();

    /**
     * Reset
     */
    void reset();

    void updateBatch(Map<RskAddress, AccountState> accountStates);

    void updateBatchDetails(Map<RskAddress, ContractDetails> cacheDetails);

    byte[] getRoot();

    /*void loadAccount(RskAddress addr,
                     Map<RskAddress, AccountState> cacheAccounts,
                     Map<RskAddress, ContractDetails> cacheDetails);
    */
    // This creates a new repository. Does not modify the parent
    Repository getSnapshotTo(byte[] root);

    // This modified in-place the repository
    void setSnapshotTo(byte[] root);


    void updateContractDetails(RskAddress addr, final ContractDetails contractDetails);

    void updateAccountState(RskAddress addr, AccountState accountState);

    void save();
    default void transfer(RskAddress fromAddr, RskAddress toAddr, Coin value) {
        addBalance(fromAddr, value.negate());
        addBalance(toAddr, value);
    }
}
