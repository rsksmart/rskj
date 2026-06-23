/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package co.rsk.core.bc;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Cached Bitcoin block data used to build fork-balance proofs when this block becomes the parent
 * of a merge-mined candidate.
 */
public final class CachedBtcBlockForFac {

    private final Sha256Hash blockHash;
    private final Sha256Hash prevBlockHash;
    private final int height;
    private final byte[] header80;
    @Nullable
    private final byte[] coinbaseSerialized;
    private final byte[] coinbaseMerkleProof;

    public CachedBtcBlockForFac(
            Sha256Hash blockHash,
            Sha256Hash prevBlockHash,
            int height,
            byte[] header80,
            @Nullable byte[] coinbaseSerialized,
            byte[] coinbaseMerkleProof) {
        this.blockHash = Objects.requireNonNull(blockHash, "blockHash");
        this.prevBlockHash = Objects.requireNonNull(prevBlockHash, "prevBlockHash");
        this.height = height;
        this.header80 = Objects.requireNonNull(header80, "header80").clone();
        if (this.header80.length != 80) {
            throw new IllegalArgumentException("header80 must be exactly 80 bytes");
        }
        this.coinbaseSerialized = coinbaseSerialized != null ? coinbaseSerialized.clone() : null;
        this.coinbaseMerkleProof = coinbaseMerkleProof != null ? coinbaseMerkleProof.clone() : new byte[0];
    }

    public Sha256Hash getBlockHash() {
        return blockHash;
    }

    public Sha256Hash getPrevBlockHash() {
        return prevBlockHash;
    }

    public int getHeight() {
        return height;
    }

    public byte[] getHeader80() {
        return header80.clone();
    }

    @Nullable
    public byte[] getCoinbaseSerialized() {
        return coinbaseSerialized != null ? coinbaseSerialized.clone() : null;
    }

    public byte[] getCoinbaseMerkleProof() {
        return coinbaseMerkleProof.clone();
    }

    public boolean isComplete() {
        return coinbaseSerialized != null
                && coinbaseSerialized.length > 0
                && coinbaseMerkleProof.length > 0;
    }

    public CachedBtcBlockForFac withCoinbase(byte[] coinbase) {
        return new CachedBtcBlockForFac(
                blockHash, prevBlockHash, height, header80, coinbase, coinbaseMerkleProof);
    }

    public CachedBtcBlockForFac withCoinbaseMerkleProof(byte[] merkleProof) {
        return new CachedBtcBlockForFac(
                blockHash, prevBlockHash, height, header80, coinbaseSerialized, merkleProof);
    }

    /**
     * Reconstructs a minimal {@link BtcBlock} with the cached header and coinbase for proof building.
     */
    public BtcBlock toBtcBlock(NetworkParameters params) {
        if (!isComplete()) {
            throw new IllegalStateException("Cannot build BtcBlock from incomplete cache entry " + blockHash);
        }
        BtcTransaction coinbase = new BtcTransaction(params, coinbaseSerialized);
        List<BtcTransaction> txs = Collections.singletonList(coinbase);
        BtcBlock headerOnly = params.getDefaultSerializer().makeBlock(header80);
        return new BtcBlock(
                params,
                headerOnly.getVersion(),
                headerOnly.getPrevBlockHash(),
                headerOnly.getMerkleRoot(),
                headerOnly.getTimeSeconds(),
                headerOnly.getDifficultyTarget(),
                headerOnly.getNonce(),
                txs);
    }

    public static CachedBtcBlockForFac fromBtcBlock(
            BtcBlock block,
            int height,
            byte[] coinbaseMerkleProof) {
        Objects.requireNonNull(block, "block");
        byte[] header80 = block.cloneAsHeader().bitcoinSerialize();
        byte[] coinbase = block.getTransactions().isEmpty()
                ? null
                : block.getTransactions().get(0).bitcoinSerialize();
        return new CachedBtcBlockForFac(
                block.getHash(),
                block.getPrevBlockHash(),
                height,
                header80,
                coinbase,
                coinbaseMerkleProof);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CachedBtcBlockForFac)) {
            return false;
        }
        CachedBtcBlockForFac that = (CachedBtcBlockForFac) o;
        return height == that.height
                && blockHash.equals(that.blockHash)
                && prevBlockHash.equals(that.prevBlockHash)
                && Arrays.equals(header80, that.header80)
                && Arrays.equals(coinbaseSerialized, that.coinbaseSerialized)
                && Arrays.equals(coinbaseMerkleProof, that.coinbaseMerkleProof);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(blockHash, prevBlockHash, height);
        result = 31 * result + Arrays.hashCode(header80);
        result = 31 * result + Arrays.hashCode(coinbaseSerialized);
        result = 31 * result + Arrays.hashCode(coinbaseMerkleProof);
        return result;
    }
}
