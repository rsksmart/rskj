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
import org.ethereum.util.RLPList;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.security.SignatureException;
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
    public static final int DATAWORD_LENGTH = 32;
    private static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};
    private static final Logger logger = LoggerFactory.getLogger(Transaction.class);
    private static final Profiler profiler = ProfilerFactory.getInstance();
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    private static final BigInteger SECP256K1N_HALF = Constants.getSECP256K1N().divide(BigInteger.valueOf(2));
    /**
     * Since EIP-155, we could encode chainId in V
     */
    private static final byte CHAIN_ID_INC = 35;

    private static final byte LOWER_REAL_V = 27;
    protected RskAddress sender;
    /* whether this is a local call transaction, just local node is involved in the call */
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
    
    /* #mish storage rent gas limit. This is separate from the gasLimit.
       * as with computational (or comp-gas) gasLimit, the rentGasLimit is deducted from origin at start of TX processing
       * unused rent gas is refunded 
     * RSKIP113: 
        * if this field is not specified in a TX, it is set equal to the gasLimit
        * OOG for rentGasLimit -> all rentGas is consumed and TX is reverted
            * Todo: what if there is enough comp gas leftover at EOT to cover rent.. still revert TX?
            * 
        * OOG for comp gasLimit -> 25% of rentGas consumed (even if trie nodes are not touched)
        * rent gas is not included/ has no implications for block gas limit*/
    private byte[] rentGasLimit;
    
    /* An unlimited size byte array specifying
     * input [data] of the message call or
     * Initialization code for a new contract */
    private final byte[] data;
    private final byte chainId;
    /* the elliptic curve signature
     * (including public key recovery bits) */
    private ECDSASignature signature;
    private byte[] rlpEncoding;
    private byte[] rawRlpEncoding; //raw is without signature data
    private Keccak256 hash;
    private Keccak256 rawHash;

    protected Transaction(byte[] rawData) {
        RLPList transaction = RLP.decodeList(rawData);
        int txSize = transaction.size(); 
        if (txSize < 9 || txSize >10) { // #mish: prev was !=9, TX strictly has 9 elements
            throw new IllegalArgumentException("A transaction must have either 9 (without storage rent) or 10 elements (with rent)");
        }

        this.nonce = transaction.get(0).getRLPData();
        this.gasPrice = RLP.parseCoinNonNullZero(transaction.get(1).getRLPData());
        this.gasLimit = transaction.get(2).getRLPData();
        this.receiveAddress = RLP.parseRskAddress(transaction.get(3).getRLPData());
        this.value = RLP.parseCoinNullZero(transaction.get(4).getRLPData());
        this.data = transaction.get(5).getRLPData();
        // 6, 7, 8 are related to signature
        if (txSize==10){ // #mish: has storage rent data
            this.rentGasLimit = transaction.get(9).getRLPData();
        }
 
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
    }

    /* CONTRACT CREATION tx #mish: no receive Addr 
     * [ nonce, gasPrice, gasLimit, "", endowment, init, signature(v, r, s) ]
     * or SIMPLE SEND tx
     * [ nonce, gasPrice, gasLimit, receiveAddress, value, data, signature(v, r, s) ]
     */
    // #mish the full constructor. Move this up to the top, and add rentGasLimit to the full constructor
    // 8 elem byte array: call this C0
    public Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] valueRaw, byte[] data,
                       byte chainId, byte[] rentGasLimit) {
        this.nonce = ByteUtil.cloneBytes(nonce);
        this.gasPrice = RLP.parseCoinNonNullZero(ByteUtil.cloneBytes(gasPriceRaw));
        this.gasLimit = ByteUtil.cloneBytes(gasLimit);
        this.receiveAddress = RLP.parseRskAddress(ByteUtil.cloneBytes(receiveAddress));
        this.value = RLP.parseCoinNullZero(ByteUtil.cloneBytes(valueRaw));
        this.data = ByteUtil.cloneBytes(data);
        this.chainId = chainId;
        this.isLocalCall = false;
        this.rentGasLimit = ByteUtil.cloneBytes(rentGasLimit);
    } 
    /*#mish: modify existing constructor signatures, just add gasLimit again at the end (after chainid) for rentGasLimit
    * then extend them to introduce new versions where rentGasLimit is an explicit argument/parameter*/

    // #mish: this constructor existed earlier as the full constructor (pre storage rent)
    // 7 elem byte array : call this C1: this is like C0 but without explicit rentGasLimit
    public Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] valueRaw, byte[] data,
                        byte chainId){
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, valueRaw, data, chainId, gasLimit);
    }

    // #mish: this constructor present prior to rent. 6 elem byte array, Call this C2. Compared to C1 is does not have chainID.
    // We cannot have a version of this with rentGas added to arglist, cos 7 elem byte array would conflict with C1's signature
    public Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data, (byte) 0, gasLimit);
    }

    // #mish: existed prior to rent: Call this C3. a 9 elem byte array..   //rentgaslimit default set to gaslimit
    public Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data, byte[] r, byte[] s, byte v) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data, (byte) 0, gasLimit);
        this.signature = ECDSASignature.fromComponents(r, s, v);
    }
    // #mish: new. call this C3Ext. Same as C3 above but with rentGasLimit in arglist. 10 elem byte array.. so no conflict with any prior 
    public Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data, 
                byte[] rentGasLimit, byte[] r, byte[] s, byte v) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data, (byte) 0, rentGasLimit);
        this.signature = ECDSASignature.fromComponents(r, s, v);
    }

    /** #mish Moving on to different style of constructor signature */
    /** #mish: And relocating constructors with similar signatures closer */

    // This alt. version uses parameters "to" (instead of receiver addr) and "amount" (instead of value)
    // First extend the prior constructor to account for rentGas
    // Call this C4.
    public Transaction(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, byte[] decodedData,
                                                                     byte chainId, BigInteger rentGasLimit) {
        this(BigIntegers.asUnsignedByteArray(nonce),
                gasPrice.toByteArray(),
                BigIntegers.asUnsignedByteArray(gasLimit),
                to != null ? Hex.decode(to) : null,
                BigIntegers.asUnsignedByteArray(amount),
                decodedData,
                chainId,
                BigIntegers.asUnsignedByteArray(rentGasLimit));
    }

    /** The version of the constructor used prior to rentGas addition (with rentGas set equal to gasLimit)
    // call this C5. */
    public Transaction(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, byte[] decodedData,byte chainId){
        this(BigIntegers.asUnsignedByteArray(nonce),
                gasPrice.toByteArray(),
                BigIntegers.asUnsignedByteArray(gasLimit),
                to != null ? Hex.decode(to) : null,
                BigIntegers.asUnsignedByteArray(amount),
                decodedData,
                chainId,
                BigIntegers.asUnsignedByteArray(gasLimit)); //rentgaslimit default set to gaslimit
    }
    // #mish: existed prior to rent: Similar to C5, except 'data' is String not byte[]. param types SBBBBSb
    // call this C6:   //rentgaslimit default set to gaslimit
    public Transaction(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, String data, byte chainId) {
        this(to, amount, nonce, gasPrice, gasLimit, data == null ? null : Hex.decode(data), chainId, gasLimit);
    }
    // C6Ext: Extend C6 to explicitly add rentGasLimit to arglist: param types SBBBBSbB
    public Transaction(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, String data, byte chainId, BigInteger rentGasLimit) {
        this(to, amount, nonce, gasPrice, gasLimit, data == null ? null : Hex.decode(data), chainId, rentGasLimit);
    }

    //Call this C7: existed prior to rent: param types SBBBBb no 'data'.  //rentgaslimit default set to gaslimit
    public Transaction(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, byte chainId) {
        this(to, amount, nonce, gasPrice, gasLimit, (byte[]) null, chainId, gasLimit);
    }
    //C7Ext: extend C7 to add rentGasLimit in arglist
    public Transaction(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, byte chainId, BigInteger rentGasLimit) {
        this(to, amount, nonce, gasPrice, gasLimit, (byte[]) null, chainId, rentGasLimit);
    }

    // #mish: existed prior to rent, param types BBBSBbb, so a reordering of C7. Plus mixed up names (value/amount).
    // Call this C8:  //rentgaslimit default set to gaslimit
    public Transaction(BigInteger nonce, BigInteger gasPrice, BigInteger gas, String to, BigInteger value, byte[] data, byte chainId) {
        this(nonce.toByteArray(), gasPrice.toByteArray(), gas.toByteArray(), Hex.decode(to), value.toByteArray(), data,
                chainId, gas.toByteArray());
    }
    //C8Ext: add rentGasLimit toarglist in C8
    public Transaction(BigInteger nonce, BigInteger gasPrice, BigInteger gas, String to, BigInteger value, byte[] data, byte chainId, BigInteger rentGasLimit) {
        this(nonce.toByteArray(), gasPrice.toByteArray(), gas.toByteArray(), Hex.decode(to), value.toByteArray(), data,
                chainId, rentGasLimit.toByteArray());
    }

    // #mish: Existed prior to rentGas: different param types. lllSlbb
    // call ths C9:  //rentgaslimit default set to gaslimit
    public Transaction(long nonce, long gasPrice, long gas, String to, long value, byte[] data, byte chainId) {
        this(BigInteger.valueOf(nonce).toByteArray(), BigInteger.valueOf(gasPrice).toByteArray(),
                BigInteger.valueOf(gas).toByteArray(), Hex.decode(to), BigInteger.valueOf(value).toByteArray(),
                data, chainId, BigInteger.valueOf(gas).toByteArray());        
    }
    //C9Ext: extend C9 to add rentGasLimit to arglist
    public Transaction(long nonce, long gasPrice, long gas, String to, long value, byte[] data, byte chainId, long rentGasLimit) {
        this(BigInteger.valueOf(nonce).toByteArray(), BigInteger.valueOf(gasPrice).toByteArray(),
                BigInteger.valueOf(gas).toByteArray(), Hex.decode(to), BigInteger.valueOf(value).toByteArray(),
                data, chainId, BigInteger.valueOf(rentGasLimit).toByteArray());        
    }

    // now the methods
    
    //just what it says..
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

        long nonZeroes = this.nonZeroDataBytes();
        long zeroVals = ListArrayUtil.getLength(this.getData()) - nonZeroes;
        // Base cost (e.g. 53K contract creation, 21K reg) + data cost (e.g. 68 per non-0 data byte, 4 for 0)
        return (this.isContractCreation() ? GasCost.TRANSACTION_CREATE_CONTRACT : GasCost.TRANSACTION) + zeroVals * GasCost.TX_ZERO_DATA + nonZeroes * GasCost.TX_NO_ZERO_DATA;
    }

    // alias for validate
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
        if (rentGasLimit.length > DATAWORD_LENGTH) {
            throw new RuntimeException("Rent Gas Limit is not valid");
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
            byte[] plainMsg = this.getEncoded();
            this.hash = new Keccak256(HashUtil.keccak256(plainMsg));
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

    public byte[] getRentGasLimit() {
        return rentGasLimit;
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

    public void sign(byte[] privKeyBytes) throws MissingPrivateKeyException {
        byte[] raw = this.getRawHash().getBytes();
        ECKey key = ECKey.fromPrivate(privKeyBytes).decompress();
        this.signature = key.sign(raw);
        this.rlpEncoding = null;
        this.hash = null;
        this.sender = null;
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

    private long nonZeroDataBytes() {
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
        byte[] raw = getRawHash().getBytes();
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
            ECKey key = ECKey.signatureToKey(getRawHash().getBytes(), getSignature());
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

    public synchronized RskAddress getSender(SignatureCache signatureCache) {
        if (sender != null) {
            return sender;
        }

        sender = signatureCache.getSender(this);

        return sender;
    }

    public byte getChainId() {
        return chainId;
    }

    @Override
    public String toString() {
        return "TransactionData [" + "hash=" + ByteUtil.toHexString(getHash().getBytes()) +
                "  nonce=" + ByteUtil.toHexString(nonce) +
                ", gasPrice=" + gasPrice.toString() +
                ", gasLimit=" + ByteUtil.toHexString(gasLimit) +
                ", rentGasLimit=" + ByteUtil.toHexString(rentGasLimit) +
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
            if (chainId == 0) {
                this.rawRlpEncoding = encode(null, null, null);
            } else {
                byte[] v = RLP.encodeByte(chainId);
                byte[] r = RLP.encodeElement(EMPTY_BYTE_ARRAY);
                byte[] s = RLP.encodeElement(EMPTY_BYTE_ARRAY);

                this.rawRlpEncoding = encode(v, r, s);
            }
        }

        return ByteUtil.cloneBytes(this.rawRlpEncoding);
    }
    // with signature data
    public byte[] getEncoded() {
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

            this.rlpEncoding = encode(v, r, s);
        }

        return ByteUtil.cloneBytes(this.rlpEncoding);
    }

    private byte[] encode(byte[] v, byte[] r, byte[] s) {
        // parse null as 0 for nonce
        byte[] toEncodeNonce;
        if (this.nonce == null || this.nonce.length == 1 && this.nonce[0] == 0) {
            toEncodeNonce = RLP.encodeElement(null);
        } else {
            toEncodeNonce = RLP.encodeElement(this.nonce);
        }
        byte[] toEncodeGasPrice = RLP.encodeCoinNonNullZero(this.gasPrice);
        byte[] toEncodeGasLimit = RLP.encodeElement(this.gasLimit);
        byte[] toEncodeRentGasLimit = RLP.encodeElement(this.rentGasLimit);
        byte[] toEncodeReceiveAddress = RLP.encodeRskAddress(this.receiveAddress);
        byte[] toEncodeValue = RLP.encodeCoinNullZero(this.value);
        byte[] toEncodeData = RLP.encodeElement(this.data);

        if (v == null && r == null && s == null) {
            return RLP.encodeList(toEncodeNonce, toEncodeGasPrice, toEncodeGasLimit,
                    toEncodeReceiveAddress, toEncodeValue, toEncodeData, toEncodeRentGasLimit);
        }

        return RLP.encodeList(toEncodeNonce, toEncodeGasPrice, toEncodeGasLimit,
                toEncodeReceiveAddress, toEncodeValue, toEncodeData, v, r, s, toEncodeRentGasLimit);
    }

    public BigInteger getGasLimitAsInteger() {
        return (this.getGasLimit() == null) ? null : BigIntegers.fromUnsignedByteArray(this.getGasLimit());
    }

    public BigInteger getRentGasLimitAsInteger() {
        return (this.getRentGasLimit() == null) ? null : BigIntegers.fromUnsignedByteArray(this.getRentGasLimit());
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
