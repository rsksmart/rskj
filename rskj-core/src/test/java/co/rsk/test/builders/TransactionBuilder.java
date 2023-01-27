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

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.core.Account;
import org.ethereum.core.ImmutableTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;

import java.math.BigInteger;
import java.util.Optional;

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
    private Byte chainId = null;
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

    public TransactionBuilder chainId(byte chainId) {
        this.chainId = chainId;
        return this;
    }

    public Transaction build() {
        byte chainId = Optional.ofNullable(this.chainId).orElse(Constants.REGTEST_CHAIN_ID);

        return build(chainId);
    }

    public Transaction build(byte chainId) {
        final String to = receiver != null ? ByteUtil.toHexString(receiver.getAddress().getBytes()) : (receiverAddress != null ? ByteUtil.toHexString(receiverAddress) : null);
        BigInteger nonce = this.nonce;
        BigInteger gasLimit = this.gasLimit;
        BigInteger gasPrice = this.gasPrice;
        byte[] data = this.data;
        BigInteger value = this.value;

        return build(to, nonce, gasLimit, gasPrice, chainId, data, value, sender.getEcKey().getPrivKeyBytes(), this.immutable);
    }

    private Transaction build(String to, BigInteger nonce, BigInteger gasLimit, BigInteger gasPrice, byte chainId, byte[] data, BigInteger value, byte[] privKeyBytes, boolean immutable) {
        Transaction tx = Transaction.builder()
                .destination(to)
                .nonce(nonce)
                .gasLimit(gasLimit)
                .gasPrice(gasPrice)
                .chainId(chainId)
                .data(data)
                .value(value)
                .build();
        tx.sign(privKeyBytes);

        if (immutable) {
            return new ImmutableTransaction(tx.getEncoded());
        }

        return tx;
    }

    /**
     * Generates a random transaction
     */
    public Transaction buildRandomTransaction() {
       return buildRandomTransaction(TransactionBuilder.class.hashCode());
    }

    public Transaction buildRandomTransaction(long seed) {
        int i = TestUtils.generateInt(String.valueOf(seed));
        long k = i * -1L;

        BigInteger randomPositiveVal = i > 0 ?  BigInteger.valueOf(i) : BigInteger.valueOf(k);

        Account receiver = new AccountBuilder().name("account" + randomPositiveVal).build();

        String to = receiver.getAddress().toHexString();
        BigInteger nonce = randomPositiveVal;
        BigInteger gasLimit = randomPositiveVal;
        BigInteger gasPrice = randomPositiveVal;
        byte chainId = Constants.REGTEST_CHAIN_ID; // should be a random valid one
        byte[] data = randomPositiveVal.toByteArray();
        BigInteger value = randomPositiveVal;
        byte[] privateKey = ECKey.fromPrivate(randomPositiveVal).getPrivKeyBytes();

        return build(to, nonce, gasLimit, gasPrice, chainId, data, value, privateKey, false);
    }
}
