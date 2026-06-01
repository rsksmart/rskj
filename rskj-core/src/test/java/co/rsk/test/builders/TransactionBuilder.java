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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.core.*;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Random;

import static org.mockito.Mockito.any;

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
    private Byte transactionType = null;
    private Byte rskSubtype = null;
    private BigInteger maxFeePerGas = null;
    private BigInteger maxPriorityFeePerGas = null;

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

    public TransactionBuilder transactionType(byte transactionType) {
        this.transactionType = transactionType;
        return this;
    }

    public TransactionBuilder rskSubtype(byte rskSubtype) {
        this.rskSubtype = rskSubtype;
        return this;
    }

    public TransactionBuilder maxFeePerGas(BigInteger maxFeePerGas) {
        this.maxFeePerGas = maxFeePerGas;
        return this;
    }

    public TransactionBuilder maxPriorityFeePerGas(BigInteger maxPriorityFeePerGas) {
        this.maxPriorityFeePerGas = maxPriorityFeePerGas;
        return this;
    }

    public Transaction build() {
        byte chainId = Optional.ofNullable(this.chainId).orElse(Constants.REGTEST_CHAIN_ID);

        return build(chainId);
    }

    public Transaction build(byte chainId) {
        RskAddress toAddress = resolveReceiveAddress();
        BigInteger nonce = this.nonce;
        BigInteger gasLimit = this.gasLimit;
        BigInteger gasPrice = this.gasPrice;
        byte[] data = this.data;
        BigInteger value = this.value;

        return createSignedTransaction(toAddress, nonce, gasLimit, gasPrice, chainId, data, value,
                sender.getEcKey().getPrivKeyBytes(), this.immutable);
    }

    private RskAddress resolveReceiveAddress() {
        if (receiver != null) {
            return receiver.getAddress();
        }
        if (receiverAddress != null && receiverAddress.length > 0) {
            return new RskAddress(receiverAddress);
        }
        return RskAddress.nullAddress();
    }

    private Transaction createSignedTransaction(RskAddress to, BigInteger nonce, BigInteger gasLimit, BigInteger gasPrice,
                                                byte chainId, byte[] data, BigInteger value, byte[] privKeyBytes,
                                                boolean immutable) {
        Transaction tx;
        if (this.rskSubtype != null) {
            tx = buildViaLegacyBuilder(to, nonce, gasLimit, gasPrice, chainId, data, value, true);
        } else if (this.transactionType != null) {
            TransactionType txType = TransactionType.fromByte(this.transactionType);
            if (txType == null || txType.isLegacy()) {
                throw new IllegalArgumentException(String.format(
                        "transaction type not supported: 0x%02x",
                        this.transactionType & 0xFF));
            }
            tx = switch (txType) {
                case TYPE_1 -> buildType1FromCallArguments(to, nonce, gasLimit, gasPrice, chainId, data, value);
                case TYPE_2 -> buildType2FromCallArguments(to, nonce, gasLimit, chainId, data, value);
                case TYPE_4 -> throw new IllegalArgumentException(
                        "Type 4 transactions must be built via Transaction.fromRaw or Transaction.fromCallArguments");
                default -> throw new IllegalArgumentException(
                        "transaction type not supported: 0x" + Integer.toHexString(this.transactionType & 0xFF));
            };
        } else {
            tx = buildViaLegacyBuilder(to, nonce, gasLimit, gasPrice, chainId, data, value, false);
        }

        tx.sign(privKeyBytes);

        if (immutable) {
            return new ImmutableTransaction(tx.getEncoded());
        }

        return tx;
    }

    private Transaction buildViaLegacyBuilder(RskAddress to, BigInteger nonce, BigInteger gasLimit, BigInteger gasPrice,
                                               byte chainId, byte[] data, BigInteger value, boolean rskNamespace) {
        org.ethereum.core.TransactionBuilder txBuilder = org.ethereum.core.Transaction.builder()
                .receiveAddress(to)
                .nonce(nonce)
                .gasLimit(gasLimit)
                .gasPrice(gasPrice)
                .chainId(chainId)
                .data(data)
                .value(value);

        if (this.maxFeePerGas != null) {
            txBuilder.maxFeePerGas(new Coin(this.maxFeePerGas));
        }
        if (this.maxPriorityFeePerGas != null) {
            txBuilder.maxPriorityFeePerGas(new Coin(this.maxPriorityFeePerGas));
        }
        if (rskNamespace) {
            txBuilder.type(TransactionType.TYPE_2, this.rskSubtype);
        }
        return txBuilder.build();
    }

    private Transaction buildType1FromCallArguments(RskAddress to, BigInteger nonce, BigInteger gasLimit, BigInteger gasPrice,
                                                   byte chainId, byte[] data, BigInteger value) {
        return Rskip546TestSupport.unsignedType1(
                chainId,
                to,
                new Coin(gasPrice),
                new Coin(value),
                gasLimit,
                nonce.toByteArray(),
                data != null ? data : new byte[0],
                Rskip546TestSupport.EMPTY_ACCESS_LIST);
    }

    private Transaction buildType2FromCallArguments(RskAddress to, BigInteger nonce, BigInteger gasLimit, byte chainId,
                                                    byte[] data, BigInteger value) {
        BigInteger maxP = this.maxPriorityFeePerGas != null ? this.maxPriorityFeePerGas : this.gasPrice;
        BigInteger maxF = this.maxFeePerGas != null ? this.maxFeePerGas : this.gasPrice;
        return Rskip546TestSupport.unsignedType2(
                chainId,
                to,
                new Coin(maxP),
                new Coin(maxF),
                new Coin(value),
                gasLimit,
                nonce.toByteArray(),
                data != null ? data : new byte[0],
                Rskip546TestSupport.EMPTY_ACCESS_LIST);
    }

    /**
     * Generates a random transaction
     */
    public Transaction createRandomTransaction() {
       return createRandomTransaction(TransactionBuilder.class.hashCode());
    }

    public Transaction createRandomTransaction(long seed) {
        int i = TestUtils.generateInt(String.valueOf(seed));
        long k = i * -1L;

        BigInteger randomPositiveVal = i > 0 ?  BigInteger.valueOf(i) : BigInteger.valueOf(k);

        Account receiver = new AccountBuilder().name("account" + randomPositiveVal).build();

        BigInteger nonce = randomPositiveVal;
        BigInteger gasLimit = randomPositiveVal;
        BigInteger gasPrice = randomPositiveVal;
        byte chainId = Constants.REGTEST_CHAIN_ID; // should be a random valid one
        byte[] data = randomPositiveVal.toByteArray();
        BigInteger value = randomPositiveVal;
        byte[] privateKey = ECKey.fromPrivate(randomPositiveVal).getPrivKeyBytes();

        return createSignedTransaction(receiver.getAddress(), nonce, gasLimit, gasPrice, chainId, data, value,
                privateKey, false);
    }

    public static Transaction createMockTransaction(
            RskSystemProperties config,
            long value,
            long gaslimit,
            long gasprice,
            long nonce,
            long data,
            long sender) {
        Random r = new Random(sender);
        Transaction transaction = Mockito.mock(Transaction.class);
        Mockito.when(transaction.getValue()).thenReturn(new Coin(BigInteger.valueOf(value)));
        Mockito.when(transaction.getGasLimit()).thenReturn(BigInteger.valueOf(gaslimit).toByteArray());
        Mockito.when(transaction.getGasLimitAsInteger()).thenReturn(BigInteger.valueOf(gaslimit));
        Mockito.when(transaction.getGasPrice()).thenReturn(Coin.valueOf(gasprice));
        Mockito.when(transaction.getNonce()).thenReturn(BigInteger.valueOf(nonce).toByteArray());
        Mockito.when(transaction.getNonceAsInteger()).thenReturn(BigInteger.valueOf(nonce));

        byte[] returnSenderBytes = new byte[20];
        r.nextBytes(returnSenderBytes);
        RskAddress returnSender = new RskAddress(returnSenderBytes);

        byte[] returnReceiveAddressBytes = new byte[20];
        r.nextBytes(returnReceiveAddressBytes);
        RskAddress returnReceiveAddress = new RskAddress(returnReceiveAddressBytes);

        byte[] randomBytes = TestUtils.generateBytes(TransactionBuilder.class, "txHash", 32);
        Mockito.when(transaction.getSender(any(SignatureCache.class))).thenReturn(returnSender);
        Mockito.when(transaction.getHash()).thenReturn(new Keccak256(randomBytes));
        Mockito.when(transaction.acceptTransactionSignature(config.getNetworkConstants().getChainId())).thenReturn(Boolean.TRUE);
        Mockito.when(transaction.getReceiveAddress()).thenReturn(returnReceiveAddress);
        ArrayList<Byte> bytes = new ArrayList<>();
        long amount = 21000;
        if (data != 0) {
            data /= 2;
            for (long i = 0; i < data / 4; i++) {
                bytes.add((byte) 0);
                amount += 4;
            }
            for (long i = 0; i < data / 68; i++) {
                bytes.add((byte) 1);
                amount += 68;
            }
        }
        int n = bytes.size();
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = bytes.get(i);
        }
        Mockito.when(transaction.getData()).thenReturn(b);
        Mockito.when(transaction.transactionCost(any(), any(), any())).thenReturn(amount);
        Mockito.when(transaction.getTypePrefix()).thenReturn(TransactionTypePrefix.legacy());

        return transaction;
    }
}
