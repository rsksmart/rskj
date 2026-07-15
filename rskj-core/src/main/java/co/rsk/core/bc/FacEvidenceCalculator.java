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

import co.rsk.crypto.Keccak256;
import co.rsk.mine.ForkBalanceProofUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Maps fork-balance proof type (derived locally after proof validation) to {@code facEvidenceValue}.
 */
public final class FacEvidenceCalculator {

    private FacEvidenceCalculator() {
    }

    /**
     * Type 2 → 0, type 1 → -1, type 0 → +1.
     */
    public static int facEvidenceValueFromProofType(byte proofType) {
        return switch (proofType) {
            case 0 -> 1;
            case 1 -> -1;
            default -> 0;
        };
    }

    /**
     * Derives proof type from the block's fork-balance proof suffix against recent merged-mining hashes.
     * Non–v3 headers, missing/placeholder/undecodable proofs → type {@code 2} (neutral evidence).
     */
    public static byte proofTypeFromBlock(Block block, @Nullable List<Keccak256> recentMergedMiningHashes) {
        return proofTypeFromHeader(block.getHeader(), recentMergedMiningHashes);
    }

    /**
     * Same as {@link #proofTypeFromBlock} for a header (e.g. uncle list entries).
     */
    public static byte proofTypeFromHeader(BlockHeader header, @Nullable List<Keccak256> recentMergedMiningHashes) {
        if (header.getVersion() != (byte) 0x03) {
            return 2;
        }
        byte[] fbp = header.getForkBalanceProof();
        if (fbp == null || fbp.length == 0 || ForkBalanceProofUtils.isDefaultForkBalancePlaceholder(fbp)) {
            return 2;
        }
        try {
            ForkBalanceProofUtils.ForkBalanceProofDecoded decoded =
                    ForkBalanceProofUtils.decodeForkBalanceProof(fbp);
            List<Keccak256> hashes = recentMergedMiningHashes != null
                    ? recentMergedMiningHashes
                    : Collections.emptyList();
            return ForkBalanceProofUtils.proofTypeIdentificationFromCoinbaseSuffix(
                    decoded.getCoinbaseLastBytes(), hashes);
        } catch (IllegalArgumentException ex) {
            return 2;
        }
    }
}
