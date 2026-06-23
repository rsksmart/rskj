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

import co.rsk.mine.ForkBalanceProofUtils;
import org.ethereum.core.Block;

/**
 * Maps fork-balance proof type (header v3) to {@code facEvidenceValue}.
 */
public final class FacEvidenceCalculator {

    private FacEvidenceCalculator() {
    }

    /**
     * Type 2 → 0, type 1 → -1, type 0 → +1. Non–v3 headers and undecodable proofs → 0.
     */
    public static int facEvidenceValueFromBlock(Block block) {
        if (block.getHeader().getVersion() != (byte) 0x03) {
            return 0;
        }
        byte[] fbp = block.getHeader().getForkBalanceProof();
        if (fbp == null || fbp.length == 0 || ForkBalanceProofUtils.isDefaultForkBalancePlaceholder(fbp)) {
            return 0;
        }
        try {
            byte t = ForkBalanceProofUtils.decodeForkBalanceProof(fbp).getProofType();
            return switch (t) {
                case 0 -> 1;
                case 1 -> -1;
                default -> 0;
            };
        } catch (IllegalArgumentException ex) {
            return 0;
        }
    }
}
