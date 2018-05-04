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

package co.rsk.test.builders;

import co.rsk.config.TestSystemProperties;
import org.ethereum.core.Account;
import org.ethereum.core.ImmutableTransaction;
import org.ethereum.core.Transaction;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

/**
 * Created by ajlopez on 8/6/2016.
 */
public class TransactionBuilder {
    private Account sender;
    private Account receiver;
    private byte[] receiverAddress;
    private byte[] data;
    private BigInteger value = BigInteger.ZERO;
    private BigInteger gasPrice = BigInteger.ONE;
    private BigInteger gasLimit = BigInteger.valueOf(21000);
    private BigInteger nonce = BigInteger.ZERO;
    private boolean immutable;

    public TransactionBuilder sender(Account sender) {
        this.sender = sender;
        return this;
    }

    public TransactionBuilder receiver(Account receiver) {
        this.receiver = receiver;
        return this;
    }

    public TransactionBuilder receiverAddress(byte[] receiverAddress) {
        this.receiverAddress = receiverAddress;
        return this;
    }

    public TransactionBuilder data(String data) {
        this.data = Hex.decode(data);
        return this;
    }

    public TransactionBuilder data(byte[] data) {
        this.data = data;
        return this;
    }

    public TransactionBuilder immutable() {
        this.immutable = true;
        return this;
    }

    public TransactionBuilder value(BigInteger value) {
        this.value = value;
        return this;
    }

    public TransactionBuilder nonce(long nonce) {
        this.nonce = BigInteger.valueOf(nonce);
        return this;
    }

    public TransactionBuilder gasPrice(BigInteger gasPrice) {
        this.gasPrice = gasPrice;
        return this;
    }

    public TransactionBuilder gasLimit(BigInteger gasLimit) {
        this.gasLimit = gasLimit;
        return this;
    }

    public Transaction build() {
        Transaction tx = Transaction.create(
                new TestSystemProperties(), receiver != null ? Hex.toHexString(receiver.getAddress().getBytes()) : (receiverAddress != null ? Hex.toHexString(receiverAddress) : null),
                value, nonce, gasPrice, gasLimit, data);
        tx.sign(sender.getEcKey().getPrivKeyBytes());

        if (this.immutable) {
            return new ImmutableTransaction(tx.getEncoded());
        }

        return tx;
    }
}
