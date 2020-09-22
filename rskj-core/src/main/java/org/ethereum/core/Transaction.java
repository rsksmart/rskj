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
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.crypto.ECKey.MissingPrivateKeyException;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.Secp256k1;
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
 * 
 * #mish: for storage rent implementation review
 * - treat transaction gaslimit as a combined limit for gas used for execution and to pay rent 
 *          (if rent is due) for all trie nodes touched by a TX (sender pays for all nodes when rent is due)
 * - internally, divide the gas limit equally between to separate budgets for execution and rent
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
     * to the state or transaction list consumes some gas. 
     * ***********************************
     * #mish: Storage rent implementation
     * Gas is also used to pay for storage rent. To work with existing wallets,
     * the same gaslimit field acts as a combined limit for execution and rent gas
     * this is divided equally to two separate execution and rent gaslimits
     * see methods getGasLimit() and getRentGasLimit()
     * */
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
    private byte[] rawRlpEncoding; //raw is without signature data
    private Keccak256 hash;
    private Keccak256 rawHash;

    protected Transaction(byte[] rawData) {
        RLPList transaction = RLP.decodeList(rawData);
        int txSize = transaction.size(); 
        if (txSize != 9) {
            throw new IllegalArgumentException("A transaction must have exactly 9 elements");
        }

        this.nonce = transaction.get(0).getRLPData();
        this.gasPrice = RLP.parseCoinNonNullZero(transaction.get(1).getRLPData());
        this.gasLimit = transaction.get(2).getRLPData();
        this.receiveAddress = RLP.parseRskAddress(transaction.get(3).getRLPData());
        this.value = RLP.parseCoinNullZero(transaction.get(4).getRLPData());
        this.data = transaction.get(5).getRLPData();
        // 6, 7, 8 are related to signature 
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

    /* CONTRACT CREATION tx #mish: no receiver Addr 
     * [ nonce, gasPrice, gasLimit, "", endowment, init, signature(v, r, s) ]
     * or SIMPLE SEND tx (need not have data)
     * [ nonce, gasPrice, gasLimit, receiveAddress, value, data, signature(v, r, s) ]
     */
    // #mish the full constructor. Call this C1
    public Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] valueRaw, byte[] data, byte chainId){
        this.nonce = ByteUtil.cloneBytes(nonce);
        this.gasPrice = RLP.parseCoinNonNullZero(ByteUtil.cloneBytes(gasPriceRaw));
        this.gasLimit = ByteUtil.cloneBytes(gasLimit);
        this.receiveAddress = RLP.parseRskAddress(ByteUtil.cloneBytes(receiveAddress));
        this.value = RLP.parseCoinNullZero(ByteUtil.cloneBytes(valueRaw));
        this.data = ByteUtil.cloneBytes(data);
        this.chainId = chainId;
        this.isLocalCall = false;
     } 
    // #mish: Call this C2. Compared to C1 it does not have chainID.
    public Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data, (byte) 0);
    }

    // #mish: Call this C3. a 9 elem byte array..
    public Transaction(byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] value, byte[] data, byte[] r, byte[] s, byte v) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data, (byte) 0);
        this.signature = ECDSASignature.fromComponents(r, s, v);
    }
    
    // This alt. version uses parameters "to" (instead of receiver addr) and "amount" (instead of value)
    // Call this C4.
    public Transaction(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, byte[] decodedData, byte chainId){
        this(BigIntegers.asUnsignedByteArray(nonce),
                gasPrice.toByteArray(),
                BigIntegers.asUnsignedByteArray(gasLimit),
                to != null ? Hex.decode(to) : null,
                BigIntegers.asUnsignedByteArray(amount),
                decodedData,
                chainId);
    }
    
    // #mish: existed prior to rent: Similar to C5, except 'data' is String not byte[]. param types SBBBBSb
    // call this C6:
    public Transaction(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, String data, byte chainId) {
        this(to, amount, nonce, gasPrice, gasLimit, data == null ? null : Hex.decode(data), chainId);
    }

    //Call this C7: existed prior to rent: param types SBBBBb no 'data'. 
    public Transaction(String to, BigInteger amount, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit, byte chainId) {
        this(to, amount, nonce, gasPrice, gasLimit, (byte[]) null, chainId);
    }

    // #mish: param types BBBSBbb, so a reordering of C7. Plus mixed up names (value/amount).
    // Call this C8: 
    public Transaction(BigInteger nonce, BigInteger gasPrice, BigInteger gas, String to, BigInteger value, byte[] data, byte chainId) {
        this(nonce.toByteArray(), gasPrice.toByteArray(), gas.toByteArray(), Hex.decode(to), value.toByteArray(), data,
                chainId);
    }

    // #mish: Different param types. lllSlbb
    // call ths C9: 
    public Transaction(long nonce, long gasPrice, long gas, String to, long value, byte[] data, byte chainId) {
        this(BigInteger.valueOf(nonce).toByteArray(), BigInteger.valueOf(gasPrice).toByteArray(),
                BigInteger.valueOf(gas).toByteArray(), Hex.decode(to), BigInteger.valueOf(value).toByteArray(),
                data, chainId);        
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
        if (gasPrice != null && gasPrice.getBytes().length > DATAWORD_LENGTH) {
            throw new RuntimeException("Gas Price is not valid");
        }
        if (value.getBytes().length > DATAWORD_LENGTH) {
            throw new RuntimeException("Value is not valid");
        }
        if (getSignature() != null) {
            if (BigIntegers.asUnsignedByteArray(signature.getR()).length > DATAWORD_LENGTH) {
                throw new RuntimeException("Signature R is not valid");
            }
            if (BigIntegers.asUnsignedByteArray(signature.getS()).length > DATAWORD_LENGTH) {
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

    /** #mish: Storage rent implementation
     * There is a single field in TX for overall gas budget (execution + rent)
      * We divide this overall gas budget of the TX by a factor to allocate to rent gas
      * then assign all remaining budget to execution gas
      * default value of factor is TX_GASBUDGET_DIVISOR = 2 (equal division)
    */
    // #mish: the conventional (EVM) execution gas limit for a Transaction
    public byte[] getGasLimit() {
        return getExecGasLimit();
    }
    // #mish: For future, explicit reference for execution gas limit
    public byte[] getExecGasLimit() {
        long gasBudget = GasCost.toGas(this.gasLimit); // convert byte to long
        long execGasBudget= gasBudget/GasCost.TX_GASBUDGET_DIVISOR;
        return BigInteger.valueOf(execGasBudget).toByteArray();
    }

    // #mish rentGas limit
    public byte[] getRentGasLimit() {
        long gasBudget = GasCost.toGas(this.gasLimit);
        long execGasBudget= gasBudget/GasCost.TX_GASBUDGET_DIVISOR;
        long rentGasBudget = gasBudget - execGasBudget;
        return BigInteger.valueOf(rentGasBudget).toByteArray();
    }
    
    public byte[] getData() {
        return data;
    }

    public ECDSASignature getSignature() {
        return signature;
    }

    public boolean acceptTransactionSignature(byte currentChainId) {
        ECDSASignature signature = getSignature();
        if (signature == null || !signature.validateComponents() || signature.getS().compareTo(SECP256K1N_HALF) >= 0) {
            return false;
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
     * @param signature
     * @return
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
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.KEY_RECOV_FROM_SIG);
        byte[] raw = getRawHash().getBytes();
        //We clear the 4th bit, the compress bit, in case a signature is using compress in true
        ECKey key = Secp256k1.getInstance().recoverFromSignature((signature.getV() - 27) & ~4, signature, raw, true);
        profiler.stop(metric);
        return key;
    }

    public synchronized RskAddress getSender() {
        if (sender != null) {
            return sender;
        }


        Metric metric = profiler.start(Profiler.PROFILING_TYPE.KEY_RECOV_FROM_SIG);
        try {
            ECKey key = Secp256k1.getInstance().signatureToKey(getRawHash().getBytes(), getSignature());
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
                ", gas=" + ByteUtil.toHexString(gasLimit) +
                ", receiveAddress=" + receiveAddress.toString() +
                ", value=" + value.toString() +
                ", data=" + ByteUtil.toHexString(data) +
                ", signatureV=" + (signature == null ? "" : signature.getV()) +
                ", signatureR=" + (signature == null ? "" : ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(signature.getR()))) +
                ", signatureS=" + (signature == null ? "" : ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(signature.getS()))) +
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
                v = RLP.encodeByte((byte) (chainId == 0 ? signature.getV() : (signature.getV() - LOWER_REAL_V) + (chainId * 2 + CHAIN_ID_INC)));
                r = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.getR()));
                s = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.getS()));
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
