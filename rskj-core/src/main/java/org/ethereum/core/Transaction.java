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
import co.rsk.metrics.profilers.MetricKind;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import co.rsk.panic.PanicProcessor;
import co.rsk.peg.BridgeUtils;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.exception.TransactionException;
import org.ethereum.core.transaction.parser.ParsedRawTransaction;
import org.ethereum.core.transaction.parser.RawTransactionEnvelopeParser;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.temp.ParsedRawTransactionAdapter;
import org.ethereum.cost.InitcodeCostCalculator;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.ECKey.MissingPrivateKeyException;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.crypto.signature.Secp256k1;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.security.SignatureException;

import java.util.Objects;
import java.util.function.Supplier;

import static co.rsk.util.ListArrayUtil.getLength;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;
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
    public static final byte CHAIN_ID_INC = 35;
    public static final byte LOWER_REAL_V = 27;



    /** Empty RLP list (0xc0) for default access list */
    private static final byte[] EMPTY_ACCESS_LIST_RLP = new byte[]{(byte) 0xc0};

    private final TransactionTypePrefix typePrefix;

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
    private final byte[] gasLimit;
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

    /** RSKIP546: Access list bytes (RLP-encoded) for Type 1 and Type 2 */
    private final byte[] accessListBytes;

    /**
     * RSKIP-546: EIP-1559 fee fields for <em>standard</em> Type 2 only ({@code null} for legacy, Type 1, Type 3/4,
     * and RSK-namespace Type 2). Effective gas price is {@code min(maxPriorityFeePerGas, maxFeePerGas)}.
     */
    private final Coin maxPriorityFeePerGas;
    private final Coin maxFeePerGas;

    protected Transaction(byte[] rawData) {
        this(RawTransactionEnvelopeParser.parse(rawData), false);
    }

    public Transaction(CallArguments argsParam, Supplier<String> nonceSupplier, byte defaultChainId){ //temp
        this(RawTransactionEnvelopeParser.parse(argsParam, nonceSupplier, defaultChainId), false);
    }

    //TEMPORAL
    private Transaction(ParsedRawTransaction args, boolean isLocalCall) {
        this(new ParsedRawTransactionAdapter(args), isLocalCall);
    }
    //TEMPORAL
    private Transaction(ParsedRawTransactionAdapter args, boolean isLocalCall) {
        this(
                args.nonce(),
                args.effectiveGasPrice(),
                args.gasLimit(),
                args.receiveAddress(),
                args.value(),
                args.data(),
                args.chainId(),
                isLocalCall,
                args.typePrefix(),
                args.accessListBytes(),
                args.maxPriorityFeePerGas(),
                args.maxFeePerGas()
        );
        if (null == this.getGasLimit() || null == this.getGasPrice() || null == this.getValue()) {
            throw invalidParamError("Missing parameter, gasPrice, gas or value");
        }

        this.signature = args.signature();
    }

    /* creation contract tx
     * [ nonce, gasPrice, gasLimit, "", endowment, init, signature(v, r, s) ]
     * or simple send tx
     * [ nonce, gasPrice, gasLimit, receiveAddress, value, data, signature(v, r, s) ]
     */
    protected Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data, (byte) 0, TransactionType.LEGACY);
    }

    protected Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data, TransactionType type) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data, (byte) 0, type);
    }

    protected Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] valueRaw, byte[] data,
                          byte chainId, TransactionType type) {
        this(
                nonce,
                RLP.parseCoinNonNullZero(ByteUtil.cloneBytes(gasPriceRaw)),
                gasLimit,
                RLP.parseRskAddress(ByteUtil.cloneBytes(receiveAddress)),
                RLP.parseCoinNullZero(ByteUtil.cloneBytes(valueRaw)),
                data,
                chainId,
                false,
                TransactionTypePrefix.typed(type)
        );
    }

    /** Constructor with optional RSK namespace subtype. */
    protected Transaction(byte[] nonce, Coin gasPriceRaw, byte[] gasLimit, RskAddress receiveAddress, Coin valueRaw, byte[] data,
                          byte chainId, final boolean localCall, TransactionType type, Byte rskSubtype) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, valueRaw, data, chainId, localCall,
                TransactionTypePrefix.of(type, rskSubtype));
    }

    /** Canonical constructor used by all overloads. */
    public Transaction(byte[] nonce, Coin gasPriceRaw, byte[] gasLimit, RskAddress receiveAddress, Coin valueRaw, byte[] data,
                       byte chainId, final boolean localCall, TransactionTypePrefix typePrefix) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, valueRaw, data, chainId, localCall, typePrefix,
                typePrefix.type() == TransactionType.TYPE_1
                        || (typePrefix.type() == TransactionType.TYPE_2 && !typePrefix.isRskNamespace())
                        ? EMPTY_ACCESS_LIST_RLP : null,
                typePrefix.type() == TransactionType.TYPE_2 && !typePrefix.isRskNamespace() ? gasPriceRaw : null,
                typePrefix.type() == TransactionType.TYPE_2 && !typePrefix.isRskNamespace() ? gasPriceRaw : null);
    }

    /** Canonical constructor with optional access list for Type 1 and Type 2 (max fee fields null). */
    public Transaction(byte[] nonce, Coin gasPriceRaw, byte[] gasLimit, RskAddress receiveAddress, Coin valueRaw, byte[] data,
                       byte chainId, final boolean localCall, TransactionTypePrefix typePrefix, byte[] accessListBytes) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, valueRaw, data, chainId, localCall, typePrefix,
                accessListBytes, null, null);
    }

    /**
     * Full canonical constructor: access list RLP and optional Type 2 max fees (RSK namespace uses null max fees).
     */
    public Transaction(byte[] nonce, Coin gasPriceRaw, byte[] gasLimit, RskAddress receiveAddress, Coin valueRaw, byte[] data,
                          byte chainId, final boolean localCall, TransactionTypePrefix typePrefix, byte[] accessListBytes,
                          @Nullable Coin maxPriorityFeePerGas, @Nullable Coin maxFeePerGas) {
        this.nonce = ByteUtil.cloneBytes(nonce);
        this.gasPrice = gasPriceRaw;
        this.gasLimit = ByteUtil.cloneBytes(gasLimit);
        this.receiveAddress = receiveAddress;
        this.value = valueRaw;
       // this.data = ByteUtil.cloneBytes(data);
        this.data = data;
        this.chainId = chainId;
        this.isLocalCall = localCall;
        this.typePrefix = typePrefix;
        this.accessListBytes = accessListBytes == null ? null : accessListBytes.clone();
        this.maxPriorityFeePerGas = maxPriorityFeePerGas;
        this.maxFeePerGas = maxFeePerGas;
    }

    public static TransactionBuilder builder() {
        return new TransactionBuilder();
    }

    public Transaction toImmutableTransaction() {
        return new ImmutableTransaction(this.getEncoded());
    }

    // There was a method called NEW_getTransactionCost that implemented this alternative solution:
    // "return (this.isContractCreation() ? GasCost.TRANSACTION_CREATE_CONTRACT : GasCost.TRANSACTION)
    //         + zeroVals * GasCost.TX_ZERO_DATA + nonZeroes * GasCost.TX_NO_ZERO_DATA;"
    public long transactionCost(Constants constants, ActivationConfig.ForBlock activations, SignatureCache signatureCache) {
        // Federators txs to the bridge are free during system setup
        if (BridgeUtils.isFreeBridgeTx(this, constants, activations, signatureCache)) {
            return 0;
        }

        long nonZeroes = this.nonZeroDataBytes();
        long zeroVals = getLength(this.getData()) - nonZeroes;

        long transactionCost = this.isContractCreation()
                ? GasCost.add(GasCost.TRANSACTION_CREATE_CONTRACT, InitcodeCostCalculator.getInstance().calculateCost(getLength(this.getData()), activations))
                : GasCost.TRANSACTION;

        long txNonZeroDataCost = getTxNonZeroDataCost(activations);

        long accessListGas = 0;
        if (activations.isActive(ConsensusRule.RSKIP546)
                && isType1OrStandardType2()
                && accessListBytes != null && accessListBytes.length > 1) {
            // RSKIP-546: 80 gas/byte of access-list RLP; only standard Type 1 / EIP-1559 Type 2 (not RSK-namespace 0x02||subtype).
            // length > 1: empty list (0xc0) is 1 byte and should not be charged.
            accessListGas = accessListBytes.length * GasCost.ACCESS_LIST_GAS_PER_BYTE;
        }

        return transactionCost + zeroVals * GasCost.TX_ZERO_DATA + nonZeroes * txNonZeroDataCost + accessListGas;
    }


    public boolean isTypedTransactionNotAllowed(ActivationConfig.ForBlock activations) {
        if (!this.typePrefix.isTyped()) {
            return false;
        }
        if (!activations.isActive(ConsensusRule.RSKIP543)) {
            return true;
        }
        // RSKIP-546 gates Type 1 and Type 2 specifically
        TransactionType type = typePrefix.type();
        return (type == TransactionType.TYPE_1 || type == TransactionType.TYPE_2)
                && !activations.isActive(ConsensusRule.RSKIP546);
    }

    public boolean isInitCodeSizeInvalidForTx(ActivationConfig.ForBlock activations) {
        int initCodeSize = getLength(this.getData());

        return this.isContractCreation()
                && activations.isActive(ConsensusRule.RSKIP438)
                && initCodeSize > Constants.getMaxInitCodeSize();
    }

    private static long getTxNonZeroDataCost(ActivationConfig.ForBlock activations) {
        return activations.isActive(ConsensusRule.RSKIP400) ? GasCost.TX_NO_ZERO_DATA_EIP2028 : GasCost.TX_NO_ZERO_DATA;
    }

    public void verify(SignatureCache signatureCache) {
        validate(signatureCache);
    }

    private void validate(SignatureCache signatureCache) {
        if (getNonce().length > DATAWORD_LENGTH) {
            throw new TransactionException("Nonce is not valid");
        }
        if (receiveAddress != null && receiveAddress.getBytes().length != 0 && receiveAddress.getBytes().length != Constants.getMaxAddressByteLength()) {
            throw new TransactionException("Receive address is not valid");
        }
        if (gasLimit.length > DATAWORD_LENGTH) {
            throw new TransactionException("Gas Limit is not valid");
        }
        if (gasPrice != null && gasPrice.getBytes().length > DATAWORD_LENGTH) {
            throw new TransactionException("Gas Price is not valid");
        }
        if (value.getBytes().length > DATAWORD_LENGTH) {
            throw new TransactionException("Value is not valid");
        }
        if (getSignature() != null) {
            if (BigIntegers.asUnsignedByteArray(signature.getR()).length > DATAWORD_LENGTH) {
                throw new TransactionException("Signature R is not valid");
            }
            if (BigIntegers.asUnsignedByteArray(signature.getS()).length > DATAWORD_LENGTH) {
                throw new TransactionException("Signature S is not valid");
            }
            RskAddress senderAddress = getSender(signatureCache);
            if (senderAddress.getBytes() != null && senderAddress.getBytes().length != Constants.getMaxAddressByteLength()) {
                throw new TransactionException("Sender is not valid");
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
            // For Type 1: getEncodedRaw() returns 0x01 || rlp([chainId, nonce, gasPrice, gasLimit, to, value, data, accessList])
            // For legacy: getEncodedRaw() returns rlp([nonce, gasPrice, gasLimit, to, value, data{, chainId, 0, 0}])
            this.rawHash = new Keccak256(HashUtil.keccak256(this.getEncodedRaw()));
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
        if (isStandardType2() && maxPriorityFeePerGas != null && maxFeePerGas != null) {
            return maxPriorityFeePerGas.compareTo(maxFeePerGas) <= 0 ? maxPriorityFeePerGas : maxFeePerGas;
        }
        if (gasPrice == null) {
            return Coin.ZERO;
        }

        return gasPrice;
    }

    @Nullable
    public Coin getMaxPriorityFeePerGas() {
        return maxPriorityFeePerGas;
    }

    @Nullable
    public Coin getMaxFeePerGas() {
        return maxFeePerGas;
    }

    /** Standard EIP-1559 Type 2 ({@code 0x02} + 12-field RLP), not RSK namespace ({@code 0x02} || subtype || legacy). */
    private boolean isStandardType2() {
        return typePrefix.type() == TransactionType.TYPE_2 && !typePrefix.isRskNamespace();
    }

    /** Types that use RSKIP-546 access-list field and intrinsic gas for that field (excludes RSK-namespace Type 2). */
    private boolean isType1OrStandardType2() {
        return typePrefix.type() == TransactionType.TYPE_1 || isStandardType2();
    }

    public byte[] getGasLimit() {
        return gasLimit == null ? null : gasLimit.clone();
    }

    public byte[] getData() {
        return data == null ? null : data.clone();
    }

    public ECDSASignature getSignature() {
        return signature;
    }

    public boolean acceptTransactionSignature(byte currentChainId) {
        if (signature == null || !signature.validateComponents() || signature.getS().compareTo(SECP256K1N_HALF) >= 0) {
            return false;
        }

        if (typePrefix.type() == TransactionType.TYPE_1 || isStandardType2()) {
            // EIP-2930 / EIP-1559 mandate chainId; chainId == 0 is not valid
            return this.chainId != 0 && this.chainId == currentChainId;
        }

        return this.getChainId() == 0 || this.getChainId() == currentChainId;
    }

    public void sign(byte[] privKeyBytes) throws MissingPrivateKeyException {
        byte[] raw = this.getRawHash().getBytes();
        ECKey key = ECKey.fromPrivate(privKeyBytes).decompress();
        this.signature = ECDSASignature.fromSignature(key.sign(raw));
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

    /**
     * Only for compatibility until we could finally remove old {@link org.ethereum.crypto.ECKey.ECDSASignature}.
     *
     * @param signature to be set
     */
    public void setSignature(ECKey.ECDSASignature signature) {
        this.signature = ECDSASignature.fromSignature(signature);
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
        Metric metric = profiler.start(MetricKind.KEY_RECOV_FROM_SIG);
        byte[] raw = getRawHash().getBytes();
        //We clear the 4th bit, the compress bit, in case a signature is using compress in true
        ECKey key = Secp256k1.getInstance().recoverFromSignature((signature.getV() - 27) & ~4, signature, raw, true);
        profiler.stop(metric);
        return key;
    }

    /**
     * Returns sender's Address
     * <p>
     * Usage of this method should be avoided in favor of getSender(SignatureCache signatureCache)
     * as it tries to get the data from the cache first, improving performance.
     *
     * @return RskAddress the sender's Address
     */
    public synchronized RskAddress getSender() {
        if (sender != null) {
            return sender;
        }

        Metric metric = profiler.start(MetricKind.KEY_RECOV_FROM_SIG);
        try {
            ECKey key = Secp256k1.getInstance().signatureToKey(getRawHash().getBytes(), getSignature());
            sender = new RskAddress(key.getAddress());
        } catch (SignatureException e) {
            logger.error(e.getMessage(), e);
            panicProcessor.panic("transaction", e.getMessage());
            sender = RskAddress.nullAddress();
        } finally {
            profiler.stop(metric);
        }

        return sender;
    }

    public synchronized RskAddress getSender(SignatureCache signatureCache) {
        if (sender != null) {
            return sender;
        }

        sender = signatureCache.getSender(this);

        if (sender != null) {
            return sender;
        }

        return getSender();
    }

    public byte getChainId() {
        return chainId;
    }

    @Nullable
    public byte[] getAccessListBytes() {
        return accessListBytes == null ? null : accessListBytes.clone();
    }

    public TransactionTypePrefix getTypePrefix() {
        return typePrefix;
    }

    public TransactionType getType() {
        return typePrefix.type();
    }
    
    public boolean isRskNamespaceTransaction() {
        return typePrefix.isRskNamespace();
    }

    public byte getRskSubtype() {
        return typePrefix.subtype();
    }

    public String getFullTypeString() {
        return typePrefix.toFullString();
    }

    public String getTypeAsHex() {
        return typePrefix.toRpcString();
    }

    public byte getEncodedV() {
        if (this.signature == null) {
            return 0;
        }
        if (typePrefix.type().isTyped()) {
            // EIP-2718 typed transactions use yParity (0 or 1) instead of EIP-155 V
            return (byte) (this.signature.getV() - LOWER_REAL_V);
        }
        return this.chainId == 0
                ? this.signature.getV()
                : (byte) (this.signature.getV() - LOWER_REAL_V + CHAIN_ID_INC + this.chainId * 2);
    }

    public static final String ERR_INVALID_CHAIN_ID = "Invalid chainId: ";

    public  void checkInvalidChain(Constants constants, String chainId) {
        if (!acceptTransactionSignature(constants.getChainId())) {
            throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_CHAIN_ID + chainId);
        }
    }

    @Override
    public String toString() {
        return "TransactionData [" + "hash=" + ByteUtil.toHexStringOrEmpty(getHash().getBytes()) +
                ", type=" + typePrefix +
                ", nonce=" + ByteUtil.toHexStringOrEmpty(nonce) +
                ", gasPrice=" + gasPrice +
                ", gas=" + ByteUtil.toHexStringOrEmpty(gasLimit) +
                ", receiveAddress=" + receiveAddress +
                ", value=" + value +
                ", data=" + ByteUtil.toHexStringOrEmpty(data) +
                (accessListBytes != null ? ", accessListLen=" + accessListBytes.length : "") +
                (maxPriorityFeePerGas != null ? ", maxPriorityFeePerGas=" + maxPriorityFeePerGas : "") +
                (maxFeePerGas != null ? ", maxFeePerGas=" + maxFeePerGas : "") +
                ", signatureV=" + (signature == null ? "" : signature.getV()) +
                ", signatureR=" + (signature == null ? "" : ByteUtil.toHexStringOrEmpty(BigIntegers.asUnsignedByteArray(signature.getR()))) +
                ", signatureS=" + (signature == null ? "" : ByteUtil.toHexStringOrEmpty(BigIntegers.asUnsignedByteArray(signature.getS()))) +
                "]";
    }

    public byte[] getEncodedRaw() {
        if (this.rawRlpEncoding == null) {
            byte[] txData;
            if (typePrefix.type() == TransactionType.TYPE_1) {
                txData = encodeType1UnsignedPayload();
            } else if (isStandardType2()) {
                txData = encodeType2UnsignedPayload();
            } else {
                // Legacy
                if (chainId == 0) {
                    txData = encode(null, null, null);
                } else {
                    byte[] v = RLP.encodeByte(chainId);
                    byte[] r = RLP.encodeElement(EMPTY_BYTE_ARRAY);
                    byte[] s = RLP.encodeElement(EMPTY_BYTE_ARRAY);
                    txData = encode(v, r, s);
                }
            }
            this.rawRlpEncoding = prependTypePrefix(txData);
        }

        return ByteUtil.cloneBytes(this.rawRlpEncoding);
    }

    public byte[] getEncoded() {
        return ByteUtil.cloneBytes(rlpEncode());
    }

    public long getSize() {
        return rlpEncode().length;
    }

    /** RLP-encoded nonce element (null or single zero byte → empty scalar). */
    private byte[] toEncodeNonce() {
        if (nonce == null || (nonce.length == 1 && nonce[0] == 0)) {
            return RLP.encodeElement(null);
        }
        return RLP.encodeElement(nonce);
    }

    /** RLP access list bytes for typed txs, or empty list {@link #EMPTY_ACCESS_LIST_RLP} when absent. */
    private byte[] toEncodeAccessList() {
        return accessListBytes != null ? accessListBytes : EMPTY_ACCESS_LIST_RLP;
    }

    private byte[] encode(byte[] v, byte[] r, byte[] s) {
        byte[] toEncodeNonce = toEncodeNonce();
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
    }

    /** Encodes the 8 shared Type 1 fields: [chainId, nonce, gasPrice, gasLimit, to, value, data, accessList] */
    private byte[][] encodeType1Fields() {
        return new byte[][]{
                RLP.encodeByte(chainId),
                toEncodeNonce(),
                RLP.encodeCoinNonNullZero(gasPrice),
                RLP.encodeElement(gasLimit),
                RLP.encodeRskAddress(receiveAddress),
                RLP.encodeCoinNullZero(value),
                RLP.encodeElement(data),
                toEncodeAccessList()
        };
    }

    /** Type 1 unsigned payload for signing: rlp([chainId, nonce, gasPrice, gasLimit, to, value, data, accessList]) */
    private byte[] encodeType1UnsignedPayload() {
        return RLP.encodeList(encodeType1Fields());
    }

    private byte[][] encodeType2Fields() {
        if (maxPriorityFeePerGas == null || maxFeePerGas == null) {
            throw new IllegalStateException("Standard Type 2 transaction requires maxPriorityFeePerGas and maxFeePerGas");
        }
        return new byte[][]{
                RLP.encodeByte(chainId),
                toEncodeNonce(),
                RLP.encodeCoinNonNullZero(maxPriorityFeePerGas),
                RLP.encodeCoinNonNullZero(maxFeePerGas),
                RLP.encodeElement(gasLimit),
                RLP.encodeRskAddress(receiveAddress),
                RLP.encodeCoinNullZero(value),
                RLP.encodeElement(data),
                toEncodeAccessList()
        };
    }

    /** Type 2 unsigned payload for signing (RSKIP-546). */
    private byte[] encodeType2UnsignedPayload() {
        return RLP.encodeList(encodeType2Fields());
    }

    /** Type 2 full encoding: 12 elements with signature */
    private byte[] encodeType2() {
        byte[][] fields = encodeType2Fields();
        byte[] yParity = signature != null
                ? RLP.encodeByte((byte) (signature.getV() - LOWER_REAL_V))
                : RLP.encodeByte((byte) 0);
        byte[] r = signature != null
                ? RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.getR()))
                : RLP.encodeElement(EMPTY_BYTE_ARRAY);
        byte[] s = signature != null
                ? RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.getS()))
                : RLP.encodeElement(EMPTY_BYTE_ARRAY);
        byte[][] all = new byte[fields.length + 3][];
        System.arraycopy(fields, 0, all, 0, fields.length);
        all[fields.length] = yParity;
        all[fields.length + 1] = r;
        all[fields.length + 2] = s;
        return RLP.encodeList(all);
    }

    /** Type 1 full encoding: 11 elements with signature */
    private byte[] encodeType1() {
        byte[][] fields = encodeType1Fields();
        byte[] yParity = signature != null
                ? RLP.encodeByte((byte) (signature.getV() - LOWER_REAL_V))
                : RLP.encodeByte((byte) 0);
        byte[] r = signature != null
                ? RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.getR()))
                : RLP.encodeElement(EMPTY_BYTE_ARRAY);
        byte[] s = signature != null
                ? RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.getS()))
                : RLP.encodeElement(EMPTY_BYTE_ARRAY);
        byte[][] all = new byte[fields.length + 3][];
        System.arraycopy(fields, 0, all, 0, fields.length);
        all[fields.length] = yParity;
        all[fields.length + 1] = r;
        all[fields.length + 2] = s;
        return RLP.encodeList(all);
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


    private static byte[] nullToZeroArray(byte[] data) {
        return data == null ? ZERO_BYTE_ARRAY.clone() : data;
    }

    public boolean isLocalCallTransaction() {
        return isLocalCall;
    }

    public void setLocalCallTransaction(boolean isLocalCall) {
        this.isLocalCall = isLocalCall;
    }

    /**
     * <p>
     * Returns true if the current transaction is a Remasc transaction by checking if the transaction position is the
     * last one, if receive address is equals to #PrecompiledContracts.REMASC_ADDR and finally if there is a signature
     * and data having value, gas limit and gas price equals to 0, otherwise returns false.
     * </p>
     * <p>
     * Use this method when you don't know if the transaction is a remasc transaction from the parameters given above.
     * </p>
     *
     * @param txPosition transaction position
     * @param txsSize    transaction size
     * @return true if the current transaction is a Remasc transaction, otherwise returns false.
     */
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
        byte[] currentData = getData();

        if ((null != currentData && currentData.length != 0) || null != getSignature()) {
            return false;
        }

        return Coin.ZERO.equals(getValue()) &&
                BigInteger.ZERO.equals(new BigInteger(1, getGasLimit())) &&
                Coin.ZERO.equals(getGasPrice());
    }

    // returning a mutable object from a private method is not that bad and is convenient this time
    @java.lang.SuppressWarnings("squid:S2384")
    private byte[] rlpEncode() {
        if (this.rlpEncoding == null) {
            byte[] txData;
            if (typePrefix.type() == TransactionType.TYPE_1) {
                txData = encodeType1();
            } else if (isStandardType2()) {
                txData = encodeType2();
            } else {
                byte[] v;
                byte[] r;
                byte[] s;
                if (this.signature != null) {
                    v = RLP.encodeByte((byte) (chainId == 0 ? signature.getV() : (signature.getV() - LOWER_REAL_V) + (chainId * 2 + CHAIN_ID_INC)));
                    r = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.getR()));
                    s = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.getS()));
                } else {
                    v = chainId == 0 ? RLP.encodeElement(EMPTY_BYTE_ARRAY) : RLP.encodeByte(chainId);
                    r = RLP.encodeElement(EMPTY_BYTE_ARRAY);
                    s = RLP.encodeElement(EMPTY_BYTE_ARRAY);
                }
                txData = encode(v, r, s);
            }
            this.rlpEncoding = prependTypePrefix(txData);
        }

        return this.rlpEncoding;
    }

    /** Prepends the RSKIP543 type prefix when present. */
    private byte[] prependTypePrefix(byte[] txData) {
        byte[] prefix = typePrefix.toBytes();
        return prefix.length == 0 ? txData : ByteUtil.merge(prefix, txData);
    }

}
