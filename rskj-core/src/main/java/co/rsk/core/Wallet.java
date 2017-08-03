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

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.EncryptedData;
import co.rsk.crypto.KeyCrypterScrypt;
import org.ethereum.core.Account;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.SHA3Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.concurrent.GuardedBy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Created by ajlopez on 15/09/2016.
 */
public class Wallet {
    @GuardedBy("accessLock")
    private KeyValueDataSource keyDS = new HashMapDB();

    @GuardedBy("accessLock")
    private Map<ByteArrayWrapper, byte[]> accounts = new HashMap<>();

    private final Object accessLock = new Object();
    private Map<ByteArrayWrapper, Long> unlocksTimeouts = new HashMap<>();

    public void setStore(KeyValueDataSource ds) {
        this.keyDS = ds;
    }

    public List<byte[]> getAccountAddresses() {
        List<byte[]> addresses = new ArrayList<>();
        Set<ByteArrayWrapper> keys = new HashSet<>();

        synchronized(accessLock) {
            for (byte[] address: keyDS.keys())
                keys.add(new ByteArrayWrapper(address));

            keys.addAll(accounts.keySet());

            for (ByteArrayWrapper address: keys)
                addresses.add(address.getData());
        }

        return addresses;
    }

    public byte[] addAccount() {
        Account account = new Account(new ECKey());
        saveAccount(account);
        return account.getAddress();
    }

    public byte[] addAccount(String passphrase) {
        Account account = new Account(new ECKey());
        saveAccount(account, passphrase);
        return account.getAddress();
    }

    public Account getAccount(byte[] address) {
        ByteArrayWrapper key = new ByteArrayWrapper(address);

        synchronized (accessLock) {
            if (!accounts.containsKey(key))
                return null;

            if (unlocksTimeouts.containsKey(key)) {
                long ending = unlocksTimeouts.get(key);
                long time = System.currentTimeMillis();
                if (ending < time) {
                    unlocksTimeouts.remove(key);
                    accounts.remove(key);
                    return null;
                }
            }
            return new Account(ECKey.fromPrivate(accounts.get(key)));
        }
    }

    public Account getAccount(byte[] address, String passphrase) {
        synchronized (accessLock) {
            byte[] encrypted = keyDS.get(address);

            if (encrypted == null)
                return null;

            return new Account(ECKey.fromPrivate(decryptAES(encrypted, passphrase.getBytes(StandardCharsets.UTF_8))));
        }
    }

    public boolean unlockAccount(byte[] address, String passphrase, long duration) {
        long ending = System.currentTimeMillis() + duration;
        boolean unlocked = unlockAccount(address, passphrase);

        if (unlocked) {
            synchronized (accessLock) {
                unlocksTimeouts.put(new ByteArrayWrapper(address), ending);
            }
        }

        return unlocked;
    }

    public boolean unlockAccount(byte[] address, String passphrase) {
        Account account;

        synchronized (accessLock) {
            byte[] encrypted = keyDS.get(address);

            if (encrypted == null)
                return false;

            account = new Account(ECKey.fromPrivate(decryptAES(encrypted, passphrase.getBytes(StandardCharsets.UTF_8))));
        }

        saveAccount(account);

        return true;
    }

    public boolean lockAccount(byte[] address) {
        synchronized (accessLock) {
            ByteArrayWrapper key = new ByteArrayWrapper(address);

            if (!accounts.containsKey(key))
                return false;

            accounts.remove(key);

            return true;
        }
    }

    public byte[] addAccountWithSeed(String seed) {
        Account account = createAccount(ECKey.fromPrivate(SHA3Helper.sha3(seed.getBytes(StandardCharsets.UTF_8))));

        saveAccount(account);

        return account.getAddress();
    }

    public byte[] addAccountWithPrivateKey(byte[] privateKeyBytes) {
        Account account = createAccount(ECKey.fromPrivate(privateKeyBytes));

        saveAccount(account);

        return account.getAddress();
    }

    public byte[] addAccountWithPrivateKey(byte[] privateKeyBytes, String passphrase) {
        Account account = createAccount(ECKey.fromPrivate(privateKeyBytes));

        saveAccount(account, passphrase);

        return account.getAddress();
    }

    private Account createAccount(ECKey key) {
        return new Account(key);
    }

    private void saveAccount(Account account) {
        synchronized (accessLock) {
            accounts.put(new ByteArrayWrapper(account.getAddress()), account.getEcKey().getPrivKeyBytes());
        }
    }

    private void saveAccount(Account account, String passphrase) {
        byte[] address = account.getAddress();
        byte[] privateKeyBytes = account.getEcKey().getPrivKeyBytes();
        byte[] encrypted = encryptAES(privateKeyBytes, passphrase.getBytes(StandardCharsets.UTF_8));

        synchronized (accessLock) {
            keyDS.put(address, encrypted);
        }
    }

    private byte[] decryptAES(byte[] encryptedBytes, byte[] passphrase) {
        RLPList rlpList = (RLPList)RLP.decode2(encryptedBytes).get(0);
        byte[] encryptedPrivateBytes = rlpList.get(0).getRLPData();
        byte[] initializationVector = rlpList.get(1).getRLPData();
        KeyCrypterScrypt keyCrypter = new KeyCrypterScrypt();
        KeyParameter keyParameter = new KeyParameter(Sha256Hash.hash(passphrase));

        EncryptedData data = new EncryptedData(initializationVector, encryptedPrivateBytes);

        return keyCrypter.decrypt(data, keyParameter);
    }

    private byte[] encryptAES(byte[] privateKeyBytes, byte[] passphrase) {
        KeyCrypterScrypt keyCrypter = new KeyCrypterScrypt();
        KeyParameter keyParameter = new KeyParameter(Sha256Hash.hash(passphrase));
        EncryptedData enc = keyCrypter.encrypt(privateKeyBytes, keyParameter);

        byte[] encryptedBytes = RLP.encode(enc.encryptedBytes);
        byte[] initialisationVector = RLP.encode(enc.initialisationVector);

        return RLP.encodeList(encryptedBytes, initialisationVector);
    }
}
