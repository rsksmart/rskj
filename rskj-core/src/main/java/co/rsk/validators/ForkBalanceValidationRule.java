/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package co.rsk.validators;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.core.bc.FacBlockHashesCache;
import co.rsk.core.bc.FacPerfLogger;
import co.rsk.crypto.Keccak256;
import co.rsk.mine.ForkBalanceParentCoinbaseProof;
import co.rsk.mine.ForkBalanceProofUtils;
import co.rsk.peg.constants.BridgeConstants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Validates fork-balance proof for RSK blocks with header version 3 after {@link ConsensusRule#RSKIP555}.
 */
public class ForkBalanceValidationRule implements BlockValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");

    private final ActivationConfig activationConfig;
    private final NetworkParameters btcParams;
    @Nullable
    private final FacBlockHashesCache facBlockHashesCache;

    public ForkBalanceValidationRule(
            ActivationConfig activationConfig,
            BridgeConstants bridgeConstants,
            @Nullable FacBlockHashesCache facBlockHashesCache) {
        this.activationConfig = activationConfig;
        this.btcParams = bridgeConstants.getBtcParams();
        this.facBlockHashesCache = facBlockHashesCache;
    }

    @Override
    public boolean isValid(Block block) {
        long blockNumber = block.getNumber();
        if (!activationConfig.isActive(ConsensusRule.RSKIP555, blockNumber)) {
            return true;
        }

        BlockHeader header = block.getHeader();
        if (header.getVersion() != (byte) 0x03) {
            FacPerfLogger.logForkBalanceProofRejected(
                    blockNumber, header.getPrintableHash(), "HEADER_VERSION_NOT_V3", null);
            return false;
        }

        byte[] forkBalanceProof = header.getForkBalanceProof();
        String printableHash = header.getPrintableHash();

        if (forkBalanceProof == null || forkBalanceProof.length == 0) {
            FacPerfLogger.logForkBalanceProofRejected(blockNumber, printableHash, "MISSING_PROOF", null);
            return false;
        }
        if (ForkBalanceProofUtils.isDefaultForkBalancePlaceholder(forkBalanceProof)) {
            byte[] mmHeader = header.getBitcoinMergedMiningHeader();
            if (mmHeader != null && mmHeader.length > 0) {
                FacPerfLogger.logForkBalanceProofRejected(
                        blockNumber, printableHash, "PLACEHOLDER_WITH_MERGED_MINING", null);
                return false;
            }
            return true;
        }

        ForkBalanceProofUtils.ForkBalanceProofDecoded decoded;
        try {
            decoded = ForkBalanceProofUtils.decodeForkBalanceProof(forkBalanceProof);
        } catch (IllegalArgumentException ex) {
            FacPerfLogger.logForkBalanceProofRejected(blockNumber, printableHash, "INVALID_RLP", ex.getMessage());
            return false;
        }

        byte[] mmHeader = header.getBitcoinMergedMiningHeader();
        byte[] mmCoinbase = header.getBitcoinMergedMiningCoinbaseTransaction();
        if (mmHeader == null || mmHeader.length == 0 || mmCoinbase == null || mmCoinbase.length == 0) {
            FacPerfLogger.logForkBalanceProofRejected(blockNumber, printableHash, "MISSING_MERGED_MINING_FIELDS", null);
            return false;
        }

        if (!checkParentHeaderMatchesMergedPrev(mmHeader, decoded.getParentBtcHeader())) {
            FacPerfLogger.logForkBalanceProofRejected(blockNumber, printableHash, "PARENT_BTC_MISMATCH", null);
            return false;
        }

        if (!checkParentCoinbaseMerkle(blockNumber, printableHash, decoded)) {
            return false;
        }

        List<Keccak256> recentMerged = mergedMiningHashesForProofType();
        if (!checkProofTypeAgainstParentCoinbase(decoded.getProofType(), decoded.getCoinbaseLastBytes(), recentMerged)) {
            FacPerfLogger.logForkBalanceProofRejected(
                    blockNumber,
                    printableHash,
                    "PROOF_TYPE_MISMATCH",
                    "declared=" + (decoded.getProofType() & 0xFF) + " recentMergedCount=" + recentMerged.size());
            return false;
        }

        return true;
    }

    private List<Keccak256> mergedMiningHashesForProofType() {
        if (facBlockHashesCache == null) {
            return Collections.emptyList();
        }
        return facBlockHashesCache.getMergedMiningHashesForProofType();
    }

    private boolean checkParentHeaderMatchesMergedPrev(byte[] mergedMiningHeader80, byte[] parentBtcHeader80) {
        try {
            BtcBlock merged = btcParams.getDefaultSerializer().makeBlock(mergedMiningHeader80);
            BtcBlock parent = btcParams.getDefaultSerializer().makeBlock(parentBtcHeader80);
            return merged.getPrevBlockHash().equals(parent.getHash());
        } catch (RuntimeException e) {
            logger.warn("Failed to parse BTC headers for fork-balance link check: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkParentCoinbaseMerkle(
            long blockNumber,
            String printableHash,
            ForkBalanceProofUtils.ForkBalanceProofDecoded decoded) {
        byte[] coinbaseLastBytes = decoded.getCoinbaseLastBytes();
        byte[] coinbaseHash = decoded.getCoinbaseHash();
        byte[] midstateProof = decoded.getMidStateProof();
        byte[] coinbaseProof = decoded.getCoinbaseProof();
        if (coinbaseLastBytes.length == 0
                || coinbaseHash.length != Sha256Hash.LENGTH
                || midstateProof.length == 0
                || coinbaseProof.length == 0) {
            FacPerfLogger.logForkBalanceProofRejected(
                    blockNumber, printableHash, "EMPTY_COINBASE_MERKLE_PROOF", null);
            return false;
        }
        if (!ForkBalanceParentCoinbaseProof.verifyCoinbaseHash(midstateProof, coinbaseLastBytes, coinbaseHash)) {
            FacPerfLogger.logForkBalanceProofRejected(
                    blockNumber, printableHash, "COINBASE_HASH_COMMITMENT_INVALID", null);
            return false;
        }
        try {
            BtcBlock parentHeaderOnly = btcParams.getDefaultSerializer().makeBlock(decoded.getParentBtcHeader());
            Sha256Hash merkleRoot = parentHeaderOnly.getMerkleRoot();
            Sha256Hash coinbaseTxId = ForkBalanceParentCoinbaseProof.coinbaseHashFromWireBytes(coinbaseHash);

            MerkleProofValidator mpValidator;
            if (activationConfig.isActive(ConsensusRule.RSKIP92, blockNumber)) {
                boolean isRskip180Enabled = activationConfig.isActive(ConsensusRule.RSKIP180, blockNumber);
                mpValidator = new Rskip92MerkleProofValidator(coinbaseProof, isRskip180Enabled);
            } else {
                mpValidator = new GenesisMerkleProofValidator(btcParams, coinbaseProof);
            }
            if (!mpValidator.isValid(merkleRoot, coinbaseTxId)) {
                FacPerfLogger.logForkBalanceProofRejected(
                        blockNumber, printableHash, "COINBASE_MERKLE_INVALID", null);
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            FacPerfLogger.logForkBalanceProofRejected(
                    blockNumber, printableHash, "COINBASE_MERKLE_EXCEPTION", e.getMessage());
            return false;
        }
    }

    private boolean checkProofTypeAgainstParentCoinbase(
            byte declared,
            byte[] coinbaseLastBytes,
            List<Keccak256> recentMerged) {
        byte expected = ForkBalanceProofUtils.proofTypeIdentificationFromCoinbaseSuffix(coinbaseLastBytes, recentMerged);
        return declared == expected;
    }
}
