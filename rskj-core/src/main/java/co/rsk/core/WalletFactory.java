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

import co.rsk.crypto.KeyCrypterScrypt;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.util.RLP;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;

/**
 * Created by mario on 06/12/16.
 */
public class WalletFactory {
    private static final String oldWalletName = "wallet";
    private static final String walletName = "walletrlp";
    private static final Object creationLock = new Object();

    private WalletFactory() {

    }

    public static boolean existsPersistentWallet() {
        return existsPersistentWallet(walletName);
    }

    public static boolean existsPersistentWallet(String storeName) {
        LevelDbDataSource ds = new LevelDbDataSource(storeName);

        return ds.exists();
    }

    public static Wallet createPersistentWallet() {
        synchronized (creationLock) {
            if (existsPersistentWallet(oldWalletName) && !existsPersistentWallet(walletName))
                convertWallet(oldWalletName, walletName);

            return createPersistentWallet(walletName);
        }
    }

    @VisibleForTesting
    public static void convertWallet(String originalWalletName, String newWalletName) {
        LevelDbDataSource originalDs = new LevelDbDataSource(originalWalletName);
        originalDs.init();

        LevelDbDataSource newDs = new LevelDbDataSource(newWalletName);
        newDs.init();

        for (byte[] key : originalDs.keys()) {
            byte[] originalBytes = originalDs.get(key);
            newDs.put(key, convertBytes(originalBytes));
        }

        originalDs.close();
        newDs.close();
    }

    @VisibleForTesting
    public static Wallet createPersistentWallet(String storeName) {
        Wallet wallet = new Wallet();
        KeyValueDataSource ds = new LevelDbDataSource(storeName);
        ds.init();
        wallet.setStore(ds);
        return wallet;
    }

    public static Wallet createWallet() {
        return new Wallet();
    }

    public static byte[] convertBytes(byte[] originalBytes) {
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(originalBytes);
            ObjectInputStream byteStream = new ObjectInputStream(in);

            ArrayList<byte[]> bytes = (ArrayList<byte[]>) byteStream.readObject();

            byte[] encryptedBytes = RLP.encode(bytes.get(0));
            byte[] initialisationVector = RLP.encode(bytes.get(1));

            return RLP.encodeList(encryptedBytes, initialisationVector);
        } catch (IOException | ClassNotFoundException e) {
            //There are lines of code that should never be executed, this is one of those
            throw new IllegalStateException(e);
        }
    }
}
