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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.GuardedBy;

import org.bouncycastle.crypto.params.KeyParameter;
import org.ethereum.core.Account;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.EncryptedData;
import co.rsk.crypto.KeyCrypterAes;
import co.rsk.util.HexUtils;

public class Wallet {
    @GuardedBy("accessLock")
    private final KeyValueDataSource<?> keyDS;

    @GuardedBy("accessLock")
    private final Map<RskAddress, byte[]> accounts = new HashMap<>();

    @GuardedBy("accessLock")
    private final List<RskAddress> initialAccounts = new ArrayList<>();

    private final Object accessLock = new Object();
    private final Map<RskAddress, Long> unlocksTimeouts = new HashMap<>();

    public Wallet(KeyValueDataSource keyDS) {
        this.keyDS = keyDS;
    }

    public List<byte[]> getAccountAddresses() {
        List<byte[]> addresses = new ArrayList<>();
        Set<RskAddress> keys = new HashSet<>();

        synchronized(accessLock) {
            for (RskAddress addr: this.initialAccounts) {
                addresses.add(addr.getBytes());
            }

            for (ByteArrayWrapper addressWrapped: keyDS.keys()) {
                keys.add(new RskAddress(addressWrapped.getData()));
            }

            keys.addAll(accounts.keySet());
            keys.removeAll(this.initialAccounts);

            for (RskAddress addr: keys) {
                addresses.add(addr.getBytes());
            }
        }

        return addresses;
    }

    public String[] getAccountAddressesAsHex() {
        return getAccountAddresses().stream()
                .map(HexUtils::toJsonHex)
                .toArray(String[]::new);
    }

    public RskAddress addAccount() {
        Account account = new Account(new ECKey());
        saveAccount(account);
        return account.getAddress();
    }

    public RskAddress addAccount(String passphrase) {
        Account account = new Account(new ECKey());
        saveAccount(account, passphrase);
        return account.getAddress();
    }

    public RskAddress addAccount(Account account) {
        saveAccount(account);
        return account.getAddress();
    }

    public Account getAccount(RskAddress addr) {
        synchronized (accessLock) {
            if (!accounts.containsKey(addr)) {
                return null;
            }

            if (unlocksTimeouts.containsKey(addr)) {
                long ending = unlocksTimeouts.get(addr);
                long time = System.currentTimeMillis();
                if (ending < time) {
                    unlocksTimeouts.remove(addr);
                    accounts.remove(addr);
                    return null;
                }
            }
            return new Account(ECKey.fromPrivate(accounts.get(addr)));
        }
    }

    public Account getAccount(RskAddress addr, String passphrase) {
        synchronized (accessLock) {
            byte[] encrypted = keyDS.get(addr.getBytes());

            if (encrypted == null) {
                return null;
            }

            return new Account(ECKey.fromPrivate(decryptAES(encrypted, passphrase.getBytes(StandardCharsets.UTF_8))));
        }
    }

    public boolean unlockAccount(RskAddress addr, String passphrase, long duration) {
        long ending = System.currentTimeMillis() + duration;
        boolean unlocked = unlockAccount(addr, passphrase);

        if (unlocked) {
            synchronized (accessLock) {
                unlocksTimeouts.put(addr, ending);
            }
        }

        return unlocked;
    }

    public boolean unlockAccount(RskAddress addr, String passphrase) {
        Account account;

        synchronized (accessLock) {
            byte[] encrypted = keyDS.get(addr.getBytes());

            if (encrypted == null) {
                return false;
            }

            account = new Account(ECKey.fromPrivate(decryptAES(encrypted, passphrase.getBytes(StandardCharsets.UTF_8))));
        }

        saveAccount(account);

        return true;
    }

    public boolean lockAccount(RskAddress addr) {
        synchronized (accessLock) {
            if (!accounts.containsKey(addr)) {
                return false;
            }

            accounts.remove(addr);
            return true;
        }
    }

    public byte[] addAccountWithSeed(String seed) {
        return addAccountWithPrivateKey(Keccak256Helper.keccak256(seed.getBytes(StandardCharsets.UTF_8)));
    }

    public byte[] addAccountWithPrivateKey(byte[] privateKeyBytes) {
        Account account = new Account(ECKey.fromPrivate(privateKeyBytes));
        synchronized (accessLock) {
            RskAddress addr = addAccount(account);
            if (!this.initialAccounts.contains(addr)) {
                this.initialAccounts.add(addr);
            }
            return addr.getBytes();
        }
    }

    public RskAddress addAccountWithPrivateKey(byte[] privateKeyBytes, String passphrase) {
        Account account = new Account(ECKey.fromPrivate(privateKeyBytes));

        saveAccount(account, passphrase);

        return account.getAddress();
    }

    public void close() {
        synchronized (accessLock) {
            keyDS.close();
        }
    }

    private void saveAccount(Account account) {
        synchronized (accessLock) {
            accounts.put(account.getAddress(), account.getEcKey().getPrivKeyBytes());
        }
    }

    private void saveAccount(Account account, String passphrase) {
        byte[] address = account.getAddress().getBytes();
        byte[] privateKeyBytes = account.getEcKey().getPrivKeyBytes();
        byte[] encrypted = encryptAES(privateKeyBytes, passphrase.getBytes(StandardCharsets.UTF_8));

        synchronized (accessLock) {
            keyDS.put(address, encrypted);
        }
    }

    private byte[] decryptAES(byte[] encryptedBytes, byte[] passphrase) {
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(encryptedBytes);
            ObjectInputStream byteStream = new ObjectInputStream(in);
            KeyCrypterAes keyCrypter = new KeyCrypterAes();
            KeyParameter keyParameter = new KeyParameter(Sha256Hash.hash(passphrase));

            ArrayList<byte[]> bytes = (ArrayList<byte[]>) byteStream.readObject();
            EncryptedData data = new EncryptedData(bytes.get(1), bytes.get(0));

            return keyCrypter.decrypt(data, keyParameter);
        } catch (IOException | ClassNotFoundException e) {
            //There are lines of code that should never be executed, this is one of those
            throw new IllegalStateException(e);
        }
    }

    private byte[] encryptAES(byte[] privateKeyBytes, byte[] passphrase) {
        KeyCrypterAes keyCrypter = new KeyCrypterAes();
        KeyParameter keyParameter = new KeyParameter(Sha256Hash.hash(passphrase));
        EncryptedData enc = keyCrypter.encrypt(privateKeyBytes, keyParameter);

        try {
            ByteArrayOutputStream encryptedResult = new ByteArrayOutputStream();
            ObjectOutputStream byteStream = new ObjectOutputStream(encryptedResult);

            ArrayList<byte[]> bytes = new ArrayList<>();
            bytes.add(enc.encryptedBytes);
            bytes.add(enc.initialisationVector);
            byteStream.writeObject(bytes);

            return encryptedResult.toByteArray();
        } catch (IOException e) {
            //How is this even possible ???
            throw new IllegalStateException(e);
        }
    }
}
