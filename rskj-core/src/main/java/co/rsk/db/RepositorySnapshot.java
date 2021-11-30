/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.db;

import co.rsk.core.RskAddress;
import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.crypto.Keccak256;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;

import java.util.Set;

/**
 * The readonly methods of a Repository.
 * This interface DOES NOT represent an immutable value, since we have startTracking/commit to apply changes.
 */
public interface RepositorySnapshot extends AccountInformationProvider {
    /**
     * @return the storage root of this repository
     */
    byte[] getRoot();

    /**
     * @return set of all the account addresses
     */
    Set<RskAddress> getAccountsKeys();

    /**
     * This method can retrieve the code size without actually retrieving the code
     * in some cases.
     */
    int getCodeLength(RskAddress addr);

    /**
     * This method can retrieve the hash code without actually retrieving the code
     * in some cases.
     * This is the PRE RSKIP169 implementation, which has a bug we need to preserve
     * before the implementation
     * @param addr of the account
     * @return hash of the contract code
     */
    Keccak256 getCodeHashNonStandard(RskAddress addr);

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
     * This method creates a new child repository for change tracking purposes.
     * Changes will be applied to this repository after calling commit on the child. This means that this interface does
     * NOT represent an immutable value.
     */
    Repository startTracking();
}
