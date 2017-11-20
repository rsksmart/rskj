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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;

/**
 * Created by mario on 23/11/16.
 */
public class AccountFileLoaderTest {

    private static final String ACCOUNT_FILE = "src/test/resources/accounts.json";
    private static final String NOT_A_JSON_ACCOUNT_FILE = "src/test/resources/log4j.properties";
    private static final String PRIVATE_KEY_1 = "d19d0cc994e7be8497430f99018143dccf8533ac28a590c47f1a15800693380b";
    private static final String PRIVATE_KEY_2 = "d19d0cc994e7be8497430f99018143dccf8533ac28a590c47f1a15800693381b";
    private static final String PRIVATE_KEY_3 = "d19d0cc994e7be8497430f99018143dccf8533ac28a590c47f1a15800693382b";

    private static final String ADDRESS_1 = "db26f581cae26d743691cf327bdf517914fdba0f";
    private static final String ADDRESS_2 = "db26f581cae26d743691cf327bdf517914fdba1f";
    private static final String ADDRESS_3 = "db26f581cae26d743691cf327bdf517914fdba2f";


    @Test
    public void load() {
        System.out.println("Working Directory = " +
                System.getProperty("user.dir"));
        Path path = Paths.get(ACCOUNT_FILE);
        AccountFileLoader fileLoader = new AccountFileLoader(path);
        Assert.assertNotNull(fileLoader);

        ConcurrentMap<String, AccountData> accounts = new ConcurrentHashMap<>();
        Assert.assertTrue(accounts.size() == 0);

        accounts = fileLoader.load();
        Assert.assertTrue(accounts.size() == 3);

        AccountData data = accounts.get("0");
        Assert.assertNotNull(data);
        Hex.toHexString(data.getPrivateKey());
        Assert.assertTrue(data.validatePrivateKey(Hex.decode(PRIVATE_KEY_1)));
        Assert.assertTrue(data.validateAddress(stringHexToByteArray(ADDRESS_1)));

        data = accounts.get("1");
        Assert.assertNotNull(data);
        Assert.assertTrue(data.validatePrivateKey(stringHexToByteArray(PRIVATE_KEY_2)));
        Assert.assertTrue(data.validateAddress(stringHexToByteArray(ADDRESS_2)));

        data = accounts.get("2");
        Assert.assertNotNull(data);
        Assert.assertTrue(data.validatePrivateKey(stringHexToByteArray(PRIVATE_KEY_3)));
        Assert.assertTrue(data.validateAddress(stringHexToByteArray(ADDRESS_3)));

    }

    @Test
    public void loadFileNotFound() {
        AccountFileLoader fileLoader = new AccountFileLoader(Paths.get("Not a file!"));
        ConcurrentMap map = fileLoader.load();
        Assert.assertTrue(map.size() == 0);
    }

    @Test
    public void loadNotAJsonFile() {
        AccountFileLoader fileLoader = new AccountFileLoader(Paths.get(NOT_A_JSON_ACCOUNT_FILE));
        ConcurrentMap map = fileLoader.load();
        Assert.assertTrue(map.size() == 0);
    }
}
