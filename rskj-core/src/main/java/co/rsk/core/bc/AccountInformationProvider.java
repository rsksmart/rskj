/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.core.bc;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.vm.DataWord;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;

public interface AccountInformationProvider {

    /**
     * Retrieve balance of an account
     *
     * @param addr of the account
     * @return balance of the account as a <code>BigInteger</code> value
     */
    Coin getBalance(RskAddress addr);

    /**
     * Retrieve storage value from an account for a given key
     *
     * @param addr of the account
     * @param key associated with this value
     * @return data in the form of a <code>DataWord</code>
     */
    @Nullable
    DataWord getStorageValue(RskAddress addr, DataWord key);

    /**
     *
     * @param addr of the account
     * @param key associated with this value
     * @return raw data
     */
    @Nullable
    byte[] getStorageBytes(RskAddress addr, DataWord key);

    /**
     *
     * @param addr of the account
     * @return the keys for that addr
     */
    Iterator<DataWord> getStorageKeys(RskAddress addr);

    /**
     *
     * @param addr of the account
     * @return the count of keys for that addr
     */
    int getStorageKeysCount(RskAddress addr);

    /**
     * Retrieve the code associated with an account
     *
     * This method returns null if there is no code at the address.
     * It may return the empty array for contracts that have installed zero code on construction.
     * (not checked)
     *
     * @param addr of the account
     * @return code in byte-array format
     */
    @Nullable
    byte[] getCode(RskAddress addr);

    /**
     * @param addr an address account
     * @return true if the addr identifies a contract
     */
    boolean isContract(RskAddress addr);

    /**
     * Get current nonce of a given account
     *
     * @param addr of the account
     * @return value of the nonce
     */
    BigInteger getNonce(RskAddress addr);

    /**
     * Retrieve a Keccak256(of the current storage root) of a given account
     *
     * @param addr an address
     * @return Keccak256(storageRoot)
     * */
    Keccak256 getStorageHash(RskAddress addr);

    /**
     * Retrieves an account proof for a given address
     * An account proof represents all the nodes starting from the state root following the path by the given the address.
     * Each node is serialized and RLP encoded.
     *
     * @param addr an address
     * @return a list of account proofs for a given address
     * */
    List<byte[]> getAccountProof(RskAddress addr);

    /**
     * Retrieves an account proof for a given address
     * An account proof represents all the nodes starting from the state root following the path by the given the address.
     * Each node is serialized and RLP encoded.
     *
     * @param addr an address
     * @return a list of storage proofs for a given address and storage key
     * */
    List<byte[]> getStorageProof(RskAddress addr, DataWord storageKey);
}
