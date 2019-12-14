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

package co.rsk.db;

import co.rsk.core.RskAddress;
import org.ethereum.core.AccountState;
import org.ethereum.vm.DataWord;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by ajlopez on 09/12/2019.
 */
public class RepositoryTrack extends AbstractRepository {
    private final AbstractRepository parent;

    public RepositoryTrack(AbstractRepository parent) {
        this.parent = parent;
    }

    @Override
    public AccountState retrieveAccountState(RskAddress address) {
        AccountState accountState = this.parent.getAccountState(address);

        if (accountState != null) {
            return accountState.clone();
        }

        return null;
    }

    @Nullable
    @Override
    public byte[] retrieveStorageBytes(RskAddress address, DataWord key) {
        return this.parent.getStorageBytes(address, key);
    }

    @Override
    public byte[] retrieveCode(RskAddress address) {
        if (!this.parent.isExist(address)) {
            return null;
        }

        return this.parent.getCode(address);
    }

    @Override
    public boolean retrieveIsContract(RskAddress address) {
        return this.parent.isContract(address);
    }

    @Override
    public void commitAccountState(RskAddress address, AccountState accountState) {
        if (accountState == null) {
            this.parent.delete(address);
        }
        else {
            this.parent.updateAccountState(address, accountState);
        }
    }

    @Override
    public void commitContract(RskAddress address) {
        this.parent.setupContract(address);
    }

    @Override
    public void commitStorage(RskAddress address, DataWord key, byte[] value) {
        this.parent.addStorageBytes(address, key, value);
    }

    @Override
    public void commitCode(RskAddress address, byte[] code) {
        this.parent.saveCode(address, code);
    }

    @Override
    public Set<RskAddress> retrieveAccountsKeys() {
        return this.parent.retrieveAccountsKeys();
    }

    @Override
    public Iterator<DataWord> retrieveStorageKeys(RskAddress address) { return this.parent.getStorageKeys(address); }
}
