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

import org.ethereum.core.Account;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.SHA3Helper;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created by ajlopez on 15/09/2016.
 */
public class WalletTest {
    @Test
    public void getEmptyAccountList() {
        Wallet wallet = WalletFactory.createWallet();

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertTrue(addresses.isEmpty());
    }

    @Test
    public void addAccountWithSeed() {
        Wallet wallet = WalletFactory.createWallet();

        byte[] address = wallet.addAccountWithSeed("seed");

        Assert.assertNotNull(address);

        byte[] calculatedAddress = ECKey.fromPrivate(SHA3Helper.sha3("seed".getBytes())).getAddress();

        Assert.assertArrayEquals(calculatedAddress, address);

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertFalse(addresses.isEmpty());
        Assert.assertEquals(1, addresses.size());

        byte[] addr = addresses.get(0);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address, addr);
    }

    @Test
    public void addAccountWithPassphrase() {
        Wallet wallet = WalletFactory.createWallet();

        byte[] address = wallet.addAccount("passphrase");

        Assert.assertNotNull(address);

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertFalse(addresses.isEmpty());
        Assert.assertEquals(1, addresses.size());

        byte[] addr = addresses.get(0);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address, addr);

        Account account = wallet.getAccount(address, "passphrase");

        Assert.assertNotNull(account);
        Assert.assertArrayEquals(address, account.getAddress());
    }

    @Test
    public void unlockAccountWithPassphrase() {
        Wallet wallet = WalletFactory.createWallet();

        byte[] address = wallet.addAccount("passphrase");

        Assert.assertNotNull(address);

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertFalse(addresses.isEmpty());
        Assert.assertEquals(1, addresses.size());

        byte[] addr = addresses.get(0);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address, addr);

        Account account0 = wallet.getAccount(address);

        Assert.assertNull(account0);

        Assert.assertTrue(wallet.unlockAccount(address, "passphrase"));

        Account account = wallet.getAccount(address);

        Assert.assertNotNull(account);
        Assert.assertArrayEquals(address, account.getAddress());
    }

    @Test
    public void unlockNonexistentAccount() {
        Wallet wallet = WalletFactory.createWallet();

        Assert.assertFalse(wallet.unlockAccount(new byte[] { 0x01, 0x02, 0x03 }, "passphrase"));
    }

    @Test
    public void lockAccount() {
        Wallet wallet = WalletFactory.createWallet();

        byte[] address = wallet.addAccount("passphrase");

        Assert.assertNotNull(address);

        Assert.assertTrue(wallet.unlockAccount(address, "passphrase"));

        Account account = wallet.getAccount(address);

        Assert.assertNotNull(account);
        Assert.assertArrayEquals(address, account.getAddress());

        Assert.assertTrue(wallet.lockAccount(address));

        Account account2 = wallet.getAccount(address);

        Assert.assertNull(account2);
    }

    @Test
    public void lockNonexistentAccount() {
        Wallet wallet = WalletFactory.createWallet();

        Assert.assertFalse(wallet.lockAccount(new byte[] { 0x01, 0x02, 0x03 }));
    }

    @Test
    public void addAccountWithRandomPrivateKey() {
        Wallet wallet = WalletFactory.createWallet();

        byte[] address = wallet.addAccount();

        Assert.assertNotNull(address);

        Account account = wallet.getAccount(address);

        Assert.assertNotNull(account);

        Assert.assertArrayEquals(address, account.getAddress());
    }

    @Test
    public void getUnknownAccount() {
        Wallet wallet = WalletFactory.createWallet();

        Account account = wallet.getAccount(new byte[] { 0x01, 0x02, 0x03 });

        Assert.assertNull(account);
    }

    @Test
    public void addAccountWithPrivateKey() {
        Wallet wallet = WalletFactory.createWallet();
        byte[] privateKeyBytes = SHA3Helper.sha3("seed".getBytes());

        byte[] address = wallet.addAccountWithPrivateKey(privateKeyBytes);

        Assert.assertNotNull(address);

        byte[] calculatedAddress = ECKey.fromPrivate(SHA3Helper.sha3("seed".getBytes())).getAddress();

        Assert.assertArrayEquals(calculatedAddress, address);

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertFalse(addresses.isEmpty());
        Assert.assertEquals(1, addresses.size());

        byte[] addr = addresses.get(0);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address, addr);
    }
}
