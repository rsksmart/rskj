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

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.panic.PanicProcessor;
import co.rsk.peg.BridgeUtils;
import co.rsk.util.ListArrayUtil;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.ECKey.ECDSASignature;
import org.ethereum.crypto.ECKey.MissingPrivateKeyException;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.security.SignatureException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Arrays;
import java.util.ArrayList;

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
    public static final int DATAWORD_LENGTH = 32;
    private static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};
    private static final Logger logger = LoggerFactory.getLogger(Transaction.class);
    private static final Profiler profiler = ProfilerFactory.getInstance();
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    private static final BigInteger SECP256K1N_HALF = Constants.getSECP256K1N().divide(BigInteger.valueOf(2));

    /* RSKIP-145 */
    private static final byte FORMAT_ONE_LEADING = 0x1;

    private static final byte NONCE_ID = 0;
    private static final byte AMOUNT_ID = 1;
    private static final byte RECEIVER_ID = 2;
    private static final byte GAS_PRICE_ID = 3;
    private static final byte GAS_LIMIT_ID = 4;
    private static final byte DATA_ID = 5;
    private static final byte SIGNATURE_ID = 6;
    // default gaslimit is 30000
    private static final byte[] DEFAULT_GAS_LIMIT = new byte[]{0x75,0x30};

    private final int version;
    
    /**
     * Since EIP-155, we could encode chainId in V
     */
    private static final byte CHAIN_ID_INC = 35;

    private static final byte LOWER_REAL_V = 27;
    

    protected RskAddress sender;
    /* whether this is a local call transaction */
    private boolean isLocalCall;
    /* a counter used to make sure each transaction can only be processed once */
    private final byte[] nonce;
    private final Coin value;
    /* the address of the destination account
     * In creation transaction the receive address is - 0 */
    private final RskAddress receiveAddress;
    private final Coin gasPrice;
    /* the amount of "gas" to allow for the computation.
     * Gas is the fuel of the computational engine.
     * Every computational step taken and every byte added
     * to the state or transaction list consumes some gas. */
    private byte[] gasLimit;
    /* An unlimited size byte array specifying
     * input [data] of the message call or
     * Initialization code for a new contract */
    private final byte[] data;
    private final byte chainId;
    /* the elliptic curve signature
     * (including public key recovery bits) */
    private ECDSASignature signature;
    private byte[] rlpEncoding;
    private byte[] rawRlpEncoding;
    private Keccak256 hash;
    private Keccak256 rawHash;

    protected Transaction(byte[] rawData){
        this(rawData, null);
    }
    
    protected Transaction(byte[] rawData, RLPList rsv) {
        if (rawData[0] != FORMAT_ONE_LEADING){
            version = 0;
            List<RLPElement> transaction = RLP.decodeList(rawData);
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
            byte[] vData = transaction.get(6).getRLPData();
            if (vData != null) {
                if (vData.length != 1) {
                    throw new TransactionException("Signature V is invalid");
                }
                byte v = vData[0];
                this.chainId = extractChainIdFromV(v);
                byte[] r = transaction.get(7).getRLPData();
                byte[] s = transaction.get(8).getRLPData();
                this.signature = ECDSASignature.fromComponents(r, s, getRealV(v));
            } else {
                this.chainId = 0;
                logger.trace("RLP encoded tx is not signed!");
            }
        }else{
            version = 1;
            byte[] nonce = new byte[]{1};
            RskAddress receiveAddress = RskAddress.nullAddress();
            Coin value = Coin.ZERO;
            Coin gasPrice = null;
            byte[] gasLimit = DEFAULT_GAS_LIMIT;
            byte[] data = null;
            byte[] signature = null;

            byte[] realRLPEncoded = Arrays.copyOfRange(rawData, 1, rawData.length);
            List<RLPElement> transaction = RLP.decodeList(realRLPEncoded);
            for (RLPElement element : transaction){
                byte[] eleData = element.getRLPData();
                //elememt id contained in lower 5 bits 
                int type = eleData[0] & (byte)0x1f; 
                byte[] realElementData = Arrays.copyOfRange(eleData, 1, eleData.length);
                switch (type) {
                    case NONCE_ID:
                        nonce = realElementData;
                        break;
                    case RECEIVER_ID:
                        receiveAddress = RLP.parseRskAddress(realElementData);
                        if (receiveAddress.equals(RskAddress.nullAddress())){
                            throw new IllegalArgumentException("Transaction format one should not contain default receiver address");   
                        }   
                        break;
                    case AMOUNT_ID:
                        value = RLP.parseCoinNonNullZero(realElementData);
                        if (value.equals(Coin.ZERO)){
                            throw new IllegalArgumentException("Transaction format one should not contain default value");   
                        }
                        break;
                    case GAS_PRICE_ID:
                        gasPrice = RLP.parseCoinNonNullZero(realElementData);
                        break;
                    case GAS_LIMIT_ID:
                        gasLimit = realElementData;
                        if (Arrays.equals(gasLimit, DEFAULT_GAS_LIMIT)){
                            throw new IllegalArgumentException("Transaction format one should not contain default gas limit");   
                        }
                        break;
                    case DATA_ID:
                        data = realElementData;
                        break;
                    case SIGNATURE_ID:
                        signature = realElementData;
                        break;
                    default:
                        throw new IllegalArgumentException("A transaction contain a unknown element id");     
                }
            } 
            this.nonce = nonce;
            this.value = value;
            this.receiveAddress = receiveAddress;
            if (gasPrice == null){
                throw new IllegalArgumentException("A transaction must contain gas price");
            }else{
                this.gasPrice = gasPrice;
            }
            this.gasLimit = gasLimit;
            this.data = data;
            
            if (signature == null && rsv == null){
                throw new IllegalArgumentException("A transaction must be signed");
            }else {
                List<RLPElement> comps;
                if (signature != null){
                    comps =  RLP.decodeList(signature);
                }else{
                    comps =  rsv;
                }
                if (comps.size() != 3) {
                    throw new IllegalArgumentException("A signature must have exactly 3 elements");
                }
                byte[] r = comps.get(0).getRLPData();
                byte[] s = comps.get(1).getRLPData();
                byte[] v = comps.get(2).getRLPData();
                this.signature = ECDSASignature.fromComponents(r, s, getRealV(v[0]));
                this.chainId = extractChainIdFromV(v[0]);
            }
        }
    }

    public Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data, (byte) 0);
    }

    public Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data, int version) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data, (byte) 0, version);
    }

    public Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data, byte[] r, byte[] s, byte v) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data, (byte) 0);

        this.signature = ECDSASignature.fromComponents(r, s, v);
    }

    public Transaction(long nonce, long gasPrice, long gas, String to, long value, byte[] data, byte chainId) {
        this(BigInteger.valueOf(nonce).toByteArray(), BigInteger.valueOf(gasPrice).toByteArray(),
                BigInteger.valueOf(gas).toByteArray(), Hex.decode(to), BigInteger.valueOf(value).toByteArray(),
                data, chainId);
    }

    public Transaction(long nonce, long gasPrice, long gas, String to, long value, byte[] data, byte chainId, int version) {
        this(BigInteger.valueOf(nonce).toByteArray(), BigInteger.valueOf(gasPrice).toByteArray(),
                BigInteger.valueOf(gas).toByteArray(), Hex.decode(to), BigInteger.valueOf(value).toByteArray(),
                data, chainId, version);
    }

    public Transaction(BigInteger nonce, BigInteger gasPrice, BigInteger gas, String to, BigInteger value, byte[] data,
                       byte chainId) {
        this(nonce.toByteArray(), gasPrice.toByteArray(), gas.toByteArray(), Hex.decode(to), value.toByteArray(), data,
                chainId);
    }

    public Transaction(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, byte chainId) {
        this(to, amount, nonce, gasPrice, gasLimit, (byte[]) null, chainId);
    }

    public Transaction(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, String data, byte chainId) {
        this(to, amount, nonce, gasPrice, gasLimit, data == null ? null : Hex.decode(data), chainId);
    }

    public Transaction(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, byte[] decodedData, byte chainId) {
        this(BigIntegers.asUnsignedByteArray(nonce),
                gasPrice.toByteArray(),
                BigIntegers.asUnsignedByteArray(gasLimit),
                to != null ? Hex.decode(to) : null,
                BigIntegers.asUnsignedByteArray(amount),
                decodedData,
                chainId);
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
        this.version = 1;
    }

    public Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] valueRaw, byte[] data,
                       byte chainId, int version) {
        this.nonce = ByteUtil.cloneBytes(nonce);
        this.gasPrice = RLP.parseCoinNonNullZero(ByteUtil.cloneBytes(gasPriceRaw));
        this.gasLimit = ByteUtil.cloneBytes(gasLimit);
        this.receiveAddress = RLP.parseRskAddress(ByteUtil.cloneBytes(receiveAddress));
        this.value = RLP.parseCoinNullZero(ByteUtil.cloneBytes(valueRaw));
        this.data = ByteUtil.cloneBytes(data);
        this.chainId = chainId;
        this.isLocalCall = false;
        this.version = version;
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
    public long transactionCost(Constants constants, ActivationConfig.ForBlock activations) {
        // Federators txs to the bridge are free during system setup
        if (BridgeUtils.isFreeBridgeTx(this, constants, activations)) {
            return 0;
        }
        if (version == 0){
            long nonZeroes = this.nonZeroBytes(this.getData());
            long zeroVals = ListArrayUtil.getLength(this.getData()) - nonZeroes;

            return (this.isContractCreation() ? GasCost.TRANSACTION_CREATE_CONTRACT : GasCost.TRANSACTION_FORMAT_ZERO) + zeroVals * GasCost.TX_ZERO_DATA + nonZeroes * GasCost.TX_NO_ZERO_DATA;
        }else{
            long nonZeroes = this.nonZeroBytes(this.getEncoded());
            long zeroVals = ListArrayUtil.getLength(this.getEncoded()) - nonZeroes;

            return (this.isContractCreation() ? GasCost.TRANSACTION_CREATE_CONTRACT : GasCost.TRANSACTION_FORMAT_ONE) + zeroVals * GasCost.TX_ZERO_DATA + nonZeroes * GasCost.TX_NO_ZERO_DATA;
        }
    }

    public void verify() {
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

    public Keccak256 getHash() {
        if (hash == null) {
            if (version == 0){
                byte[] plainMsg = this.getEncoded();
                this.hash = new Keccak256(HashUtil.keccak256(plainMsg));
            }else{
                byte[] plainMsg = this.getFullRec();
                this.hash = new Keccak256(HashUtil.keccak256(plainMsg));
            }
        }

        return this.hash;
    }

    public Keccak256 getRawHash() {
        if (rawHash == null) {
            byte[] plainMsg = this.getEncodedRaw();
            this.rawHash = new Keccak256(HashUtil.keccak256(plainMsg));
        }

        return this.rawHash;
    }

    public byte[] getNonce() {
        return nullToZeroArray(nonce);
    }

    public Coin getValue() {
        return value;
    }

    public RskAddress getReceiveAddress() {
        return receiveAddress;
    }

    public Coin getGasPrice() {
        // some blocks have zero encoded as null, but if we altered the internal field then re-encoding the value would
        // give a different value than the original.
        if (gasPrice == null) {
            return Coin.ZERO;
        }

        return gasPrice;
    }

    public byte[] getGasLimit() {
        return gasLimit;
    }

    public byte[] getData() {
        return data;
    }

    public ECDSASignature getSignature() {
        return signature;
    }

    public boolean acceptTransactionSignature(byte currentChainId) {
        ECDSASignature signature = getSignature();
        if (signature == null || !signature.validateComponents() || signature.s.compareTo(SECP256K1N_HALF) >= 0) {
            return false;
        }

        return this.getChainId() == 0 || this.getChainId() == currentChainId;
    }

    public byte[] getFullRec(){
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(NONCE_ID);
            if (!(this.nonce == null || this.nonce.length == 1 && this.nonce[0] == 1)) {
                baos.write(this.nonce);
            }
            baos.write(AMOUNT_ID);
            if ((this.value != null) && (!this.value.equals(Coin.ZERO))){
                baos.write(this.value.getBytes());
            }
            baos.write(RECEIVER_ID);
            if ((this.receiveAddress!=null) && (!this.receiveAddress.equals(RskAddress.nullAddress()))){
                baos.write(this.receiveAddress.getBytes());
            }
            baos.write(GAS_PRICE_ID);
            baos.write(this.gasPrice.getBytes());
            baos.write(GAS_LIMIT_ID);
            if ((this.gasLimit != null) && (!Arrays.equals(this.gasLimit, DEFAULT_GAS_LIMIT))){
                baos.write(this.gasLimit);
            }
            baos.write(DATA_ID);
            if (this.data != null){
                baos.write(this.data);
            }
            return baos.toByteArray();
        }catch (IOException e){
            throw new TransactionException("Cannot generate fullRec");
        }
    }

    public void sign(byte[] privKeyBytes) throws MissingPrivateKeyException {
        byte[] raw;
        if (version == 0 ){
            raw = this.getRawHash().getBytes();
        }else{
            raw = this.getHash().getBytes();
        }
        ECKey key = ECKey.fromPrivate(privKeyBytes).decompress();
        this.signature = key.sign(raw);
        this.rlpEncoding = null;
        this.hash = null;
        this.sender = null;
    }
    public void setSignature(byte[] r, byte[] s, byte[] v){
        this.signature = ECDSASignature.fromComponents(r, s, getRealV(v[0]));
    }

    public void setSignature(ECDSASignature signature) {
        this.signature = signature;
        this.rlpEncoding = null;
        this.hash = null;
        this.sender = null;
    }

    @Nullable
    public RskAddress getContractAddress() {
        if (!isContractCreation()) {
            return null;
        }

        return new RskAddress(HashUtil.calcNewAddr(this.getSender().getBytes(), this.getNonce()));
    }

    public boolean isContractCreation() {
        return this.receiveAddress.equals(RskAddress.nullAddress());
    }

    private long nonZeroBytes(byte[] data){
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

    /*
     * Crypto
     */

    public ECKey getKey() {
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.KEY_RECOV_FROM_SIG);
        byte[] raw;
        if (version == 0){
            raw = getRawHash().getBytes();
        }else{
            raw = getHash().getBytes();
        }
        
        //We clear the 4th bit, the compress bit, in case a signature is using compress in true
        ECKey key = ECKey.recoverFromSignature((signature.v - 27) & ~4, signature, raw, true);
        profiler.stop(metric);
        return key;
    }

    public synchronized RskAddress getSender() {
        if (sender != null) {
            return sender;
        }


        Metric metric = profiler.start(Profiler.PROFILING_TYPE.KEY_RECOV_FROM_SIG);
        try {
            byte[] raw;
            if (version == 0){
                raw = getRawHash().getBytes();
            }else{
                raw = getHash().getBytes();
            }
            ECKey key = ECKey.signatureToKey(raw, getSignature());
            sender = new RskAddress(key.getAddress());
        } catch (SignatureException e) {
            logger.error(e.getMessage(), e);
            panicProcessor.panic("transaction", e.getMessage());
            sender = RskAddress.nullAddress();
        }
        finally {
            profiler.stop(metric);
        }

        return sender;
    }

    public byte getChainId() {
        return chainId;
    }
 
    public int getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "TransactionData [" + "hash=" + ByteUtil.toHexString(getHash().getBytes()) +
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
        if (this.rawRlpEncoding == null) {
            // Since EIP-155 use chainId for v
            if (version == 0){
                if (chainId == 0) {
                    this.rawRlpEncoding = encode(null, null, null, false);
                } else {
                    byte[] v = RLP.encodeByte(chainId);
                    byte[] r = RLP.encodeElement(EMPTY_BYTE_ARRAY);
                    byte[] s = RLP.encodeElement(EMPTY_BYTE_ARRAY);

                    this.rawRlpEncoding = encode(v, r, s, true);
                }
            }else{
                throw new UnsupportedOperationException("version 1 need use getFullRec to compute signature");
            }
        }

        return ByteUtil.cloneBytes(this.rawRlpEncoding);
    }

    @Nullable
    public byte[] getEncodedRSV(){
        if (version == 0){
            throw new UnsupportedOperationException("version 0 does not support this operation");
        }
        if (this.signature != null){
            byte[] v = RLP.encodeByte((byte) (chainId == 0 ? signature.v : (signature.v - LOWER_REAL_V) + (chainId * 2 + CHAIN_ID_INC)));
            byte[] r = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.r));
            byte[] s = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.s));
            return RLP.encodeList(r, s, v);
        }
        return null;
    }
    /*
    public byte[] getEncoded(){
        return getEncoded(false);
    }*/
    public byte[] getEncodedForBlock(){
        return getEncoded(true);
    }

    public byte[] getEncoded(){
        return getEncoded(false);
    }

    public byte[] getEncoded(boolean forBlockContain) {
        if (this.rlpEncoding == null) {
            byte[] v;
            byte[] r;
            byte[] s;
            if (this.signature != null) {
                v = RLP.encodeByte((byte) (chainId == 0 ? signature.v : (signature.v - LOWER_REAL_V) + (chainId * 2 + CHAIN_ID_INC)));
                r = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.r));
                s = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.s));
            } else {
                v = chainId == 0 ? RLP.encodeElement(EMPTY_BYTE_ARRAY) : RLP.encodeByte(chainId);
                r = RLP.encodeElement(EMPTY_BYTE_ARRAY);
                s = RLP.encodeElement(EMPTY_BYTE_ARRAY);
            }

            this.rlpEncoding = encode(v, r, s, !forBlockContain);
        }

        return ByteUtil.cloneBytes(this.rlpEncoding);
    }

    private byte[] encode(byte[] v, byte[] r, byte[] s, boolean versionOneContainSig) {
        if (version == 0){
            // parse null as 0 for nonce
            byte[] toEncodeNonce;
            if (this.nonce == null || this.nonce.length == 1 && this.nonce[0] == 0) {
                toEncodeNonce = RLP.encodeElement(null);
            } else {
                toEncodeNonce = RLP.encodeElement(this.nonce);
            }
            byte[] toEncodeGasPrice = RLP.encodeCoinNonNullZero(this.gasPrice);
            byte[] toEncodeGasLimit = RLP.encodeElement(this.gasLimit);
            byte[] toEncodeReceiveAddress = RLP.encodeRskAddress(this.receiveAddress);
            byte[] toEncodeValue = RLP.encodeCoinNullZero(this.value);
            byte[] toEncodeData = RLP.encodeElement(this.data);

            if (v == null && r == null && s == null) {
                return RLP.encodeList(toEncodeNonce, toEncodeGasPrice, toEncodeGasLimit,
                    toEncodeReceiveAddress, toEncodeValue, toEncodeData);
            } 

            return RLP.encodeList(toEncodeNonce, toEncodeGasPrice, toEncodeGasLimit,
                toEncodeReceiveAddress, toEncodeValue, toEncodeData, v, r, s);
        }else {
            //version 1
            byte[] toEncodeNonce = null;
            if (!(this.nonce == null || this.nonce.length == 1 && this.nonce[0] == 1)) {
                toEncodeNonce = new byte[1 + this.nonce.length];
                toEncodeNonce[0] = NONCE_ID;
                System.arraycopy(this.nonce, 0, toEncodeNonce, 1, this.nonce.length);
                toEncodeNonce = RLP.encodeElement(toEncodeNonce);
            }

            byte[] toEncodeValue = null;
            if (this.value != null){
                byte[] valueBytes = this.value.getBytes();
                toEncodeValue = new byte[1 + valueBytes.length];
                toEncodeValue[0] = AMOUNT_ID;
                System.arraycopy(valueBytes, 0, toEncodeValue, 1, valueBytes.length);
                toEncodeValue = RLP.encodeElement(toEncodeValue);
            }

            byte[] toEncodeReceiveAddress = null;
            if (!this.receiveAddress.equals(RskAddress.nullAddress())){
                byte[] addrBytes = this.receiveAddress.getBytes();
                toEncodeReceiveAddress = new byte[1 + addrBytes.length];
                toEncodeReceiveAddress[0] = RECEIVER_ID;
                System.arraycopy(addrBytes, 0, toEncodeReceiveAddress, 1, addrBytes.length);
                toEncodeReceiveAddress = RLP.encodeElement(toEncodeReceiveAddress);
            }

            byte[] toEncodeGasPrice = new byte[1 + this.gasPrice.getBytes().length];
            toEncodeGasPrice[0] = GAS_PRICE_ID;
            System.arraycopy(this.gasPrice.getBytes(), 0, toEncodeGasPrice, 1, this.gasPrice.getBytes().length);
            toEncodeGasPrice = RLP.encodeElement(toEncodeGasPrice);

            byte[] toEncodeGasLimit = null;
            if (!Arrays.equals(this.gasLimit, DEFAULT_GAS_LIMIT)){
                toEncodeGasLimit = new byte[1 + this.gasLimit.length];
                toEncodeGasLimit[0] = GAS_LIMIT_ID;
                System.arraycopy(this.gasLimit, 0, toEncodeGasLimit, 1, this.gasLimit.length);
                toEncodeGasLimit = RLP.encodeElement(toEncodeGasLimit);
            }

            byte[] toEncodeData = null;
            if (this.data != null){
                toEncodeData = new byte[1 + this.data.length];
                toEncodeData[0] = DATA_ID;
                System.arraycopy(this.data, 0, toEncodeData, 1, this.data.length);
                toEncodeData = RLP.encodeElement(toEncodeData);
            }
            
            byte[] toEncodeSig = null;
            if (versionOneContainSig){
                byte[] sigList = RLP.encodeList(r,s,v);
                toEncodeSig = new byte[1 + sigList.length];
                toEncodeSig[0] = SIGNATURE_ID;
                System.arraycopy(sigList, 0, toEncodeSig, 1, sigList.length);
                toEncodeSig = RLP.encodeElement(toEncodeSig);
            }

            List<byte[]> toEncodedElements = new ArrayList<byte[]>();
            if (toEncodeNonce != null){
                toEncodedElements.add(toEncodeNonce);
            }
            if (toEncodeValue != null){
                toEncodedElements.add(toEncodeValue);
            }
            if (toEncodeReceiveAddress != null){
                toEncodedElements.add(toEncodeReceiveAddress);
            }
            toEncodedElements.add(toEncodeGasPrice);
            if (toEncodeGasLimit != null){
                toEncodedElements.add(toEncodeGasLimit);
            }
            if (toEncodeData != null){
                toEncodedElements.add(toEncodeData);
            }
            toEncodedElements.add(toEncodeSig);
            byte[] allEncodedElements = RLP.encodeList(toEncodedElements.toArray(new byte[toEncodedElements.size()][]));
            byte[] finalResult = new byte[1 + allEncodedElements.length];
            finalResult[0] = FORMAT_ONE_LEADING;
            System.arraycopy(allEncodedElements, 0, finalResult, 1, allEncodedElements.length);
            return finalResult;
        }

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


    private byte[] nullToZeroArray(byte[] data) {
        return data == null ? ZERO_BYTE_ARRAY : data;
    }

    public boolean isLocalCallTransaction() {
        return isLocalCall;
    }

    public void setLocalCallTransaction(boolean isLocalCall) {
        this.isLocalCall = isLocalCall;
    }

    public boolean isRemascTransaction(int txPosition, int txsSize) {
        return isLastTx(txPosition, txsSize) && checkRemascAddress() && checkRemascTxZeroValues();
    }

    private boolean isLastTx(int txPosition, int txsSize) {
        return txPosition == (txsSize - 1);
    }

    private boolean checkRemascAddress() {
        return PrecompiledContracts.REMASC_ADDR.equals(getReceiveAddress());
    }

    private boolean checkRemascTxZeroValues() {
        if (null != getData() || null != getSignature()) {
            return false;
        }

        return Coin.ZERO.equals(getValue()) &&
                BigInteger.ZERO.equals(new BigInteger(1, getGasLimit())) &&
                Coin.ZERO.equals(getGasPrice());
    }
}
