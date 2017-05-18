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

package org.ethereum.core.genesis;

import org.ethereum.core.AccountState;
import org.ethereum.db.ContractDetails;

public class InitialAddressState {
    private AccountState accountState;
    private ContractDetails contractDetails;

    public InitialAddressState(AccountState accountState, ContractDetails contractDetails) {
        this.accountState = accountState;
        this.contractDetails = contractDetails;
    }

    public AccountState getAccountState() {
        return accountState;
    }

    public ContractDetails getContractDetails() {
        return contractDetails;
    }
}
