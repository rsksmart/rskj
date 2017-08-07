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
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Created by ajlopez on 15/09/2016.
 */
public class WalletTest {
    private static Random random = new Random();

    @Test
    public void getEmptyAccountList() {
        Wallet wallet = new Wallet();

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertTrue(addresses.isEmpty());
    }

    @Test
    public void addAccountWithSeed() {
        Wallet wallet = new Wallet();

        byte[] address = wallet.addAccountWithSeed("seed", null);

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
        Wallet wallet = new Wallet();

        byte[] address = wallet.addAccount("passphrase");

        Assert.assertNotNull(address);

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertFalse(addresses.isEmpty());
        Assert.assertEquals(1, addresses.size());

        byte[] addr = addresses.get(0);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address, addr);

        Account account = wallet.getAccountUsingPassphrase(address, "passphrase");

        Assert.assertNotNull(account);
        Assert.assertArrayEquals(address, account.getAddress());
    }

    @Test
    public void unlockAccountWithPassphrase() {
        Wallet wallet = new Wallet();

        byte[] address = wallet.addAccount("passphrase");

        Assert.assertNotNull(address);

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertFalse(addresses.isEmpty());
        Assert.assertEquals(1, addresses.size());

        byte[] addr = addresses.get(0);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address, addr);

        Account account0 = wallet.getAccount(address, null);

        Assert.assertNull(account0);

        Assert.assertTrue(wallet.unlockAccount(address, "passphrase", null));

        Account account = wallet.getAccount(address, null);

        Assert.assertNotNull(account);
        Assert.assertArrayEquals(address, account.getAddress());
    }

    @Test
    public void unlockAccountWithPassphraseAndDuration() throws InterruptedException {
        Wallet wallet = new Wallet();

        byte[] address = wallet.addAccount("passphrase");

        Assert.assertNotNull(address);

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertFalse(addresses.isEmpty());
        Assert.assertEquals(1, addresses.size());

        byte[] addr = addresses.get(0);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address, addr);

        Account account0 = wallet.getAccount(address, null);

        Assert.assertNull(account0);

        Assert.assertTrue(wallet.unlockAccount(address, "passphrase", 500, null));

        Account account = wallet.getAccount(address, null);

        Assert.assertNotNull(account);
        Assert.assertArrayEquals(address, account.getAddress());

        TimeUnit.SECONDS.sleep(1);

        wallet.removeAccountsWithUnlockDurationExpired();
        Assert.assertNull(wallet.getAccount(address, null));
    }

    @Test
    public void unlockAccountWithPassphraseDurationAndSecret() throws InterruptedException {
        byte[] secret = generateSecret();

        Wallet wallet = new Wallet();

        byte[] address = wallet.addAccount("passphrase");

        Assert.assertNotNull(address);

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertFalse(addresses.isEmpty());
        Assert.assertEquals(1, addresses.size());

        byte[] addr = addresses.get(0);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address, addr);

        Account account0 = wallet.getAccount(address, secret);

        Assert.assertNull(account0);

        Assert.assertTrue(wallet.unlockAccount(address, "passphrase", 500, secret));

        Assert.assertNull(wallet.getAccount(address, null));
        Assert.assertNull(wallet.getAccount(address, generateSecret()));

        Account account = wallet.getAccount(address, secret);

        Assert.assertNotNull(account);
        Assert.assertArrayEquals(address, account.getAddress());

        TimeUnit.SECONDS.sleep(1);

        wallet.removeAccountsWithUnlockDurationExpired();
        Assert.assertNull(wallet.getAccount(address, null));
    }

    @Test
    public void lockAccountByTimeout() throws InterruptedException {
        Wallet wallet = new Wallet();

        byte[] address = wallet.addAccount("passphrase");

        Assert.assertNotNull(address);

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertFalse(addresses.isEmpty());
        Assert.assertEquals(1, addresses.size());

        byte[] addr = addresses.get(0);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address, addr);

        Account account0 = wallet.getAccount(address, null);

        Assert.assertNull(account0);

        Assert.assertTrue(wallet.unlockAccount(address, "passphrase", 500, null));

        wallet.start(1);

        TimeUnit.MILLISECONDS.sleep(1500);
        Assert.assertNull(wallet.getAccount(address, null));

        wallet.stop();
    }


    @Test
    public void lockAccountByTimeoutUsingSecret() throws InterruptedException {
        Wallet wallet = new Wallet();

        byte[] address = wallet.addAccount("passphrase");

        Assert.assertNotNull(address);

        List<byte[]> addresses = wallet.getAccountAddresses();

        Assert.assertNotNull(addresses);
        Assert.assertFalse(addresses.isEmpty());
        Assert.assertEquals(1, addresses.size());

        byte[] addr = addresses.get(0);

        Assert.assertNotNull(addr);
        Assert.assertArrayEquals(address, addr);

        Account account0 = wallet.getAccount(address, null);

        Assert.assertNull(account0);

        byte[] secret = generateSecret();
        Assert.assertTrue(wallet.unlockAccount(address, "passphrase", 500, secret));

        wallet.start(1);

        TimeUnit.MILLISECONDS.sleep(1500);
        Assert.assertNull(wallet.getAccount(address, null));
        Assert.assertNull(wallet.getAccount(address, secret));

        wallet.stop();
    }

    @Test
    public void unlockNonexistentAccount() {
        Wallet wallet = new Wallet();

        Assert.assertFalse(wallet.unlockAccount(new byte[] { 0x01, 0x02, 0x03 }, "passphrase", null));
    }

    @Test
    public void unlockNonexistentAccountUsingSecret() {
        Wallet wallet = new Wallet();

        Assert.assertFalse(wallet.unlockAccount(new byte[] { 0x01, 0x02, 0x03 }, "passphrase", generateSecret()));
    }

    @Test
    public void lockAccount() {
        Wallet wallet = new Wallet();

        byte[] address = wallet.addAccount("passphrase");

        Assert.assertNotNull(address);

        Assert.assertTrue(wallet.unlockAccount(address, "passphrase", null));

        Account account = wallet.getAccount(address, null);

        Assert.assertNotNull(account);
        Assert.assertArrayEquals(address, account.getAddress());

        Assert.assertTrue(wallet.lockAccount(address, null));

        Account account2 = wallet.getAccount(address, null);

        Assert.assertNull(account2);
    }

    @Test
    public void lockAccountUsingSecret() {
        byte[] secret = generateSecret();
        Wallet wallet = new Wallet();

        byte[] address = wallet.addAccount("passphrase");

        Assert.assertNotNull(address);

        Assert.assertTrue(wallet.unlockAccount(address, "passphrase", secret));

        Account account = wallet.getAccount(address, secret);

        Assert.assertNotNull(account);
        Assert.assertArrayEquals(address, account.getAddress());

        Assert.assertFalse(wallet.lockAccount(address, null));
        Assert.assertFalse(wallet.lockAccount(address, generateSecret()));
        Assert.assertTrue(wallet.lockAccount(address, secret));

        Account account2 = wallet.getAccount(address, secret);

        Assert.assertNull(account2);
    }

    @Test
    public void lockNonexistentAccount() {
        Wallet wallet = new Wallet();

        Assert.assertFalse(wallet.lockAccount(new byte[] { 0x01, 0x02, 0x03 }, null));
    }

    @Test
    public void lockNonexistentAccountUsingSecret() {
        Wallet wallet = new Wallet();

        Assert.assertFalse(wallet.lockAccount(new byte[] { 0x01, 0x02, 0x03 }, generateSecret()));
    }

    @Test
    public void getUnknownAccount() {
        Wallet wallet = new Wallet();

        Account account = wallet.getAccount(new byte[] { 0x01, 0x02, 0x03 }, null);

        Assert.assertNull(account);
    }

    @Test
    public void getKnownAndUnlockedAccount() {
        byte[] secret = generateSecret();
        Wallet wallet = new Wallet();

        byte[] address = wallet.addAccount("passphrase");

        wallet.unlockAccount(address, "passphrase", secret);

        Assert.assertNull(wallet.getAccount(address, null));
        Assert.assertNull(wallet.getAccount(address, generateSecret()));
        Assert.assertNotNull(wallet.getAccount(address, secret));
    }

    @Test
    public void getKnownButNotNotUnlockedAccount() {
        Wallet wallet = new Wallet();

        byte[] address = wallet.addAccount("passphrase");

        Assert.assertNull(wallet.getAccount(address, null));
        Assert.assertNull(wallet.getAccount(address, generateSecret()));
    }

    @Test
    public void addAccountWithPrivateKey() {
        Wallet wallet = new Wallet();
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

    private static byte[] generateSecret() {
        byte[] bytes = new byte[32];

        random.nextBytes(bytes);

        return bytes;
    }
}
