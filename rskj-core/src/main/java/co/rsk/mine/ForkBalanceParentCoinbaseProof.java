/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package co.rsk.mine;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.core.bc.ForkBalanceFacProtocolConstants;
import co.rsk.peg.utils.BtcTransactionFormatUtils;
import org.bouncycastle.crypto.digests.SHA256Digest;

import java.util.Arrays;
import java.util.Objects;

/**
 * Compact parent coinbase fields for fork-balance proofs (suffix, hash, midstate).
 */
public final class ForkBalanceParentCoinbaseProof {

    /** Upper bound for {@code midStateProof} on the wire (generous vs BouncyCastle {@link SHA256Digest#getEncodedState()}). */
    public static final int MIDSTATE_PROOF_MAX_WIRE_BYTES = 128;

    private ForkBalanceParentCoinbaseProof() {
    }

    /**
     * Wire fields derived from a parent coinbase transaction serialization.
     */
    public static final class CompactFields {
        private final byte[] lastCoinbaseBytes;
        private final byte[] coinbaseHash;
        private final byte[] midStateProof;

        public CompactFields(byte[] lastCoinbaseBytes, byte[] coinbaseHash, byte[] midStateProof) {
            this.lastCoinbaseBytes = Objects.requireNonNull(lastCoinbaseBytes, "lastCoinbaseBytes").clone();
            this.coinbaseHash = Objects.requireNonNull(coinbaseHash, "coinbaseHash").clone();
            this.midStateProof = Objects.requireNonNull(midStateProof, "midStateProof").clone();
            validateLengths(this.lastCoinbaseBytes, this.coinbaseHash, this.midStateProof);
        }

        public byte[] getCoinbaseLastBytes() {
            return lastCoinbaseBytes.clone();
        }

        /** @deprecated use {@link #getCoinbaseLastBytes()} */
        @Deprecated
        public byte[] getLastCoinbaseBytes() {
            return getCoinbaseLastBytes();
        }

        public byte[] getCoinbaseHash() {
            return coinbaseHash.clone();
        }

        public byte[] getMidStateProof() {
            return midStateProof.clone();
        }
    }

    public static CompactFields fromSerialized(byte[] coinbaseSerialized) {
        Objects.requireNonNull(coinbaseSerialized, "coinbaseSerialized");
        if (coinbaseSerialized.length == 0) {
            throw new IllegalArgumentException("coinbaseSerialized must not be empty");
        }
        int suffixLen = Math.min(ForkBalanceFacProtocolConstants.PARENT_COINBASE_SUFFIX_MAX_BYTES, coinbaseSerialized.length);
        int prefixLen = coinbaseSerialized.length - suffixLen;
        byte[] lastCoinbaseBytes = Arrays.copyOfRange(
                coinbaseSerialized, coinbaseSerialized.length - suffixLen, coinbaseSerialized.length);
        byte[] midStateProof = midStateProofAfterPrefix(coinbaseSerialized, prefixLen);
        byte[] coinbaseHash = BtcTransactionFormatUtils.calculateBtcTxHash(coinbaseSerialized).getBytes();
        return new CompactFields(lastCoinbaseBytes, coinbaseHash, midStateProof);
    }

    public static CompactFields fromCoinbase(BtcTransaction coinbase) {
        Objects.requireNonNull(coinbase, "coinbase");
        return fromSerialized(coinbase.bitcoinSerialize());
    }

    /**
     * Verifies that {@code midStateProof} and {@code lastCoinbaseBytes} commit to {@code coinbaseHash}
     * (Bitcoin double-SHA256 tx id, internal byte order).
     */
    public static boolean verifyCoinbaseHash(byte[] midStateProof, byte[] lastCoinbaseBytes, byte[] coinbaseHash) {
        try {
            validateLengths(lastCoinbaseBytes, coinbaseHash, midStateProof);
        } catch (IllegalArgumentException e) {
            return false;
        }
        Sha256Hash computed = hashFromMidstateAndSuffix(midStateProof, lastCoinbaseBytes);
        return Arrays.equals(computed.getBytes(), coinbaseHash);
    }

    public static Sha256Hash coinbaseHashFromWireBytes(byte[] coinbaseHash) {
        if (coinbaseHash == null || coinbaseHash.length != Sha256Hash.LENGTH) {
            throw new IllegalArgumentException("coinbaseHash must be exactly 32 bytes");
        }
        return Sha256Hash.wrap(coinbaseHash);
    }

    /**
     * Builds midstate after hashing {@code serialized[0..prefixLen)}. When {@code prefixLen} is not a multiple of
     * 64, the partial block is retained in the encoded state so verification can continue with the suffix only.
     */
    private static byte[] midStateProofAfterPrefix(byte[] serialized, int prefixLen) {
        SHA256Digest digest = new SHA256Digest();
        if (prefixLen > 0) {
            digest.update(serialized, 0, prefixLen);
        }
        return digest.getEncodedState();
    }

    private static Sha256Hash hashFromMidstateAndSuffix(byte[] midStateProof, byte[] lastCoinbaseBytes) {
        SHA256Digest digest = new SHA256Digest(midStateProof);
        digest.update(lastCoinbaseBytes, 0, lastCoinbaseBytes.length);
        byte[] firstRound = new byte[Sha256Hash.LENGTH];
        digest.doFinal(firstRound, 0);
        return Sha256Hash.wrapReversed(Sha256Hash.hash(firstRound));
    }

    public static boolean isValidMidstateWireBytes(byte[] midStateProof) {
        if (midStateProof == null || midStateProof.length == 0
                || midStateProof.length > MIDSTATE_PROOF_MAX_WIRE_BYTES) {
            return false;
        }
        try {
            new SHA256Digest(midStateProof);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void validateLengths(byte[] lastCoinbaseBytes, byte[] coinbaseHash, byte[] midStateProof) {
        if (lastCoinbaseBytes.length == 0
                || lastCoinbaseBytes.length > ForkBalanceFacProtocolConstants.PARENT_COINBASE_SUFFIX_MAX_BYTES) {
            throw new IllegalArgumentException(
                    "lastCoinbaseBytes length must be in [1, "
                            + ForkBalanceFacProtocolConstants.PARENT_COINBASE_SUFFIX_MAX_BYTES + "]");
        }
        if (coinbaseHash.length != Sha256Hash.LENGTH) {
            throw new IllegalArgumentException("coinbaseHash must be exactly 32 bytes");
        }
        if (!isValidMidstateWireBytes(midStateProof)) {
            throw new IllegalArgumentException("midStateProof is not a valid SHA-256 encoded midstate");
        }
    }
}
