/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.core;

import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;

import java.util.HashSet;
import java.util.Set;

public class TouchedAccountsTracker {
    private final Set<DataWord> touchedAccounts = new HashSet<>();

    /**
     * Invariant-preserving state trie clearing as specified in EIP-161
     */
    public void clearEmptyAccountsFrom(Repository repository) {
        for (DataWord acctAddrDW : touchedAccounts) {
            byte[] acctAddr = acctAddrDW.getData();
            AccountState state = repository.getAccountState(acctAddr);
            if (state != null && state.isEmpty()) {
                repository.delete(acctAddr);
            }
        }
    }

    public void add(DataWord address) {
        touchedAccounts.add(address);
    }

    public void mergeFrom(TouchedAccountsTracker other) {
        touchedAccounts.addAll(other.touchedAccounts);
    }
}
