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
import org.ethereum.core.ImportResult;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Structured FAC import logging ({@code fac-perf} / {@code fac-perf-fail}).
 */
public final class FacPerfLogger {

    private static final Logger LOG = LoggerFactory.getLogger("blockchain");

    private FacPerfLogger() {
    }

    public static void logForkBalanceProofRejected(
            long blockNumber,
            String printableHash,
            String reason,
            @Nullable String detail) {
        if (detail == null || detail.isEmpty()) {
            LOG.info(
                    "fac-perf-fail blockNumber={} blockHash={} category=forkBalanceProof reason={}",
                    blockNumber,
                    printableHash,
                    reason);
        } else {
            LOG.info(
                    "fac-perf-fail blockNumber={} blockHash={} category=forkBalanceProof reason={} detail={}",
                    blockNumber,
                    printableHash,
                    reason,
                    detail);
        }
    }

    public static void logBlockValidatorRejected(Block block) {
        LOG.info(
                "fac-perf-fail blockNumber={} blockHash={} category=blockValidator reason=PRECHECK_FAILED",
                block.getNumber(),
                block.getPrintableHash());
    }

    public static void logBlockConnected(
            Block block,
            ImportResult importResult,
            @Nullable BlockFacTracker blockFacTracker,
            @Nullable BlockStore blockStore) {
        int headerVersion = block.getHeader().getVersion() & 0xFF;
        String proofType = forkBalanceProofTypeLabel(block);

        BlockFacFields fields = blockFacTracker != null ? blockFacTracker.get(block.getHash()) : null;
        if (fields == null) {
            LOG.info(
                    "fac-perf blockNumber={} blockHash={} headerVersion=0x{} forkBalanceProofType={} "
                            + "importResult={} facEvidenceValue= facSafetyLevel= lastSafeBlockNumber= isLastSafeBlock=false",
                    block.getNumber(),
                    block.getPrintableHash(),
                    Integer.toHexString(headerVersion),
                    proofType,
                    importResult);
            return;
        }

        boolean isLastSafe = fields.getLastSafeBlock() != null
                && fields.getLastSafeBlock().equals(block.getHash());
        Long lastSafeNumber = resolveBlockNumber(blockStore, fields.getLastSafeBlock());

        LOG.info(
                "fac-perf blockNumber={} blockHash={} headerVersion=0x{} forkBalanceProofType={} "
                        + "importResult={} facEvidenceValue={} facSafetyLevel={} lastSafeBlockNumber={} isLastSafeBlock={}",
                block.getNumber(),
                block.getPrintableHash(),
                Integer.toHexString(headerVersion),
                proofType,
                importResult,
                fields.getFacEvidenceValue(),
                fields.getFacSafetyLevel(),
                lastSafeNumber != null ? lastSafeNumber : "null",
                isLastSafe);
    }

    @Nullable
    private static Long resolveBlockNumber(@Nullable BlockStore blockStore, @Nullable co.rsk.crypto.Keccak256 hash) {
        if (blockStore == null || hash == null) {
            return null;
        }
        Block b = blockStore.getBlockByHash(hash.getBytes());
        return b != null ? b.getNumber() : null;
    }

    private static String forkBalanceProofTypeLabel(Block block) {
        if (block.getHeader().getVersion() != (byte) 0x03) {
            return "n/a";
        }
        byte[] fbp = block.getHeader().getForkBalanceProof();
        if (fbp == null || fbp.length == 0) {
            return "missing";
        }
        if (ForkBalanceProofUtils.isDefaultForkBalancePlaceholder(fbp)) {
            return "placeholder";
        }
        try {
            return String.valueOf(ForkBalanceProofUtils.decodeForkBalanceProof(fbp).getProofType() & 0xFF);
        } catch (IllegalArgumentException ex) {
            return "invalid";
        }
    }
}
