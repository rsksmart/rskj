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
import co.rsk.panic.PanicProcessor;
import co.rsk.peg.BridgeUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.ethereum.config.Constants;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.ECKey.ECDSASignature;
import org.ethereum.crypto.ECKey.MissingPrivateKeyException;
import org.ethereum.crypto.HashUtil;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.converters.CallArgumentsToByteArray;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.ethereum.vm.GasCost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;
import org.spongycastle.util.BigIntegers;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.List;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * A transaction (formally, T) is a single cryptographically
 * signed instruction sent by an actor external to Ethereum.
 * An external actor can be a person (via a mobile device or desktop computer)
 * or could be from a piece of automated software running on a server.
 * There are two types of transactions: those which result in message calls
 * and those which result in the creation of new contracts.
 */

// TODO review implements SerializableObejct
public class Transaction implements SerializableObject {
    private static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};

    private static final Logger logger = LoggerFactory.getLogger(Transaction.class);
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    private static final BigInteger SECP256K1N_HALF = Constants.getSECP256K1N().divide(BigInteger.valueOf(2));

    /* SHA3 hash of the RLP encoded transaction */
    private byte[] hash;

    /* a counter used to make sure each transaction can only be processed once */
    private byte[] nonce;

    /* the amount of ether to transfer (calculated as wei) */
    private byte[] value;

    /* the address of the destination account
     * In creation transaction the receive address is - 0 */
    private byte[] receiveAddress;

    /* the amount of ether to pay as a transaction fee
     * to the miner for each unit of gas */
    private byte[] gasPrice;

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

    protected byte[] sendAddress;

    /* Tx in encoded form */
    protected byte[] rlpEncoded;
    private byte[] rlpRaw;
    /* Indicates if this transaction has been parsed
     * from the RLP-encoded data */
    private boolean parsed = false;

    protected Transaction(byte[] rawData) {
        this.rlpEncoded = rawData;
        parsed = false;
    }

    /* creation contract tx
     * [ nonce, gasPrice, gasLimit, "", endowment, init, signature(v, r, s) ]
     * or simple send tx
     * [ nonce, gasPrice, gasLimit, receiveAddress, value, data, signature(v, r, s) ]
     */
    public Transaction(byte[] nonce, byte[] gasPrice, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data) {
        this(nonce, gasPrice, gasLimit, receiveAddress, value, data, (byte) 0);
    }

    public Transaction(byte[] nonce, byte[] gasPrice, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data, byte[] r, byte[] s, byte v) {
        this(nonce, gasPrice, gasLimit, receiveAddress, value, data, (byte) 0);

        this.signature = ECDSASignature.fromComponents(r, s, v);
    }

    public Transaction(byte[] nonce, byte[] gasPrice, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data,
                       byte chainId) {
        this.nonce = ByteUtil.cloneBytes(nonce);
        this.gasPrice = ByteUtil.cloneBytes(gasPrice);
        this.gasLimit = ByteUtil.cloneBytes(gasLimit);
        this.receiveAddress = ByteUtil.cloneBytes(receiveAddress);
        if (value == null || ByteUtil.isSingleZero(value)) {
            this.value = EMPTY_BYTE_ARRAY;
        } else {
            this.value = ByteUtil.cloneBytes(value);
        }
        this.data = ByteUtil.cloneBytes(data);
        this.chainId = chainId;

        if (receiveAddress == null) {
            this.receiveAddress = ByteUtil.EMPTY_BYTE_ARRAY;
        }

        parsed = true;
    }

    public Transaction toImmutableTransaction() {
        return new ImmutableTransaction(this.getEncoded());
    }

    private byte extractChainIdFromV(byte v) {
        if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1))
            return 0;
        return (byte) (((0x00FF & v) - CHAIN_ID_INC) / 2);
    }

    private byte getRealV(byte v) {
        if (v == LOWER_REAL_V || v == (LOWER_REAL_V + 1))
            return v;
        byte realV = LOWER_REAL_V;
        int inc = 0;
        if ((int) v % 2 == 0)
            inc = 1;
        return (byte) (realV + inc);
    }

    // There was a method called NEW_getTransactionCost that implemented this alternative solution:
    // "return (this.isContractCreation() ? GasCost.TRANSACTION_CREATE_CONTRACT : GasCost.TRANSACTION)
    //         + zeroVals * GasCost.TX_ZERO_DATA + nonZeroes * GasCost.TX_NO_ZERO_DATA;"
    public long transactionCost(Block block){
        if (!parsed)
            rlpParse();

		//Federators txs to the bridge are free during system setup
        if (BridgeUtils.isFreeBridgeTx(this, block.getNumber())) {
            return 0;
        }

        long nonZeroes = this.nonZeroDataBytes();
        long zeroVals  = ArrayUtils.getLength(this.getData()) - nonZeroes;

        return GasCost.TRANSACTION + zeroVals * GasCost.TX_ZERO_DATA + nonZeroes * GasCost.TX_NO_ZERO_DATA;
    }

    public void rlpParse() {
        List<RLPElement> transaction = (RLPList)RLP.decode2(rlpEncoded).get(0);

        this.nonce = transaction.get(0).getRLPData();
        this.gasPrice = transaction.get(1).getRLPData();
        this.gasLimit = transaction.get(2).getRLPData();
        this.receiveAddress = transaction.get(3).getRLPData();
        this.value = transaction.get(4).getRLPData();
        this.data = transaction.get(5).getRLPData();
        // only parse signature in case tx is signed
        if (transaction.get(6).getRLPData() != null) {
            byte[] vData =  transaction.get(6).getRLPData();
            if (vData.length != 1 )
                throw new TransactionException("Signature V is invalid");
            byte v = vData[0];
            this.chainId = extractChainIdFromV(v);
            byte[] r = transaction.get(7).getRLPData();
            byte[] s = transaction.get(8).getRLPData();
            this.signature = ECDSASignature.fromComponents(r, s, getRealV(v));
        } else {
            logger.debug("RLP encoded tx is not signed!");
        }
        this.parsed = true;
        this.hash = getHash();
    }

    public boolean isParsed() {
        return parsed;
    }

    public byte[] getHash() {
        if (!parsed)
            rlpParse();

        byte[] plainMsg = this.getEncoded();
        return HashUtil.sha3(plainMsg);
    }

    public byte[] getRawHash() {
        if (!parsed)
            rlpParse();

        byte[] plainMsg = this.getEncodedRaw();
        return HashUtil.sha3(plainMsg);
    }

    public byte[] getNonce() {
        if (!parsed)
            rlpParse();

        return nonce == null ? ZERO_BYTE_ARRAY : nonce;
    }

    public byte[] getValue() {
        if (!parsed)
            rlpParse();

        return value == null ? ZERO_BYTE_ARRAY : value;
    }

    public byte[] getReceiveAddress() {
        if (!parsed)
            rlpParse();

        return receiveAddress;
    }

    public byte[] getGasPrice() {
        if (!parsed)
            rlpParse();

        return gasPrice == null ? ZERO_BYTE_ARRAY : gasPrice;
    }

    public byte[] getGasLimit() {
        if (!parsed)
            rlpParse();

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
        if (data == null)
            return 0;

        int counter = 0;
        for (final byte aData : data) {
            if (aData != 0)
                ++counter;
        }
        return counter;
    }

    public byte[] getData() {
        if (!parsed)
            rlpParse();

        return data;
    }

    public ECDSASignature getSignature() {
        if (!parsed)
            rlpParse();

        return signature;
    }

    public boolean acceptTransactionSignature() {
        if (!getSignature().validateComponents())
            return false;

        if (getSignature().s.compareTo(SECP256K1N_HALF) >= 0)
            return false;

        byte chId = this.getChainId();

        if (chId !=0 && chId != RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getChainId())
            return false;

        return true;
    }

    public void sign(byte[] privKeyBytes) throws MissingPrivateKeyException {
        byte[] rawHash = this.getRawHash();
        ECKey key = ECKey.fromPrivate(privKeyBytes).decompress();
        this.signature = key.sign(rawHash);
        this.rlpEncoded = null;
    }

    public byte[] getContractAddress() {
        if (!isContractCreation())
            return null;

        return HashUtil.calcNewAddr(this.getSender(), this.getNonce());
    }

    public boolean isContractCreation() {
        if (!parsed)
            rlpParse();

        if (this.receiveAddress == null)
            return true;

        for (int k = 0; k < this.receiveAddress.length; k++)
            if (this.receiveAddress[k] != 0)
                return false;

        return true;
    }

    /*
     * Crypto
     */

    public ECKey getKey() {
        byte[] rawHash = getRawHash();
        //We clear the 4th bit, the compress bit, in case a signature is using compress in true
        return ECKey.recoverFromSignature((signature.v - 27) & ~4, signature, rawHash, true);
    }

    public synchronized byte[] getSender() {
        try {
            if (sendAddress == null) {
                ECKey key = ECKey.signatureToKey(getRawHash(), getSignature().toBase64());
                sendAddress = key.getAddress();
            }
            return sendAddress;
        } catch (SignatureException e) {
            logger.error(e.getMessage(), e);
            panicProcessor.panic("transaction", e.getMessage());
        }
        return null;
    }

    public byte getChainId() {
        if (!parsed)
            rlpParse();
        return chainId;
    }

    @Override
    public String toString() {
        if (!parsed)
            rlpParse();

        return "TransactionData [" + "hash=" + ByteUtil.toHexString(hash) +
                "  nonce=" + ByteUtil.toHexString(nonce) +
                ", gasPrice=" + ByteUtil.toHexString(gasPrice) +
                ", gas=" + ByteUtil.toHexString(gasLimit) +
                ", receiveAddress=" + ByteUtil.toHexString(receiveAddress) +
                ", value=" + ByteUtil.toHexString(value) +
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

        if (!parsed)
            rlpParse();

        if (rlpRaw != null)
            return rlpRaw;

        // parse null as 0 for nonce
        byte[] toEncodeNonce = null;
        if (this.nonce == null || this.nonce.length == 1 && this.nonce[0] == 0) {
            toEncodeNonce = RLP.encodeElement(null);
        } else {
            toEncodeNonce = RLP.encodeElement(this.nonce);
        }
        byte[] toEncodeGasPrice = RLP.encodeElement(this.gasPrice);
        byte[] toEncodeGasLimit = RLP.encodeElement(this.gasLimit);
        byte[] toEncodeReceiveAddress = RLP.encodeElement(this.receiveAddress);
        byte[] toEncodeValue = RLP.encodeElement(this.value);
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
        if (rlpEncoded != null)
            return rlpEncoded;

        // parse null as 0 for nonce
        byte[] toEncodeNonce = null;
        if (this.nonce == null || this.nonce.length == 1 && this.nonce[0] == 0) {
            toEncodeNonce = RLP.encodeElement(null);
        } else {
            toEncodeNonce = RLP.encodeElement(this.nonce);
        }
        byte[] toEncodeGasPrice = RLP.encodeElement(this.gasPrice);
        byte[] toEncodeGasLimit = RLP.encodeElement(this.gasLimit);
        byte[] toEncodeReceiveAddress = RLP.encodeElement(this.receiveAddress);
        byte[] toEncodeValue = RLP.encodeElement(this.value);
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

        this.hash = this.getHash();

        return rlpEncoded;
    }

    public BigInteger getGasPriceAsInteger() {
        return (this.getGasPrice() == null) ? null : BigIntegers.fromUnsignedByteArray(this.getGasPrice());
    }

    public BigInteger getGasLimitAsInteger() {
        return (this.getGasLimit() == null) ? null : BigIntegers.fromUnsignedByteArray(this.getGasLimit());
    }

    public BigInteger getNonceAsInteger() {
        return (this.getNonce() == null) ? null : BigIntegers.fromUnsignedByteArray(this.getNonce());
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(this.getHash());
    }

    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof Transaction))
            return false;

        Transaction tx = (Transaction) obj;

        return Arrays.equals(this.getHash(), tx.getHash());
    }

    public static Transaction create(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit){
        return create(to, amount, nonce, gasPrice, gasLimit, null);
    }

    public static Transaction create(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, String data){

        byte[] decodedData = data == null ? null : Hex.decode(data);

        return new Transaction(BigIntegers.asUnsignedByteArray(nonce),
                BigIntegers.asUnsignedByteArray(gasPrice),
                BigIntegers.asUnsignedByteArray(gasLimit),
                to != null ? Hex.decode(to) : null,
                BigIntegers.asUnsignedByteArray(amount),
                decodedData,
                getConfigChainId());
    }

    public static byte getConfigChainId() {
        return RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getChainId();
    }

    public static Transaction create(byte[] nonce, Web3.CallArguments args){

        CallArgumentsToByteArray hexArgs = new CallArgumentsToByteArray(args);

        return new Transaction(nonce, hexArgs.getGasPrice(), hexArgs.getGasLimit(), hexArgs.getToAddress(), hexArgs.getValue(), hexArgs.getData());
    }
}
