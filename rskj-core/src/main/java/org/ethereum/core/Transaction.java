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
import java.nio.ByteBuffer;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
    private final int version;
    private final int ownerCount;
    /* a counter used to make sure each transaction can only be processed once */
    private final List<byte[]> nonce;
    private final Coin value;
    /* the address of the destination account
     * In creation transaction the receive address is - 0 */
    private final RskAddress receiveAddress;
    private final Coin gasPrice;
    /* An unlimited size byte array specifying
     * input [data] of the message call or
     * Initialization code for a new contract */
    private final byte[] data;
    private final byte chainId;
    /* List of  */
    protected List<RskAddress> senders;
    protected RskAddress sender;
    /* whether this is a local call transaction */
    private boolean isLocalCall;
    /* the amount of "gas" to allow for the computation.
     * Gas is the fuel of the computational engine.
     * Every computational step taken and every byte added
     * to the state or transaction list consumes some gas. */
    private byte[] gasLimit;
    /* the elliptic curve signatures
     * (including public key recovery bits)
     * in format 0 there can only be one
     * in format 1 more than one means a multi-signed transaction*/
    private List<ECDSASignature> signatures;
    private byte[] rlpEncoding;
    private byte[] rawRlpEncoding;
    private Keccak256 hash;
    private Keccak256 rawHash;

    protected Transaction(byte[] rawData) {
        List<RLPElement> transaction = RLP.decodeList(rawData);
        if (transaction.size() != 9) {
            throw new IllegalArgumentException("A transaction must have exactly 9 elements");
        }

        if (transaction.get(0) instanceof RLPList) {
            // Transaction format 1
            this.version = RLP.decodeInt(nullToZeroArray(((RLPList) transaction.get(0)).get(0).getRLPData()), 0);
            if (this.version != 1) {
                throw new TransactionException("Transaction has unknown version");
            }
            if (transaction.get(1) instanceof RLPList) {
                List<byte[]> nonce = new ArrayList<>();
                for (RLPElement element : (RLPList) transaction.get(1)) {
                    nonce.add(nullToZeroArray(element.getRLPData()));
                }
                this.nonce = Collections.unmodifiableList(nonce);
            } else {
                this.nonce = Collections.singletonList(nullToZeroArray(transaction.get(1).getRLPData()));
            }
            this.gasPrice = RLP.parseCoinNonNullZero(transaction.get(2).getRLPData());
            this.gasLimit = transaction.get(3).getRLPData();
            this.receiveAddress = RLP.parseRskAddress(transaction.get(4).getRLPData());
            this.value = RLP.parseCoinNullZero(transaction.get(5).getRLPData());
            this.data = transaction.get(6).getRLPData();
            this.ownerCount = RLP.decodeInt(nullToZeroArray(transaction.get(7).getRLPData()), 0);

            if (transaction.get(8) instanceof RLPList) {
                RLPList signaturesField = (RLPList) transaction.get(8);
                if (signaturesField.isEmpty() || signaturesField.get(0) instanceof RLPList) {
                    List<ECDSASignature> signatures = new ArrayList<>();
                    byte chainId = 0;
                    boolean chainIdUnset = true;
                    for (RLPElement e : signaturesField) {
                        if (e instanceof RLPList) {
                            RLPList element = (RLPList) e;
                            if (element.size() != 3) {
                                throw new IllegalArgumentException("Signature field must be made up of 3 elements");
                            }
                            byte[] vData = element.get(0).getRLPData();
                            byte[] r = element.get(1).getRLPData();
                            byte[] s = element.get(2).getRLPData();
                            ECDSASignature signature = parseSignature(vData, r, s);
                            if (signature != null) {
                                if (chainIdUnset) {
                                    chainIdUnset = false;
                                    chainId = extractChainIdFromV(vData[0]);
                                }
                                signatures.add(signature);
                            }
                        } else {
                            throw new IllegalArgumentException("Signature field must be a list");
                        }
                    }
                    if (signatures.isEmpty()) {
                        this.chainId = 0;
                        logger.trace("RLP encoded tx is not signed!");
                    } else {
                        this.chainId = chainId;
                    }
                    this.signatures = Collections.unmodifiableList(signatures);
                } else {
                    if (signaturesField.size() != 3) {
                        throw new IllegalArgumentException("Signature field must be made up of 3 elements");
                    }
                    byte[] vData = signaturesField.get(0).getRLPData();
                    byte[] r = signaturesField.get(1).getRLPData();
                    byte[] s = signaturesField.get(2).getRLPData();
                    ECDSASignature signature = parseSignature(vData, r, s);
                    if (signature != null) {
                        this.chainId = extractChainIdFromV(vData[0]);
                        this.signatures = Collections.singletonList(signature);
                    } else {
                        this.chainId = 0;
                        logger.trace("RLP encoded tx is not signed!");
                        this.signatures = Collections.emptyList();
                    }
                }
            } else {
                throw new IllegalArgumentException("Invalid signatures field");
            }
        } else {
            // Transaction format 0
            this.version = 0;
            this.nonce = Collections.singletonList(nullToZeroArray(transaction.get(0).getRLPData()));
            this.gasPrice = RLP.parseCoinNonNullZero(transaction.get(1).getRLPData());
            this.gasLimit = transaction.get(2).getRLPData();
            this.receiveAddress = RLP.parseRskAddress(transaction.get(3).getRLPData());
            this.value = RLP.parseCoinNullZero(transaction.get(4).getRLPData());
            this.data = transaction.get(5).getRLPData();

            // only parse signature in case tx is signed
            byte[] vData = transaction.get(6).getRLPData();
            byte[] r = transaction.get(7).getRLPData();
            byte[] s = transaction.get(8).getRLPData();
            ECDSASignature signature = parseSignature(vData, r, s);
            if (signature != null) {
                byte v = vData[0];
                this.chainId = extractChainIdFromV(v);
                this.signatures = Collections.singletonList(signature);
            } else {
                this.chainId = 0;
                logger.trace("RLP encoded tx is not signed!");
                this.signatures = Collections.emptyList();
            }
            this.ownerCount = 1;
        }
    }

    /* creation contract tx
     * [ nonce, gasPrice, gasLimit, "", endowment, init, signature(v, r, s) ]
     * or simple send tx
     * [ nonce, gasPrice, gasLimit, receiveAddress, value, data, signature(v, r, s) ]
     */
    public Transaction(
            byte[] nonce,
            byte[] gasPriceRaw,
            byte[] gasLimit,
            byte[] receiveAddress,
            byte[] value,
            byte[] data) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data, (byte) 0);
    }

    public Transaction(
            byte[] nonce,
            byte[] gasPriceRaw,
            byte[] gasLimit,
            byte[] receiveAddress,
            byte[] value,
            byte[] data,
            byte[] r,
            byte[] s,
            byte v) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data, (byte) 0);

        this.signatures = Collections.singletonList(ECDSASignature.fromComponents(r, s, v));
    }

    public Transaction(long nonce, long gasPrice, long gas, String to, long value, byte[] data, byte chainId) {
        this(1, nonce, gasPrice, gas, to, value, data, chainId);
    }

    public Transaction(
            int version,
            long nonce,
            long gasPrice,
            long gas,
            String to,
            long value,
            byte[] data,
            byte chainId) {
        this(version, BigInteger.valueOf(nonce).toByteArray(), BigInteger.valueOf(gasPrice).toByteArray(),
             BigInteger.valueOf(gas).toByteArray(), Hex.decode(to), BigInteger.valueOf(value).toByteArray(),
             data, chainId
        );
    }

    public Transaction(
            BigInteger nonce, BigInteger gasPrice, BigInteger gas, String to, BigInteger value, byte[] data,
            byte chainId) {
        this(
                nonce.toByteArray(),
                gasPrice.toByteArray(),
                gas.toByteArray(),
                Hex.decode(to),
                value.toByteArray(),
                data,
                chainId
        );
    }

    public Transaction(
            String to,
            BigInteger amount,
            BigInteger nonce,
            BigInteger gasPrice,
            BigInteger gasLimit,
            byte chainId) {
        this(to, amount, nonce, gasPrice, gasLimit, (byte[]) null, chainId);
    }

    public Transaction(
            String to,
            BigInteger amount,
            BigInteger nonce,
            BigInteger gasPrice,
            BigInteger gasLimit,
            String data,
            byte chainId) {
        this(to, amount, nonce, gasPrice, gasLimit, data == null ? null : Hex.decode(data), chainId);
    }

    public Transaction(
            String to,
            BigInteger amount,
            BigInteger nonce,
            BigInteger gasPrice,
            BigInteger gasLimit,
            byte[] decodedData,
            byte chainId) {
        this(
                BigIntegers.asUnsignedByteArray(nonce),
                gasPrice.toByteArray(),
                BigIntegers.asUnsignedByteArray(gasLimit),
                to != null ? Hex.decode(to) : null,
                BigIntegers.asUnsignedByteArray(amount),
                decodedData,
                chainId
        );
    }

    public Transaction(
            byte[] nonce, byte[] gasPriceRaw, byte[] gasLimit, byte[] receiveAddress, byte[] valueRaw, byte[] data,
            byte chainId) {
        this(1, nonce, gasPriceRaw, gasLimit, receiveAddress, valueRaw, data, chainId);
    }

    public Transaction(
            int version,
            byte[] nonce,
            byte[] gasPriceRaw,
            byte[] gasLimit,
            byte[] receiveAddress,
            byte[] valueRaw,
            byte[] data,
            byte chainId) {
        this.nonce = Collections.singletonList(ByteUtil.cloneBytes(nonce));
        this.gasPrice = RLP.parseCoinNonNullZero(ByteUtil.cloneBytes(gasPriceRaw));
        this.gasLimit = ByteUtil.cloneBytes(gasLimit);
        this.receiveAddress = RLP.parseRskAddress(ByteUtil.cloneBytes(receiveAddress));
        this.value = RLP.parseCoinNullZero(ByteUtil.cloneBytes(valueRaw));
        this.data = ByteUtil.cloneBytes(data);
        this.chainId = chainId;
        this.isLocalCall = false;
        this.version = version;
        this.ownerCount = 1;
        this.signatures = Collections.emptyList();
    }

    public Transaction(
            List<byte[]> nonce,
            byte[] gasPriceRaw,
            byte[] gasLimit,
            byte[] receiveAddress,
            byte[] value,
            byte[] data,
            int ownerCount) {
        this(nonce, gasPriceRaw, gasLimit, receiveAddress, value, data, ownerCount, (byte) 0);
    }

    public Transaction(
            List<byte[]> nonce,
            byte[] gasPriceRaw,
            byte[] gasLimit,
            byte[] receiveAddress,
            byte[] valueRaw,
            byte[] data,
            int ownerCount,
            byte chainId) {
        this.nonce = Collections.unmodifiableList(nonce);
        this.gasPrice = RLP.parseCoinNonNullZero(ByteUtil.cloneBytes(gasPriceRaw));
        this.gasLimit = ByteUtil.cloneBytes(gasLimit);
        this.receiveAddress = RLP.parseRskAddress(ByteUtil.cloneBytes(receiveAddress));
        this.value = RLP.parseCoinNullZero(ByteUtil.cloneBytes(valueRaw));
        this.data = ByteUtil.cloneBytes(data);
        this.chainId = chainId;
        this.isLocalCall = false;
        this.version = 1;
        this.ownerCount = ownerCount;
        this.signatures = Collections.emptyList();
    }

    public Transaction toImmutableTransaction() {
        return new ImmutableTransaction(this.getEncoded());
    }

    private ECDSASignature parseSignature(byte[] vData, byte[] r, byte[] s) {
        if (vData == null) {
            return null;
        }
        if (vData.length != 1) {
            throw new TransactionException("Signature V is invalid");
        }
        byte v = vData[0];
        return ECDSASignature.fromComponents(r, s, getRealV(v));
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

        return (this.isContractCreation() ? GasCost.TRANSACTION_CREATE_CONTRACT : GasCost.TRANSACTION) + zeroVals * GasCost.TX_ZERO_DATA + nonZeroes * GasCost.TX_NO_ZERO_DATA;
    }

    public void verify() {
        validate();
    }

    private void validate() {
        if (nonce.size() != this.ownerCount && nonce.size() != this.ownerCount + 1) {
            throw new RuntimeException("Invalid nonce count");
        }
        for (byte[] nonce : nonce) {
            if (nonce.length > DATAWORD_LENGTH) {
                throw new RuntimeException("Nonce is not valid");
            }
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
        for (ECDSASignature signature : signatures) {
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

    public Keccak256 getRawHash(int signerIndex) {
        byte[] plainMsg = this.getEncodedRaw(signerIndex);
        return new Keccak256(HashUtil.keccak256(plainMsg));
    }

    public byte[] getSingleNonce() {
        if (nonce.size() > 1) {
            throw new UnsupportedOperationException("Tried get only one nonce");
        }
        return nonce.isEmpty() ? EMPTY_BYTE_ARRAY : nonce.get(0);
    }

    public List<byte[]> getNonces() {
        return Collections.unmodifiableList(this.nonce);
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

    //Throws exception when multi-signature because you shouldn't be using only one of the signatures
    public ECDSASignature getSingleSignature() {
        if (signatures.size() > 1) {
            throw new UnsupportedOperationException("Tried getting only one signature of multi-signature transaction");
        }
        return signatures.size() == 1 ? signatures.get(0) : null;
    }

    public List<ECDSASignature> getSignatures() {
        return Collections.unmodifiableList(this.signatures);
    }

    public void setSignature(ECDSASignature signature) {
        this.signatures = Collections.singletonList(signature);
        this.rlpEncoding = null;
        this.hash = null;
        this.sender = null;
    }

    public boolean acceptTransactionSignature(byte currentChainId) {
        if (this.signatures.size() == 0) {
            return false;
        }
        for (ECDSASignature signature : this.signatures) {
            if (signature == null || !signature.validateComponents() || signature.s.compareTo(SECP256K1N_HALF) >= 0) {
                return false;
            }
        }

        return this.getChainId() == 0 || this.getChainId() == currentChainId;
    }

    public void sign(byte[] privKeyBytes) throws MissingPrivateKeyException {
        byte[] raw = this.getRawHash().getBytes();
        ECKey key = ECKey.fromPrivate(privKeyBytes).decompress();
        this.signatures = Collections.singletonList(key.sign(raw));
        this.rlpEncoding = null;
        this.hash = null;
        this.sender = null;
    }

    public void sign(List<byte[]> privKeyBytesList) throws MissingPrivateKeyException {
        List<ECDSASignature> signatures = new ArrayList<>();
        for (int i = 0; i < privKeyBytesList.size(); i++) {
            byte[] raw = this.getRawHash(i).getBytes();
            ECKey key = ECKey.fromPrivate(privKeyBytesList.get(i)).decompress();
            signatures.add(key.sign(raw));
        }
        this.signatures = Collections.unmodifiableList(signatures);
        this.rlpEncoding = null;
        this.hash = null;
        this.sender = null;
    }

    @Nullable
    public RskAddress getContractAddress() {
        if (!isContractCreation()) {
            return null;
        }

        return new RskAddress(HashUtil.calcNewAddr(this.getSender().getBytes(), this.getSingleNonce()));
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
        ECDSASignature signature = getSingleSignature();
        Metric metric = profiler.start(Profiler.PROFILING_TYPE.KEY_RECOV_FROM_SIG);
        byte[] raw = getRawHash().getBytes();
        //We clear the 4th bit, the compress bit, in case a signature is using compress in true
        ECKey key = ECKey.recoverFromSignature((signature.v - 27) & ~4, signature, raw, true);
        profiler.stop(metric);
        return key;
    }

    public synchronized List<RskAddress> getSenders() {
        if (this.senders != null) {
            return this.senders;
        }

        if (ownerCount == 1) {
            this.senders = Collections.singletonList(getSender());
        } else {
            ArrayList<RskAddress> senders = new ArrayList<>(ownerCount);
            try {
                for (int i = 0; i < signatures.size(); i++) {
                    byte[] hash = getRawHash(i).getBytes();
                    ECKey key = ECKey.signatureToKey(hash, signatures.get(i));
                    senders.add(new RskAddress(key.getAddress()));
                }
                this.senders = Collections.unmodifiableList(senders);

            } catch (SignatureException e) {
                logger.error(e.getMessage(), e);
                panicProcessor.panic("transaction", e.getMessage());
                this.senders = Collections.singletonList(RskAddress.nullAddress());
            }
        }
        return this.senders;
    }

    public synchronized RskAddress getSender() {
        if (sender != null) {
            return sender;
        }


        Metric metric = profiler.start(Profiler.PROFILING_TYPE.KEY_RECOV_FROM_SIG);
        try {
            if (ownerCount > 1) {
                int length = 0;
                ArrayList<byte[]> keys = new ArrayList<>(ownerCount);
                for (int i = 0; i < ownerCount; i++) {
                    byte[] hash = getRawHash(i).getBytes();
                    ECKey key = ECKey.signatureToKey(hash, signatures.get(i));
                    byte[] pubKey = key.getPubKey();
                    keys.add(pubKey);
                    length += pubKey.length;
                }
                ByteBuffer buf = ByteBuffer.allocate(length);
                for (byte[] pubKey : keys) {
                    buf.put(pubKey);
                }
                //TODO This seems to be the last 20 bytes rather than the first 20, is this okay
                byte[] pubKeyHash = HashUtil.keccak256Omit12(buf.array());
                sender = new RskAddress(pubKeyHash);
            } else {
                ECKey key = ECKey.signatureToKey(getRawHash().getBytes(), getSingleSignature());
                sender = new RskAddress(key.getAddress());
            }
        } catch (SignatureException e) {
            logger.error(e.getMessage(), e);
            panicProcessor.panic("transaction", e.getMessage());
            sender = RskAddress.nullAddress();
        } finally {
            profiler.stop(metric);
        }

        return sender;
    }

    /* Gets sender charged (and reimbursed) gas fees */
    public RskAddress getChargedSender() {
        List<RskAddress> senders = this.getSenders();
        if (this.nonce.size() == ownerCount) {
            return this.getSender();
        } else {
            return senders.get(senders.size() - 1);
        }
    }

    public byte getChainId() {
        return chainId;
    }

    @Override
    public String toString() {
        if (version == 0) {
            ECDSASignature signature = this.signatures.get(0);
            return "TransactionData [" + "hash=" + ByteUtil.toHexString(getHash().getBytes()) +
                    "  nonce=" + ByteUtil.toHexString(nonce.get(0)) +
                    ", gasPrice=" + gasPrice.toString() +
                    ", gas=" + ByteUtil.toHexString(gasLimit) +
                    ", receiveAddress=" + receiveAddress.toString() +
                    ", value=" + value.toString() +
                    ", data=" + ByteUtil.toHexString(data) +
                    ", signatureV=" + (signature == null ? "" : signature.v) +
                    ", signatureR=" + (signature == null ? "" : ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(
                    signature.r))) +
                    ", signatureS=" + (signature == null ? "" : ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(
                    signature.s))) +
                    "]";
        } else {
            return "TransactionData [" + "hash=" + ByteUtil.toHexString(getHash().getBytes()) +
                    "  nonce={" + nonce.stream().map(ByteUtil::toHexString).collect(Collectors.joining(", ")) +
                    "}, gasPrice=" + gasPrice.toString() +
                    ", gas=" + ByteUtil.toHexString(gasLimit) +
                    ", receiveAddress=" + receiveAddress.toString() +
                    ", value=" + value.toString() +
                    ", data=" + ByteUtil.toHexString(data) +
                    ", ownerCount=" + ownerCount +
                    ", signatures={" + signatures.stream().map(signature -> "Signature [v=" + signature.v +
                    ", r=" + ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(signature.r)) +
                    ", s=" + ByteUtil.toHexString(BigIntegers.asUnsignedByteArray(signature.s)) +
                    "]").collect(Collectors.joining(", ")) +
                    "}]";
        }

    }

    /**
     * For signatures you have to keep also
     * RLP of the transaction without any signature data
     */
    public byte[] getEncodedRaw() {
        if (this.rawRlpEncoding == null) {
            if (this.version == 0) {
                // Since EIP-155 use chainId for v
                if (chainId == 0) {
                    this.rawRlpEncoding = encodeFormat0(null, null, null);
                } else {
                    byte[] v = RLP.encodeByte(chainId);
                    byte[] r = RLP.encodeElement(EMPTY_BYTE_ARRAY);
                    byte[] s = RLP.encodeElement(EMPTY_BYTE_ARRAY);

                    this.rawRlpEncoding = encodeFormat0(v, r, s);
                }
            } else {
                this.rawRlpEncoding = encodeFormat1(false, -1);
            }
        }

        return ByteUtil.cloneBytes(this.rawRlpEncoding);
    }

    public byte[] getEncodedRaw(int signerIndex) {
        return encodeFormat1(false, signerIndex);
    }

    public byte[] getEncoded() {
        if (this.rlpEncoding == null) {
            if (this.version == 0) {
                byte[] v;
                byte[] r;
                byte[] s;

                if (!this.signatures.isEmpty()) {
                    ECDSASignature signature = this.signatures.get(0);
                    v = RLP.encodeByte((byte) (chainId == 0 ? signature.v : (signature.v - LOWER_REAL_V) + (chainId * 2 + CHAIN_ID_INC)));
                    r = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.r));
                    s = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.s));
                } else {
                    v = chainId == 0 ? RLP.encodeElement(EMPTY_BYTE_ARRAY) : RLP.encodeByte(chainId);
                    r = RLP.encodeElement(EMPTY_BYTE_ARRAY);
                    s = RLP.encodeElement(EMPTY_BYTE_ARRAY);
                }

                this.rlpEncoding = encodeFormat0(v, r, s);
            } else {
                this.rlpEncoding = encodeFormat1(true, -1);
            }
        }

        return ByteUtil.cloneBytes(this.rlpEncoding);
    }

    private byte[] encodeFormat0(byte[] v, byte[] r, byte[] s) {
        // parse null as 0 for nonce
        byte[] toEncodeNonce;
        if (this.nonce.isEmpty() || this.nonce.get(0).length == 1 && this.nonce.get(0)[0] == 0) {
            toEncodeNonce = RLP.encodeElement(null);
        } else {
            toEncodeNonce = RLP.encodeElement(this.nonce.get(0));
        }
        byte[] toEncodeGasPrice = RLP.encodeCoinNonNullZero(this.gasPrice);
        byte[] toEncodeGasLimit = RLP.encodeElement(this.gasLimit);
        byte[] toEncodeReceiveAddress = RLP.encodeRskAddress(this.receiveAddress);
        byte[] toEncodeValue = RLP.encodeCoinNullZero(this.value);
        byte[] toEncodeData = RLP.encodeElement(this.data);

        if (v == null && r == null && s == null) {
            return RLP.encodeList(toEncodeNonce, toEncodeGasPrice, toEncodeGasLimit,
                                  toEncodeReceiveAddress, toEncodeValue, toEncodeData
            );
        }

        return RLP.encodeList(toEncodeNonce, toEncodeGasPrice, toEncodeGasLimit,
                              toEncodeReceiveAddress, toEncodeValue, toEncodeData, v, r, s
        );
    }

    private byte[] encodeFormat1(boolean signed, int signerIndex) {
        byte[] toEncodeVersion = RLP.encodeList(RLP.encodeInt(version));
        // parse null as 0 for nonce
        byte[] toEncodeNonce;
        if (this.nonce.isEmpty() || this.nonce.get(0).length == 1 && this.nonce.get(0)[0] == 0) {
            toEncodeNonce = RLP.encodeElement(null);
        } else if (this.nonce.size() == 1) {
            toEncodeNonce = RLP.encodeElement(this.nonce.get(0));
        } else {
            byte[][] toEncodeNonceElements = new byte[this.nonce.size()][];
            for (int i = 0; i < this.nonce.size(); i++) {
                toEncodeNonceElements[i] = RLP.encodeElement(this.nonce.get(i));
            }
            toEncodeNonce = RLP.encodeList(toEncodeNonceElements);
        }
        byte[] toEncodeGasPrice = RLP.encodeCoinNonNullZero(this.gasPrice);
        byte[] toEncodeGasLimit = RLP.encodeElement(this.gasLimit);
        byte[] toEncodeReceiveAddress = RLP.encodeRskAddress(this.receiveAddress);
        byte[] toEncodeValue = RLP.encodeCoinNullZero(this.value);
        byte[] toEncodeData = RLP.encodeElement(this.data);
        byte[] toEncodeOwnerCount = RLP.encodeInt(this.ownerCount);
        byte[] toEncodeSignature;
        if (signed && !signatures.isEmpty()) {
            byte[][] toEncodeSignatureElements = new byte[signatures.size()][];
            for (int i = 0; i < signatures.size(); i++) {
                ECDSASignature signature = signatures.get(i);
                byte[] v = RLP.encodeByte((byte) (chainId == 0 ? signature.v : (signature.v - LOWER_REAL_V) + (chainId * 2 + CHAIN_ID_INC)));
                byte[] r = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.r));
                byte[] s = RLP.encodeElement(BigIntegers.asUnsignedByteArray(signature.s));
                toEncodeSignatureElements[i] = RLP.encodeList(v, r, s);
            }
            toEncodeSignature = RLP.encodeList(toEncodeSignatureElements);
        } else if (signerIndex == -1) {
            if (chainId == 0) {
                toEncodeSignature = RLP.encodedEmptyList();
            } else {
                byte[] v = RLP.encodeByte(chainId);
                byte[] r = RLP.encodeElement(EMPTY_BYTE_ARRAY);
                byte[] s = RLP.encodeElement(EMPTY_BYTE_ARRAY);

                toEncodeSignature = RLP.encodeList(v, r, s);
            }
        } else {
            toEncodeSignature = RLP.encodeList(RLP.encodeInt(this.chainId), RLP.encodeInt(signerIndex));
        }

        return RLP.encodeList(
                toEncodeVersion,
                toEncodeNonce,
                toEncodeGasPrice,
                toEncodeGasLimit,
                toEncodeReceiveAddress,
                toEncodeValue,
                toEncodeData,
                toEncodeOwnerCount,
                toEncodeSignature
        );
    }

    public BigInteger getGasLimitAsInteger() {
        return (this.getGasLimit() == null) ? null : BigIntegers.fromUnsignedByteArray(this.getGasLimit());
    }

    public BigInteger getNonceAsInteger() {
        return (this.getSingleNonce() == null) ? null : BigIntegers.fromUnsignedByteArray(this.getSingleNonce());
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
        if (null != getData() || !this.signatures.isEmpty()) {
            return false;
        }

        return Coin.ZERO.equals(getValue()) &&
                BigInteger.ZERO.equals(new BigInteger(1, getGasLimit())) &&
                Coin.ZERO.equals(getGasPrice());
    }
}
