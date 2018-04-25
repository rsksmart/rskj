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
import org.ethereum.crypto.Keccak256Helper;
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

        byte[] calculatedAddress = ECKey.fromPrivate(Keccak256Helper.keccak256("seed".getBytes())).getAddress();

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

        byte[] address = wallet.addAccount("passphrase").getBytes();

        Assert.assertNotNull(address);

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertFalse(addresses.isEmpty());
        Assert.assertEquals(1, addresses.size());

        byte[] addr = addresses.get(0);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address, addr);

        Account account = wallet.getAccount(new RskAddress(address), "passphrase");

        Assert.assertNotNull(account);
        Assert.assertArrayEquals(address, account.getAddress().getBytes());
    }

    @Test
    public void addAccountWithPassphraseAndWithSeed() {
        Wallet wallet = WalletFactory.createWallet();

        RskAddress addr1 = wallet.addAccount("passphrase");
        Assert.assertNotNull(addr1);

        byte[] address2 = wallet.addAccountWithSeed("seed");
        Assert.assertNotNull(address2);

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertFalse(addresses.isEmpty());
        Assert.assertEquals(2, addresses.size());

        byte[] addr = addresses.get(0);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address2, addr);

        addr = addresses.get(1);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(addr1.getBytes(), addr);

        Account account = wallet.getAccount(addr1, "passphrase");

        Assert.assertNotNull(account);
        Assert.assertEquals(addr1, account.getAddress());
    }

    @Test
    public void addAccountWithPassphraseAndTwoAccountsWithSeed() {
        Wallet wallet = WalletFactory.createWallet();

        RskAddress addr1 = wallet.addAccount("passphrase");
        Assert.assertNotNull(addr1);

        byte[] address2 = wallet.addAccountWithSeed("seed");
        Assert.assertNotNull(address2);
        byte[] address3 = wallet.addAccountWithSeed("seed2");
        Assert.assertNotNull(address3);

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertFalse(addresses.isEmpty());
        Assert.assertEquals(3, addresses.size());

        byte[] addr = addresses.get(0);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address2, addr);

        addr = addresses.get(1);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address3, addr);

        addr = addresses.get(2);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(addr1.getBytes(), addr);

        Account account = wallet.getAccount(addr1, "passphrase");

        Assert.assertNotNull(account);
        Assert.assertEquals(addr1, account.getAddress());
    }

    @Test
    public void addAndUnlockAccountWithPassphraseAndTwoAccountsWithSeed() {
        Wallet wallet = WalletFactory.createWallet();

        RskAddress addr1 = wallet.addAccount("passphrase");
        Assert.assertNotNull(addr1);

        byte[] address2 = wallet.addAccountWithSeed("seed");
        Assert.assertNotNull(address2);
        byte[] address3 = wallet.addAccountWithSeed("seed2");
        Assert.assertNotNull(address3);

        wallet.unlockAccount(addr1, "passphrase", 10000);

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertFalse(addresses.isEmpty());
        Assert.assertEquals(3, addresses.size());

        byte[] addr = addresses.get(0);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address2, addr);

        addr = addresses.get(1);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address3, addr);

        addr = addresses.get(2);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(addr1.getBytes(), addr);

        Account account = wallet.getAccount(addr1, "passphrase");

        Assert.assertNotNull(account);
        Assert.assertEquals(addr1, account.getAddress());
    }

    @Test
    public void unlockAccountWithPassphrase() {
        Wallet wallet = WalletFactory.createWallet();

        byte[] address = wallet.addAccount("passphrase").getBytes();

        Assert.assertNotNull(address);

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertFalse(addresses.isEmpty());
        Assert.assertEquals(1, addresses.size());

        byte[] addr = addresses.get(0);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address, addr);

        Account account0 = wallet.getAccount(new RskAddress(address));

        Assert.assertNull(account0);

        Assert.assertTrue(wallet.unlockAccount(new RskAddress(address), "passphrase"));

        Account account = wallet.getAccount(new RskAddress(address));

        Assert.assertNotNull(account);
        Assert.assertArrayEquals(address, account.getAddress().getBytes());
    }

    @Test
    public void unlockNonexistentAccount() {
        Wallet wallet = WalletFactory.createWallet();

        RskAddress addr = new RskAddress("0x0000000000000000000000000000000000000023");
        Assert.assertFalse(wallet.unlockAccount(addr, "passphrase"));
    }

    @Test
    public void lockAccount() {
        Wallet wallet = WalletFactory.createWallet();

        byte[] address = wallet.addAccount("passphrase").getBytes();

        Assert.assertNotNull(address);

        Assert.assertTrue(wallet.unlockAccount(new RskAddress(address), "passphrase"));

        Account account = wallet.getAccount(new RskAddress(address));

        Assert.assertNotNull(account);
        Assert.assertArrayEquals(address, account.getAddress().getBytes());

        Assert.assertTrue(wallet.lockAccount(new RskAddress(address)));

        Account account2 = wallet.getAccount(new RskAddress(address));

        Assert.assertNull(account2);
    }

    @Test
    public void lockNonexistentAccount() {
        Wallet wallet = WalletFactory.createWallet();

        RskAddress addr = new RskAddress("0x0000000000000000000000000000000000000023");
        Assert.assertFalse(wallet.lockAccount(addr));
    }

    @Test
    public void addAccountWithRandomPrivateKey() {
        Wallet wallet = WalletFactory.createWallet();

        byte[] address = wallet.addAccount().getBytes();

        Assert.assertNotNull(address);

        Account account = wallet.getAccount(new RskAddress(address));

        Assert.assertNotNull(account);

        Assert.assertArrayEquals(address, account.getAddress().getBytes());
    }

    @Test
    public void getUnknownAccount() {
        Wallet wallet = WalletFactory.createWallet();

        RskAddress addr = new RskAddress("0x0000000000000000000000000000000000000023");
        Account account = wallet.getAccount(addr);

        Assert.assertNull(account);
    }

    @Test
    public void addAccountWithPrivateKey() {
        Wallet wallet = WalletFactory.createWallet();
        byte[] privateKeyBytes = Keccak256Helper.keccak256("seed".getBytes());

        byte[] address = wallet.addAccountWithPrivateKey(privateKeyBytes);

        Assert.assertNotNull(address);

        byte[] calculatedAddress = ECKey.fromPrivate(Keccak256Helper.keccak256("seed".getBytes())).getAddress();

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
