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

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.config.RskMiningConstants;
import co.rsk.core.bc.CachedBtcBlockForFac;
import co.rsk.core.bc.ForkBalanceFacProtocolConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.util.ListArrayUtil;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Fork-balance proof encoding and validation helpers (header v3).
 * Wire format: RLP {@code [proofType, parentBtcHeader, coinbaseHash, coinbaseProof, coinbaseLastBytes, midstateProof]}.
 */
public final class ForkBalanceProofUtils {

    private ForkBalanceProofUtils() {
    }

    /**
     * Classifies {@code BTCB.rskReference()} from the parent coinbase suffix against {@code rskMergedMiningHashes}.
     *
     * @return {@code 2} if there is no RSK tag (or payload too short); {@code 1} if a tag is present but the
     *         32-byte value after the tag matches none of {@code rskMergedMiningHashes}; {@code 0} if a tag is
     *         present and that value matches an entry in the list.
     */
    public static byte proofTypeIdentificationFromCoinbaseSuffix(
            byte[] coinbaseLastBytes,
            List<Keccak256> rskMergedMiningHashes) {
        byte[] suffix = coinbaseLastBytes != null ? coinbaseLastBytes : EMPTY_BYTE_ARRAY;
        int tagPos = Collections.lastIndexOfSubList(
                ListArrayUtil.asByteList(suffix),
                ListArrayUtil.asByteList(RskMiningConstants.RSK_TAG));
        if (tagPos < 0) {
            return 2;
        }
        int hashStart = tagPos + RskMiningConstants.RSK_TAG.length;
        if (hashStart + Keccak256.HASH_LEN > suffix.length) {
            return 2;
        }
        byte[] fromTag = Arrays.copyOfRange(suffix, hashStart, hashStart + Keccak256.HASH_LEN);
        Keccak256 hashInCoinbase = new Keccak256(fromTag);
        if (rskMergedMiningHashes == null || rskMergedMiningHashes.isEmpty()) {
            return 1;
        }
        for (Keccak256 h : rskMergedMiningHashes) {
            if (h != null && h.equals(hashInCoinbase)) {
                return 0;
            }
        }
        return 1;
    }

    /**
     * Same as {@link #proofTypeIdentificationFromCoinbaseSuffix} using the wire serialization of the
     * <strong>parent</strong> BTC coinbase ({@code BTCB}).
     */
    public static byte proofTypeIdentification(BtcTransaction parentBtcCoinbase, List<Keccak256> rskMergedMiningHashes) {
        if (parentBtcCoinbase == null) {
            throw new IllegalArgumentException("parentBtcCoinbase must not be null");
        }
        return proofTypeIdentificationFromCoinbaseSuffix(
                ForkBalanceParentCoinbaseProof.fromSerialized(parentBtcCoinbase.bitcoinSerialize()).getCoinbaseLastBytes(),
                rskMergedMiningHashes);
    }

    /**
     * Default proof for v3 headers built before merge-mining data exists: {@code proofType = 2}, empty payloads.
     */
    public static byte[] defaultForkBalanceProofSkeletonBytes() {
        return encodeForkBalanceProofSkeleton(
                (byte) 2,
                new byte[80],
                new byte[0],
                new byte[0],
                new byte[0],
                new byte[0]);
    }

    /**
     * Encodes the fork-balance proof as an RLP list of six elements (spec order).
     *
     * @param proofType         {@link #proofTypeIdentificationFromCoinbaseSuffix}
     * @param parentBtcHeader   parent Bitcoin block header on the wire, exactly 80 bytes
     * @param coinbaseHash      double-SHA256 id of the parent coinbase (32 bytes)
     * @param coinbaseProof     partial Merkle proof for the parent BTC block's coinbase
     * @param coinbaseLastBytes tail of the parent coinbase (up to 128 bytes)
     * @param midstateProof     SHA-256 midstate for the parent coinbase
     */
    public static byte[] encodeForkBalanceProofSkeleton(
            byte proofType,
            byte[] parentBtcHeader,
            byte[] coinbaseHash,
            byte[] coinbaseProof,
            byte[] coinbaseLastBytes,
            byte[] midstateProof) {
        Objects.requireNonNull(parentBtcHeader, "parentBtcHeader");
        if (parentBtcHeader.length != 80) {
            throw new IllegalArgumentException("parentBtcHeader must be exactly 80 bytes, got " + parentBtcHeader.length);
        }
        byte[] suffix = coinbaseLastBytes != null ? coinbaseLastBytes : new byte[0];
        if (suffix.length > ForkBalanceFacProtocolConstants.PARENT_COINBASE_SUFFIX_MAX_BYTES) {
            throw new IllegalArgumentException(
                    "coinbaseLastBytes exceeds "
                            + ForkBalanceFacProtocolConstants.PARENT_COINBASE_SUFFIX_MAX_BYTES + " bytes");
        }
        byte[] hash = coinbaseHash != null ? coinbaseHash : new byte[0];
        if (hash.length != 0 && hash.length != Sha256Hash.LENGTH) {
            throw new IllegalArgumentException("coinbaseHash must be empty or exactly 32 bytes");
        }
        byte[] mp = coinbaseProof != null ? coinbaseProof : new byte[0];
        byte[] mid = midstateProof != null ? midstateProof : new byte[0];
        if (mid.length != 0 && !ForkBalanceParentCoinbaseProof.isValidMidstateWireBytes(mid)) {
            throw new IllegalArgumentException("midstateProof is not a valid SHA-256 encoded midstate");
        }
        // Use encodeElement for the type byte so proofType 0 is a single 0x00 item; RLP.encodeByte(0) is 0x80 and
        // decodes to an empty payload, which breaks decodeForkBalanceProof / FAC evidence for type 0.
        return RLP.encodeList(
                RLP.encodeElement(new byte[]{proofType}),
                RLP.encodeElement(parentBtcHeader),
                RLP.encodeElement(hash),
                RLP.encodeElement(mp),
                RLP.encodeElement(suffix),
                RLP.encodeElement(mid));
    }

    /**
     * Builds fork-balance proof bytes from merge-mining context.
     * <p>
     * The parent Bitcoin header and parent coinbase are taken from {@code btcParentBlock} (BTCB). A check enforces
     * that {@code mergedMinedBtcBlock} (BTCA) references {@code btcParentBlock} as its previous block.
     *
     * @param coinbaseMerkleProof partial Merkle proof for the parent BTC block's coinbase (from
     *                            {@link MinerUtils#buildMerkleProof} with {@link MerkleProofBuilder#buildFromBlock} on the parent)
     */
    public static byte[] buildForkBalanceProofSkeleton(
            List<Keccak256> rskHashesForProofType,
            BtcBlock mergedMinedBtcBlock,
            BtcBlock btcParentBlock,
            byte[] coinbaseMerkleProof) {
        Objects.requireNonNull(mergedMinedBtcBlock, "mergedMinedBtcBlock");
        Objects.requireNonNull(btcParentBlock, "btcParentBlock");
        assertBtcParentChildLink(mergedMinedBtcBlock, btcParentBlock);
        ForkBalanceParentCoinbaseProof.CompactFields parentCoinbase =
                ForkBalanceParentCoinbaseProof.fromCoinbase(btcParentBlock.getTransactions().get(0));
        return buildForkBalanceProofSkeleton(
                rskHashesForProofType,
                mergedMinedBtcBlock,
                btcHeaderWireBytes(btcParentBlock),
                parentCoinbase,
                coinbaseMerkleProof);
    }

    /**
     * Builds fork-balance proof bytes using a cached parent entry (header, coinbase, and merkle proof).
     */
    public static byte[] buildForkBalanceProofSkeleton(
            List<Keccak256> rskHashesForProofType,
            BtcBlock mergedMinedBtcBlock,
            CachedBtcBlockForFac cachedParent) {
        Objects.requireNonNull(cachedParent, "cachedParent");
        if (!cachedParent.isComplete()) {
            throw new IllegalArgumentException("cached parent entry is incomplete");
        }
        assertBtcParentChildLink(mergedMinedBtcBlock, cachedParent.getBlockHash());
        ForkBalanceParentCoinbaseProof.CompactFields parentCoinbase =
                ForkBalanceParentCoinbaseProof.fromSerialized(cachedParent.getCoinbaseSerialized());
        return buildForkBalanceProofSkeleton(
                rskHashesForProofType,
                mergedMinedBtcBlock,
                cachedParent.getHeader80(),
                parentCoinbase,
                cachedParent.getCoinbaseMerkleProof());
    }

    private static byte[] buildForkBalanceProofSkeleton(
            List<Keccak256> rskHashesForProofType,
            BtcBlock mergedMinedBtcBlock,
            byte[] parentBtcHeader,
            ForkBalanceParentCoinbaseProof.CompactFields parentCoinbase,
            byte[] coinbaseMerkleProof) {
        byte proofType = proofTypeIdentificationFromCoinbaseSuffix(
                parentCoinbase.getCoinbaseLastBytes(),
                rskHashesForProofType);
        return encodeForkBalanceProofSkeleton(
                proofType,
                parentBtcHeader,
                parentCoinbase.getCoinbaseHash(),
                coinbaseMerkleProof,
                parentCoinbase.getCoinbaseLastBytes(),
                parentCoinbase.getMidStateProof());
    }

    private static void assertBtcParentChildLink(BtcBlock child, Sha256Hash parentHash) {
        Sha256Hash expectedPrev = child.getPrevBlockHash();
        if (Sha256Hash.ZERO_HASH.equals(expectedPrev)) {
            return;
        }
        if (!expectedPrev.equals(parentHash)) {
            throw new IllegalArgumentException(
                    "mergedMinedBtcBlock prevBlockHash does not match cached parent hash");
        }
    }

    private static void assertBtcParentChildLink(BtcBlock child, BtcBlock parent) {
        Sha256Hash expectedPrev = child.getPrevBlockHash();
        if (Sha256Hash.ZERO_HASH.equals(expectedPrev)) {
            return;
        }
        Sha256Hash parentHash = parent.getHash();
        if (!expectedPrev.equals(parentHash)) {
            throw new IllegalArgumentException(
                    "mergedMinedBtcBlock prevBlockHash does not match btcParentBlock hash (expected parent-child link)");
        }
    }

    private static byte[] btcHeaderWireBytes(BtcBlock block) {
        byte[] header = block.cloneAsHeader().bitcoinSerialize();
        if (header.length != 80) {
            throw new IllegalStateException("BTC block header serialization must be 80 bytes, got " + header.length);
        }
        return header;
    }

    /**
     * Decoded {@code forkBalanceProof} RLP payload (six elements).
     */
    public static final class ForkBalanceProofDecoded {
        private final byte proofType;
        private final byte[] parentBtcHeader;
        private final byte[] coinbaseHash;
        private final byte[] coinbaseProof;
        private final byte[] coinbaseLastBytes;
        private final byte[] midstateProof;

        ForkBalanceProofDecoded(
                byte proofType,
                byte[] parentBtcHeader,
                byte[] coinbaseHash,
                byte[] coinbaseProof,
                byte[] coinbaseLastBytes,
                byte[] midstateProof) {
            this.proofType = proofType;
            this.parentBtcHeader = parentBtcHeader;
            this.coinbaseHash = coinbaseHash;
            this.coinbaseProof = coinbaseProof;
            this.coinbaseLastBytes = coinbaseLastBytes;
            this.midstateProof = midstateProof;
        }

        public byte getProofType() {
            return proofType;
        }

        public byte[] getParentBtcHeader() {
            return parentBtcHeader;
        }

        public byte[] getCoinbaseHash() {
            return coinbaseHash;
        }

        public byte[] getCoinbaseProof() {
            return coinbaseProof;
        }

        public byte[] getCoinbaseLastBytes() {
            return coinbaseLastBytes;
        }

        /** @deprecated use {@link #getCoinbaseLastBytes()} */
        @Deprecated
        public byte[] getLastCoinbaseBytes() {
            return getCoinbaseLastBytes();
        }

        public byte[] getMidStateProof() {
            return midstateProof;
        }

        /** @deprecated use {@link #getMidStateProof()} */
        @Deprecated
        public byte[] getMidstateProof() {
            return getMidStateProof();
        }
    }

    public static boolean isDefaultForkBalancePlaceholder(byte[] forkBalanceProofEncoded) {
        return forkBalanceProofEncoded != null
                && Arrays.equals(forkBalanceProofEncoded, defaultForkBalanceProofSkeletonBytes());
    }

    public static ForkBalanceProofDecoded decodeForkBalanceProof(byte[] encoded) {
        if (encoded == null || encoded.length == 0) {
            throw new IllegalArgumentException("forkBalanceProof is null or empty");
        }
        RLPList list = RLP.decodeList(encoded);
        if (list.size() != 6) {
            throw new IllegalArgumentException("forkBalanceProof must be an RLP list of 6 elements");
        }
        byte[] proofTypeRaw = list.get(0).getRLPData();
        if (proofTypeRaw == null || proofTypeRaw.length != 1) {
            throw new IllegalArgumentException("proofType must be a single byte");
        }
        int proofTypeUnsigned = proofTypeRaw[0] & 0xFF;
        if (proofTypeUnsigned > 2) {
            throw new IllegalArgumentException("proofType must be 0, 1, or 2");
        }
        byte proofType = (byte) proofTypeUnsigned;
        byte[] parent = list.get(1).getRLPData();
        if (parent == null || parent.length != 80) {
            throw new IllegalArgumentException("parentBtcHeader must be exactly 80 bytes");
        }
        byte[] hash = list.get(2).getRLPData();
        if (hash == null) {
            hash = EMPTY_BYTE_ARRAY;
        }
        if (hash.length != 0 && hash.length != Sha256Hash.LENGTH) {
            throw new IllegalArgumentException("coinbaseHash must be empty or exactly 32 bytes");
        }
        byte[] cbProof = list.get(3).getRLPData();
        if (cbProof == null) {
            cbProof = EMPTY_BYTE_ARRAY;
        }
        byte[] lastBytes = list.get(4).getRLPData();
        if (lastBytes == null) {
            lastBytes = EMPTY_BYTE_ARRAY;
        }
        if (lastBytes.length > ForkBalanceFacProtocolConstants.PARENT_COINBASE_SUFFIX_MAX_BYTES) {
            throw new IllegalArgumentException(
                    "coinbaseLastBytes exceeds "
                            + ForkBalanceFacProtocolConstants.PARENT_COINBASE_SUFFIX_MAX_BYTES + " bytes");
        }
        byte[] mid = list.get(5).getRLPData();
        if (mid == null) {
            mid = EMPTY_BYTE_ARRAY;
        }
        if (mid.length != 0 && !ForkBalanceParentCoinbaseProof.isValidMidstateWireBytes(mid)) {
            throw new IllegalArgumentException("midstateProof is not a valid SHA-256 encoded midstate");
        }
        return new ForkBalanceProofDecoded(proofType, parent, hash, cbProof, lastBytes, mid);
    }

    /**
     * Whether the compressed merged-mining coinbase stored on the RSK header contains the RSK merged-mining tag
     * (searches the tail after midstate truncation, then the full buffer).
     */
    public static boolean mergedMiningStoredCoinbaseContainsRskTag(byte[] compressedCoinbase) {
        return extractRskTagPositionInMergedMiningStoredCoinbase(compressedCoinbase) >= 0;
    }

    /**
     * Extracts the 32-byte merged-mining hash after the last RSK tag in the stored (compressed) coinbase, if present.
     */
    public static Optional<Keccak256> extractRskMergedMiningHashFromCompressedCoinbase(byte[] compressedCoinbase) {
        int pos = extractRskTagPositionInMergedMiningStoredCoinbase(compressedCoinbase);
        if (pos < 0) {
            return Optional.empty();
        }
        int hashStart = pos + RskMiningConstants.RSK_TAG.length;
        if (compressedCoinbase == null || hashStart + Keccak256.HASH_LEN > compressedCoinbase.length) {
            return Optional.empty();
        }
        return Optional.of(new Keccak256(Arrays.copyOfRange(compressedCoinbase, hashStart, hashStart + Keccak256.HASH_LEN)));
    }

    private static int extractRskTagPositionInMergedMiningStoredCoinbase(byte[] compressedCoinbase) {
        if (compressedCoinbase == null || compressedCoinbase.length <= RskMiningConstants.MIDSTATE_SIZE_TRIMMED) {
            return -1;
        }
        byte[] tail = Arrays.copyOfRange(
                compressedCoinbase, RskMiningConstants.MIDSTATE_SIZE_TRIMMED, compressedCoinbase.length);
        int inTail = Collections.lastIndexOfSubList(
                ListArrayUtil.asByteList(tail),
                ListArrayUtil.asByteList(RskMiningConstants.RSK_TAG));
        if (inTail >= 0) {
            return RskMiningConstants.MIDSTATE_SIZE_TRIMMED + inTail;
        }
        return Collections.lastIndexOfSubList(
                ListArrayUtil.asByteList(compressedCoinbase),
                ListArrayUtil.asByteList(RskMiningConstants.RSK_TAG));
    }
}
