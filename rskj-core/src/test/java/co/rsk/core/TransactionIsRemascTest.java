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

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransactionIsRemascTest {
    int txPosition = 6;
    int txsSize = 7;
    RskAddress destination = PrecompiledContracts.REMASC_ADDR;
    byte[] data = null;
    Coin value = Coin.ZERO;
    byte[] gasPrice = Hex.decode("00");
    byte[] gasLimit = Hex.decode("00");

    private Transaction buildTx(
        RskAddress destination,
        byte[] data,
        Coin value,
        byte[] gasPrice,
        byte[] gasLimit
    ) {
        return Transaction.builder()
            .destination(destination)
            .data(data)
            .value(value)
            .gasPrice(gasPrice)
            .gasLimit(gasLimit)
            .build();
    }

    private void assertIsRemascTransaction(
        RskAddress destination,
        byte[] data,
        Coin value,
        byte[] gasPrice,
        byte[] gasLimit,
        int txPosition,
        int txsSize
    ) {
        Transaction tx = buildTx(destination, data, value, gasPrice, gasLimit);

        assertTrue(tx.isRemascTransaction(txPosition, txsSize));
    }

    private void assertIsNotRemascTransaction(
        Transaction tx,
        int txPosition,
        int txsSize
    ) {
        assertFalse(tx.isRemascTransaction(txPosition, txsSize));
    }

    private void assertIsNotRemascTransaction(
        RskAddress destination,
        byte[] data,
        Coin value,
        byte[] gasPrice,
        byte[] gasLimit,
        int txPosition,
        int txsSize
    ) {
        Transaction tx = buildTx(destination, data, value, gasPrice, gasLimit);

        assertIsNotRemascTransaction(tx, txPosition, txsSize);
    }

    @Test
    public void validRemascTransactionNullData() {
        assertIsRemascTransaction(destination, data, value, gasPrice, gasLimit, txPosition, txsSize);
    }

    @Test
    public void validRemascTransactionEmptyData() {
        byte[] data = {};
        assertIsRemascTransaction(destination, data, value, gasPrice, gasLimit, txPosition, txsSize);
    }

    @Test
    public void notRemascTransactionNotLastTx() {
        int txPosition = 3;
        assertIsNotRemascTransaction(destination, data, value, gasPrice, gasLimit, txPosition, txsSize);
    }

    @Test
    public void notRemascTransactionNotEmptyData() {
        byte[] data = { 1, 2, 3, 4 };
        assertIsNotRemascTransaction(destination, data, value, gasPrice, gasLimit, txPosition, txsSize);
    }

    @Test
    public void notRemascTransactionNotNullSig() {
        byte[] senderPrivateKey = HashUtil.keccak256("cow".getBytes());
        Transaction tx = buildTx(destination, data, value, gasPrice, gasLimit);
        tx.sign(senderPrivateKey);

        assertIsNotRemascTransaction(tx, txPosition, txsSize);
    }

    @Test
    public void notRemascTransactionReceiverIsNotRemasc() {
        byte[] privateKey = HashUtil.keccak256("cat".getBytes());
        ECKey ecKey = ECKey.fromPrivate(privateKey);
        RskAddress destination =  RLP.parseRskAddress(ByteUtil.cloneBytes(ecKey.getAddress()));

        assertIsNotRemascTransaction(destination, data, value, gasPrice, gasLimit, txPosition, txsSize);
    }


    @Test
    public void notRemascTransactionValueIsNotZero() {
        Coin value = Coin.valueOf(10);
        assertIsNotRemascTransaction(destination, data, value, gasPrice, gasLimit, txPosition, txsSize);
    }


    @Test
    public void notRemascTransactionGasPriceIsNotZero() {
        byte[] gasPrice = { 10 };
        assertIsNotRemascTransaction(destination, data, value, gasPrice, gasLimit, txPosition, txsSize);
    }


    @Test
    public void notRemascTransactionGasLimitIsNotZero() {
        byte[] gasLimit = { 10 };
        assertIsNotRemascTransaction(destination, data, value, gasPrice, gasLimit, txPosition, txsSize);
    }
}

