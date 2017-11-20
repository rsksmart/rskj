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

import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

/**
 * Created by mario on 23/11/16.
 */
public class AccountDataTest {
    private static final String PRIVATE_KEY = "d19d0cc994e7be8497430f99018143dccf8533ac28a590c47f1a15800693380b";
    private static final String ADDRESS = "db26f581cae26d743691cf327bdf517914fdba0f";


    @Test
    public void create() {
        AccountData data = new AccountData(Hex.decode(ADDRESS), Hex.decode(PRIVATE_KEY));
        Assert.assertNotNull(data);
        Assert.assertNotNull(data.getPrivateKey());
        Assert.assertNotNull(data.getAddress());
    }

    @Test
    public void validateAccount() {
        AccountData data = new AccountData(Hex.decode(ADDRESS), Hex.decode(PRIVATE_KEY));
        Assert.assertTrue(data.validatePrivateKey(Hex.decode(PRIVATE_KEY)));
        Assert.assertFalse(data.validatePrivateKey("Not My Value".getBytes()));
        Assert.assertFalse(data.validatePrivateKey(null));
    }

    @Test
    public void validateAddress() {
        AccountData data = new AccountData(Hex.decode(ADDRESS), Hex.decode(PRIVATE_KEY));
        Assert.assertTrue(data.validateAddress(Hex.decode(ADDRESS)));
        Assert.assertFalse(data.validateAddress("Not My Value".getBytes()));
        Assert.assertFalse(data.validateAddress(null));
    }
}
