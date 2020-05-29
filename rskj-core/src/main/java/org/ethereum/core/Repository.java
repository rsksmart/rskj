/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General License for more details.
 *
 * You should have received a copy of the GNU Lesser General License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.core;


import co.rsk.core.types.ints.Uint24;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import co.rsk.trie.Trie;
import org.ethereum.vm.DataWord;

import java.math.BigInteger;

public interface Repository extends RepositorySnapshot {
    Trie getTrie();

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
     * Put a value in storage of an account at a given key
     *
     * @param addr of the account
     * @param key of the data to store
     * @param value is the data to store
     */
    void addStorageRow(RskAddress addr, DataWord key, DataWord value);

    void addStorageBytes(RskAddress addr, DataWord key, byte[] value);

    /**
     * Add value to the balance of an account
     *
     * @param addr of the account
     * @param value to be added
     * @return new balance of the account
     */
    Coin addBalance(RskAddress addr, Coin value);

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

    void updateAccountState(RskAddress addr, AccountState accountState);

    default void transfer(RskAddress fromAddr, RskAddress toAddr, Coin value) {
        addBalance(fromAddr, value.negate());
        addBalance(toAddr, value);
    }

    /* #mish extend repository for methods related to storage rent
    * (i.e. put/get methods for node (accountState, code, storage) value length and last rent paid timestamp
    * These are implemented in MutableRepository
    */

    DataWord getAccountNodeKey(RskAddress addr);

    // for account state node.. both regular accounts as well as contracts
    Uint24 getAccountNodeValueLength(RskAddress addr);
    
    long getAccountNodeLRPTime(RskAddress addr);

    // update with lastRentPaidTime. This is an extension of updateAccountState(addr, State)
    void updateAccountNodeWithRent(RskAddress addr, final AccountState accountState, final long newlastRentPaidTime);
    
    // For nodes containing contract code
    
    // Start with key as DataWord for HashMaps. Using `getCodeNodexx` to emphasize this is about the node,
    // rather than the code, and to dinsinguish from prior methods
    DataWord getCodeNodeKey(RskAddress addr);

    // this returns an Uint24, unlike `getCodeLength()` which returns an int. Same otherwise.
    Uint24 getCodeNodeLength(RskAddress addr) ;

    long getCodeNodeLRPTime(RskAddress addr);

    // update node with rent info (and code) 
    void saveCodeWithRent(RskAddress addr, final byte[] code, final long newlastRentPaidTime);
    
    // For nodes containing contract storage

    // start with key for stortage root
    DataWord getStorageRootKey(RskAddress addr);

    //storage root node value is always 0x01
    Uint24 getStorageRootValueLength(RskAddress addr);

    long getStorageRootLRPTime(RskAddress addr);

    void updateStorageRootWithRent(RskAddress addr, final byte[] value, final long newlastRentPaidTime);

    // methods for individual storage nodes: addr is not enough, also need the key
    Uint24 getStorageValueLength(RskAddress addr, DataWord key);

    long getStorageLRPTime(RskAddress addr, DataWord key);

    void updateStorageWithRent(RskAddress addr,  DataWord key, final byte[] value, final long newlastRentPaidTime);

}
