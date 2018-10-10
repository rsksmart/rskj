/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.core;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.panic.PanicProcessor;
import co.rsk.peg.BridgeUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.Constants;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.ECKey.ECDSASignature;
import org.ethereum.crypto.ECKey.MissingPrivateKeyException;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.vm.GasCost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.security.SignatureException;
import java.util.List;
import java.util.Objects;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * A transaction (formally, T) is a single cryptographically
 * signed instruction sent by an actor external to Ethereum.
 * An external actor can be a person (via a mobile device or desktop computer)
 * or could be from a piece of automated software running on a server.
 * There are two types of transactions: those which result in message calls
 * and those which result in the creation of new contracts.
 */
public class Transaction {
    private static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};

    private static final Logger logger = LoggerFactory.getLogger(Transaction.class);
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    private static final BigInteger SECP256K1N_HALF = Constants.getSECP256K1N().divide(BigInteger.valueOf(2));

    public static final int DATAWORD_LENGTH = 32;

    /* whether this is a local call transaction */
    private boolean isLocalCall;

    /* SHA3 hash of the RLP encoded transaction */
    private byte[] hash;

    /* a counter used to make sure each transaction can only be processed once */
    private byte[] nonce;

    private Coin value;

    /* the address of the destination account
     * In creation transaction the receive address is - 0 */
    private RskAddress receiveAddress;

    private Coin gasPrice;

    /* the amount of "gas" to allow for the computation.
     * Gas is the fuel of the computational engine.
     * Every computational step taken and every byte added
     * to the state or transaction list consumes some gas. */
    private byte[] gasLimit;

    /* An unlimited size byte array specifying
     * input [data] of the message call or
     * Initialization code for a new contract */
    private byte[] data;

    /**
     * Since EIP-155, we could encode chainId in V
     */
    private static final byte CHAIN_ID_INC = 35;
    private static final byte LOWER_REAL_V = 27;
    private byte chainId = 0;

    /* the elliptic curve signature
     * (including public key recovery bits) */
    private ECDSASignature signature;

    protected RskAddress sender;

    /* Tx in encoded form */
    protected byte[] rlpEncoded;
    private byte[] rlpRaw;
    /* Indicates if this transaction has been parsed
     * from the RLP-encoded data */
    private boolean parsed = false;

    protected Transaction(byte[] rawData) {
        this.rlpEncoded = rawData;
        rlpParse();
        // clear it so we always reencode the received data
        this.rlpEncoded = null;
    }

    /* creation contract tx
     * [ nonce, gasPrice, gasLimit, "", endowment, init, signature(v, r, s) ]
     * or simple send tx
     * [ nonce, gasPrice, gasLimit, receiveAddress, value, data, signature(v, r, s) ]
     */
    public Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data, (byte) 0);
    }

    public Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data, byte[] r, byte[] s, byte v) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data, (byte) 0);

        this.signature = ECDSASignature.fromComponents(r, s, v);
    }

    public Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] valueRaw, byte[] data,
                       byte chainId) {
        this.nonce = ByteUtil.cloneBytes(nonce);
        this.gasPrice = RLP.parseCoinNonNullZero(ByteUtil.cloneBytes(gasPriceRaw));
        this.gasLimit = ByteUtil.cloneBytes(gasLimit);
        this.receiveAddress = RLP.parseRskAddress(ByteUtil.cloneBytes(receiveAddress));
        this.value = RLP.parseCoinNullZero(ByteUtil.cloneBytes(valueRaw));
        this.data = ByteUtil.cloneBytes(data);
        this.chainId = chainId;
        this.isLocalCall = false;

        parsed = true;
    }

    public Transaction toImmutableTransaction() {
        return new ImmutableTransaction(this.getEncoded());
    }

    private byte extractChainIdFromV(byte v) {
        if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1)) {
            return 0;
        }
        return (byte) (((0x00FF & v) - CHAIN_ID_INC) / 2);
    }

    private byte getRealV(byte v) {
        if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1)) {
            return v;
        }
        byte realV = LOWER_REAL_V;
        int inc = 0;
        if ((int) v % 2 == 0) {
            inc = 1;
        }
        return (byte) (realV + inc);
    }

    // There was a method called NEW_getTransactionCost that implemented this alternative solution:
    // "return (this.isContractCreation() ? GasCost.TRANSACTION_CREATE_CONTRACT : GasCost.TRANSACTION)
    //         + zeroVals * GasCost.TX_ZERO_DATA + nonZeroes * GasCost.TX_NO_ZERO_DATA;"
    public long transactionCost(Block block, BlockchainNetConfig netConfig){
        if (!parsed) {
            rlpParse();
        }

		// Federators txs to the bridge are free during system setup
        if (BridgeUtils.isFreeBridgeTx(this, block.getNumber(), netConfig)) {
            return 0;
        }

        long nonZeroes = this.nonZeroDataBytes();
        long zeroVals  = ArrayUtils.getLength(this.getData()) - nonZeroes;

        return (this.isContractCreation() ? GasCost.TRANSACTION_CREATE_CONTRACT : GasCost.TRANSACTION) + zeroVals * GasCost.TX_ZERO_DATA + nonZeroes * GasCost.TX_NO_ZERO_DATA;
    }

    public void verify() {
        rlpParse();
        validate();
    }

    private void validate() {
        if (getNonce().length > DATAWORD_LENGTH) {
            throw new RuntimeException("Nonce is not valid");
        }
        if (receiveAddress != null && receiveAddress.getBytes().length != 0 && receiveAddress.getBytes().length != Constants.getMaxAddressByteLength()) {
            throw new RuntimeException("Receive address is not valid");
        }
        if (gasLimit.length > DATAWORD_LENGTH) {
            throw new RuntimeException("Gas Limit is not valid");
        }
        if (gasPrice != null && gasPrice.getBytes().length > DATAWORD_LENGTH) {
            throw new RuntimeException("Gas Price is not valid");
        }
        if (value.getBytes().length > DATAWORD_LENGTH) {
            throw new RuntimeException("Value is not valid");
        }
        if (getSignature() != null) {
            if (BigIntegers.asUnsignedByteArray(signature.r).length > DATAWORD_LENGTH) {
                throw new RuntimeException("Signature R is not valid");
            }
            if (BigIntegers.asUnsignedByteArray(signature.s).length > DATAWORD_LENGTH) {
                throw new RuntimeException("Signature S is not valid");
            }
            if (getSender().getBytes() != null && getSender().getBytes().length != Constants.getMaxAddressByteLength()) {
                throw new RuntimeException("Sender is not valid");
            }
        }
    }

    public void rlpParse() {
        List<RLPElement> transaction = RLP.decodeList(rlpEncoded);
        if (transaction.size() != 9) {
            throw new IllegalArgumentException("A transaction must have exactly 9 elements");
        }

        this.nonce = transaction.get(0).getRLPData();
        this.gasPrice = RLP.parseCoinNonNullZero(transaction.get(1).getRLPData());
        this.gasLimit = transaction.get(2).getRLPData();
        this.receiveAddress = RLP.parseRskAddress(transaction.get(3).getRLPData());
        this.value = RLP.parseCoinNullZero(transaction.get(4).getRLPData());
        this.data = transaction.get(5).getRLPData();
        // only parse signature in case tx is signed
        if (transaction.get(6).getRLPData() != null) {
            byte[] vData =  transaction.get(6).getRLPData();
            if (vData.length != 1 ) {
                throw new TransactionException("Signature V is invalid");
            }
            byte v = vData[0];
            this.chainId = extractChainIdFromV(v);
            byte[] r = transaction.get(7).getRLPData();
            byte[] s = transaction.get(8).getRLPData();
            this.signature = ECDSASignature.fromComponents(r, s, getRealV(v));
        } else {
            logger.trace("RLP encoded tx is not signed!");
        }
        this.parsed = true;
        this.hash = getHash().getBytes();
    }

    public boolean isParsed() {
        return parsed;
    }

    public Keccak256 getHash() {
        if (!parsed) {
            rlpParse();
        }

        byte[] plainMsg = this.getEncoded();
        return new Keccak256(HashUtil.keccak256(plainMsg));
    }

    public Keccak256 getRawHash() {
        if (!parsed) {
            rlpParse();
        }

        byte[] plainMsg = this.getEncodedRaw();
        return new Keccak256(HashUtil.keccak256(plainMsg));
    }

    public byte[] getNonce() {
        if (!parsed) {
            rlpParse();
        }

        return nullToZeroArray(nonce);
    }

    public Coin getValue() {
        if (!parsed) {
            rlpParse();
        }

        return value;
    }

    public RskAddress getReceiveAddress() {
        if (!parsed) {
            rlpParse();
        }

        return receiveAddress;
    }

    public Coin getGasPrice() {
        if (!parsed) {
            rlpParse();
        }

        // some blocks have zero encoded as null, but if we altered the internal field then re-encoding the value would
        // give a different value than the original.
        if (gasPrice == null) {
            return Coin.ZERO;
        }

        return gasPrice;
    }

    public byte[] getGasLimit() {
        if (!parsed) {
            rlpParse();
        }

        return gasLimit;
    }

    public void setGasLimit(byte[] gasLimit) {
        this.gasLimit = ByteUtils.clone(gasLimit);
        // Once the tx content has changed, the signature and rlpEncoded should be recalculated
        this.signature = null;
        this.rlpEncoded = null;
        this.rlpRaw = null;
    }

    public long nonZeroDataBytes() {
        if (data == null) {
            return 0;
        }

        int counter = 0;
        for (final byte aData : data) {
            if (aData != 0) {
                ++counter;
            }
        }
        return counter;
    }

    public byte[] getData() {
        if (!parsed) {
            rlpParse();
        }

        return data;
    }

    public ECDSASignature getSignature() {
        if (!parsed) {
            rlpParse();
        }

        return signature;
    }

    public boolean acceptTransactionSignature(byte currentChainId) {
        ECDSASignature signature = getSignature();
        if (signature == null) {
            return false;
        }

        if (!signature.validateComponents()) {
            return false;
        }

        if (signature.s.compareTo(SECP256K1N_HALF) >= 0) {
            return false;
        }

        byte chId = this.getChainId();

        if (chId !=0 && chId != currentChainId) {
            return false;
        }

        return true;
    }

    public void sign(byte[] privKeyBytes) throws MissingPrivateKeyException {
        byte[] rawHash = this.getRawHash().getBytes();
        ECKey key = ECKey.fromPrivate(privKeyBytes).decompress();
        this.setSignature(key.sign(rawHash));
    }

    public void setSignature(ECDSASignature signature) {
        this.signature = signature;
        this.rlpEncoded = null;
    }

    @Nullable
    public RskAddress getContractAddress() {
        if (!isContractCreation()) {
            return null;
        }

        return new RskAddress(HashUtil.calcNewAddr(this.getSender().getBytes(), this.getNonce()));
    }

    public boolean isContractCreation() {
        if (!parsed) {
            rlpParse();
        }

        return this.receiveAddress.equals(RskAddress.nullAddress());
    }

    /*
     * Crypto
     */

    public ECKey getKey() {
        byte[] rawHash = getRawHash().getBytes();
        //We clear the 4th bit, the compress bit, in case a signature is using compress in true
        return ECKey.recoverFromSignature((signature.v - 27) & ~4, signature, rawHash, true);
    }

    public synchronized RskAddress getSender() {
        if (sender != null) {
            return sender;
        }

        try {
            ECKey key = ECKey.signatureToKey(getRawHash().getBytes(), getSignature().toBase64());
            sender = new RskAddress(key.getAddress());
        } catch (SignatureException e) {
            logger.error(e.getMessage(), e);
            panicProcessor.panic("transaction", e.getMessage());
            sender = RskAddress.nullAddress();
        }

        return sender;
    }

    public byte getChainId() {
        if (!parsed) {
            rlpParse();
        }
        return chainId;
    }

    @Override
    public String toString() {
        if (!parsed) {
            rlpParse();
        }

        return "TransactionData [" + "hash=" + ByteUtil.toHexString(hash) +
                "  nonce=" + ByteUtil.toHexString(nonce) +
                ", gasPrice=" + gasPrice.toString() +
                ", gas=" + ByteUtil.toHexString(gasLimit) +
                ", receiveAddress=" + receiveAddress.toString() +
                ", value=" + value.toString() +
                ", data=" + ByteUtil.toHexString(data) +
                ", signatureV=" + (signature == null ? "" : signature.v) +
                ", signatureR=" + (signature == null ? "" : ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(signature.r))) +
                ", signatureS=" + (signature == null ? "" : ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(signature.s))) +
                "]";
    }

    /**
     * For signatures you have to keep also
     * RLP of the transaction without any signature data
     */
    public byte[] getEncodedRaw() {

        if (!parsed) {
            rlpParse();
        }

        if (rlpRaw != null) {
            return rlpRaw;
        }

        // parse null as 0 for nonce
        byte[] toEncodeNonce = null;
        if (this.nonce == null || this.nonce.length == 1 && this.nonce[0] == 0) {
            toEncodeNonce = RLP.encodeElement((byte[]) null);
        } else {
            toEncodeNonce = RLP.encodeElement(this.nonce);
        }
        byte[] toEncodeGasPrice = RLP.encodeCoinNonNullZero(this.gasPrice);
        byte[] toEncodeGasLimit = RLP.encodeElement(this.gasLimit);
        byte[] toEncodeReceiveAddress = RLP.encodeRskAddress(this.receiveAddress);
        byte[] toEncodeValue = RLP.encodeCoinNullZero(this.value);
        byte[] toEncodeData = RLP.encodeElement(this.data);

        // Since EIP-155 use chainId for v
        if (chainId == 0) {
            rlpRaw = RLP.encodeList(toEncodeNonce, toEncodeGasPrice, toEncodeGasLimit, toEncodeReceiveAddress,
                    toEncodeValue, toEncodeData);
        } else {
            byte[] v;
            byte[] r;
            byte[] s;
            v = RLP.encodeByte(chainId);
            r = RLP.encodeElement(EMPTY_BYTE_ARRAY);
            s = RLP.encodeElement(EMPTY_BYTE_ARRAY);
            rlpRaw = RLP.encodeList(toEncodeNonce, toEncodeGasPrice, toEncodeGasLimit, toEncodeReceiveAddress,
                    toEncodeValue, toEncodeData, v, r, s);
        }
        return rlpRaw;
    }

    public byte[] getEncoded() {
        if (rlpEncoded != null) {
            return rlpEncoded;
        }

        // parse null as 0 for nonce
        byte[] toEncodeNonce = null;
        if (this.nonce == null || this.nonce.length == 1 && this.nonce[0] == 0) {
            toEncodeNonce = RLP.encodeElement((byte[]) null);
        } else {
            toEncodeNonce = RLP.encodeElement(this.nonce);
        }
        byte[] toEncodeGasPrice = RLP.encodeCoinNonNullZero(this.gasPrice);
        byte[] toEncodeGasLimit = RLP.encodeElement(this.gasLimit);
        byte[] toEncodeReceiveAddress = RLP.encodeRskAddress(this.receiveAddress);
        byte[] toEncodeValue = RLP.encodeCoinNullZero(this.value);
        byte[] toEncodeData = RLP.encodeElement(this.data);

        byte[] v;
        byte[] r;
        byte[] s;

        if (signature != null) {
            int encodeV;
            if (chainId == 0) {
                encodeV = signature.v;
            } else {
                encodeV = signature.v - LOWER_REAL_V;
                encodeV += chainId * 2 + CHAIN_ID_INC;
            }
            v = RLP.encodeByte((byte) encodeV);
            r = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.r));
            s = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.s));
        } else {
            // Since EIP-155 use chainId for v
            v = chainId == 0 ? RLP.encodeElement(EMPTY_BYTE_ARRAY) : RLP.encodeByte(chainId);
            r = RLP.encodeElement(EMPTY_BYTE_ARRAY);
            s = RLP.encodeElement(EMPTY_BYTE_ARRAY);
        }

        this.rlpEncoded = RLP.encodeList(toEncodeNonce, toEncodeGasPrice, toEncodeGasLimit,
                toEncodeReceiveAddress, toEncodeValue, toEncodeData, v, r, s);

        Keccak256 hash = this.getHash();

        this.hash = hash == null ? null : hash.getBytes();

        return rlpEncoded;
    }

    public BigInteger getGasLimitAsInteger() {
        return (this.getGasLimit() == null) ? null : BigIntegers.fromUnsignedByteArray(this.getGasLimit());
    }

    public BigInteger getNonceAsInteger() {
        return (this.getNonce() == null) ? null : BigIntegers.fromUnsignedByteArray(this.getNonce());
    }

    @Override
    public int hashCode() {
        return this.getHash().hashCode();
    }

    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof Transaction)) {
            return false;
        }

        Transaction tx = (Transaction) obj;

        return Objects.equals(this.getHash(), tx.getHash());
    }

    public static Transaction create(RskSystemProperties config, String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit){
        return create(config, to, amount, nonce, gasPrice, gasLimit, (byte[]) null);
    }

    public static Transaction create(RskSystemProperties config, String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, String data){
        byte[] decodedData = data == null ? null : Hex.decode(data);
        return create(config, to, amount, nonce, gasPrice, gasLimit, decodedData);
    }

    public static Transaction create(RskSystemProperties config, String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, byte[] decodedData) {
        return new Transaction(BigIntegers.asUnsignedByteArray(nonce),
                gasPrice.toByteArray(),
                BigIntegers.asUnsignedByteArray(gasLimit),
                to != null ? Hex.decode(to) : null,
                BigIntegers.asUnsignedByteArray(amount),
                decodedData,
                config.getBlockchainConfig().getCommonConstants().getChainId());
    }

    private byte[] nullToZeroArray(byte[] data) {
        return data == null ? ZERO_BYTE_ARRAY : data;
    }

    public boolean isLocalCallTransaction() {
        return isLocalCall;
    }

    public void setLocalCallTransaction(boolean isLocalCall) {
        this.isLocalCall = isLocalCall;
    }
}
