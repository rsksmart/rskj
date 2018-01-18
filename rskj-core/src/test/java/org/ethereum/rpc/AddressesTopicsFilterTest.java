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

package org.ethereum.rpc;

import co.rsk.test.builders.AccountBuilder;
import org.ethereum.core.Account;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by ajlopez on 18/01/2018.
 */
public class AddressesTopicsFilterTest {
    @Test
    public void matchAddress() {
        Account account = new AccountBuilder().name("account").build();
        byte[] address = account.getAddress().getBytes();

        AddressesTopicsFilter filter = new AddressesTopicsFilter(new byte[][] { address }, null);

        Assert.assertTrue(filter.matchesContractAddress(address));
        Assert.assertFalse(filter.matchesContractAddress(new byte[20]));
    }
}
