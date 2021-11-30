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

package co.rsk.net.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.core.Account;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.Utils;

/**
 * Created by ajlopez on 7/22/2016.
 */
public class TransactionUtils {
	
    public static List<Transaction> getTransactions(int n) {
        List<Transaction> txs = new ArrayList<>();

        for (long k = 0; k < n; k++)
            txs.add(createTransaction(getPrivateKeyBytes(k), getAddress(), BigInteger.valueOf(k + 1), BigInteger.valueOf(k)));

        return txs;
    }

    public static String getAddress() {
        Account targetAcc = new Account(new ECKey(Utils.getRandom()));
        return ByteUtil.toHexString(targetAcc.getAddress().getBytes());
    }

    public static byte[] getPrivateKeyBytes() {
    	return getPrivateKeyBytes(0L);
    }
    
    public static byte[] getPrivateKeyBytes(long salt) {
    	String seed = "this is a seed" + Long.toString(salt);
        return HashUtil.keccak256(seed.getBytes());
    }

    public static Transaction createTransaction(byte[] privateKey, String toAddress, BigInteger value, BigInteger nonce) {
        return createTransaction(privateKey, toAddress, value, nonce, BigInteger.ONE, BigInteger.valueOf(21000));
    }

    public static Transaction createTransaction(byte[] privateKey, String toAddress, BigInteger value, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit) {
        Transaction tx = Transaction
                .builder()
                .nonce(nonce)
                .gasPrice(gasPrice)
                .gasLimit(gasLimit)
                .destination(toAddress != null ? Hex.decode(toAddress) : null)
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(value)
                .build();
        tx.sign(privateKey);
        return tx;
    }

    public static Transaction createTransaction() {
        return getTransactions(1).get(0);
    }
}
