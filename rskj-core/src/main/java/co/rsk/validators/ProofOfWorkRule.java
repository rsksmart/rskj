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

package co.rsk.validators;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.PartialMerkleTree;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.config.BridgeConstants;
import co.rsk.config.RskMiningConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.peg.utils.PartialMerkleTreeFormatUtils;
import co.rsk.util.DifficultyUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.ethereum.config.Constants;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Checks proof value against its boundary for the block header.
 */
@Component
public class ProofOfWorkRule implements BlockHeaderValidationRule, BlockValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");

    private final BridgeConstants bridgeConstants;
    private final Constants constants;
    private boolean fallbackMiningEnabled = true;

    @Autowired
    public ProofOfWorkRule(RskSystemProperties config) {
        this.bridgeConstants = config.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        this.constants = config.getBlockchainConfig().getCommonConstants();
    }

    public ProofOfWorkRule setFallbackMiningEnabled(boolean e) {
        fallbackMiningEnabled = e;
        return this;
    }

    @Override
    public boolean isValid(Block block) {
        return isValid(block.getHeader());
    }

    public static boolean isFallbackMiningPossible(Constants constants, BlockHeader header) {

        if (header.getNumber() >= constants.getEndOfFallbackMiningBlockNumber()) {
            return false;
        }

        if (header.getDifficulty().compareTo(constants.getFallbackMiningDifficulty()) > 0) {
            return false;
        }

        // If more than 10 minutes have elapsed, and difficulty is lower than 4 peta/s (config)
        // then private mining is still possible, but only after 10 minutes of inactivity or
        // previous block was privately mined.
        // This difficulty reset will be computed in DifficultyRule
        return true;
    }

    public boolean isFallbackMiningPossibleAndBlockSigned(BlockHeader header) {

        if (header.getBitcoinMergedMiningCoinbaseTransaction() != null) {
            return false;
        }

        if (header.getBitcoinMergedMiningMerkleProof() != null) {
            return false;
        }

        if (!fallbackMiningEnabled) {
            return false;
        }

        return isFallbackMiningPossible(constants, header);

    }

    @Override
    public boolean isValid(BlockHeader header) {
        // TODO: refactor this an move it to another class. Change the Global ProofOfWorkRule to AuthenticationRule.
        // TODO: Make ProofOfWorkRule one of the classes that inherits from AuthenticationRule.

        if (isFallbackMiningPossibleAndBlockSigned(header)) {
            boolean isValidFallbackSignature = validFallbackBlockSignature(constants, header, header.getBitcoinMergedMiningHeader());
            if (!isValidFallbackSignature) {
                logger.warn("Fallback signature failed. Header {}", header.getShortHash());
            }
            return isValidFallbackSignature;
        }

        co.rsk.bitcoinj.core.NetworkParameters bitcoinNetworkParameters = bridgeConstants.getBtcParams();
        byte[] bitcoinMergedMiningCoinbaseTransactionCompressed = header.getBitcoinMergedMiningCoinbaseTransaction();

        if (bitcoinMergedMiningCoinbaseTransactionCompressed == null) {
            logger.warn("Compressed coinbase transaction does not exist. Header {}", header.getShortHash());
            return false;
        }

        if (header.getBitcoinMergedMiningHeader() == null) {
            logger.warn("Bitcoin merged mining header does not exist. Header {}", header.getShortHash());
            return false;
        }

        byte[] pmtSerialized = header.getBitcoinMergedMiningMerkleProof();
        if (!PartialMerkleTreeFormatUtils.hasExpectedSize(pmtSerialized)) {
            logger.warn("Partial merkle tree does not have the expected size. Header {}", header.getShortHash());
            return false;
        }

        BtcBlock bitcoinMergedMiningBlock = bitcoinNetworkParameters.getDefaultSerializer().makeBlock(header.getBitcoinMergedMiningHeader());
        PartialMerkleTree bitcoinMergedMiningMerkleBranch  = new PartialMerkleTree(bitcoinNetworkParameters, pmtSerialized, 0);

        BigInteger target = DifficultyUtils.difficultyToTarget(header.getDifficulty());

        BigInteger bitcoinMergedMiningBlockHashBI = bitcoinMergedMiningBlock.getHash().toBigInteger();

        if (bitcoinMergedMiningBlockHashBI.compareTo(target) > 0) {
            logger.warn("Hash {} is higher than target {}", bitcoinMergedMiningBlockHashBI.toString(16), target.toString(16));
            return false;
        }

        byte[] bitcoinMergedMiningCoinbaseTransactionMidstate = new byte[RskMiningConstants.MIDSTATE_SIZE];
        System.arraycopy(bitcoinMergedMiningCoinbaseTransactionCompressed, 0, bitcoinMergedMiningCoinbaseTransactionMidstate, 8, RskMiningConstants.MIDSTATE_SIZE_TRIMMED);

        byte[] bitcoinMergedMiningCoinbaseTransactionTail = new byte[bitcoinMergedMiningCoinbaseTransactionCompressed.length - RskMiningConstants.MIDSTATE_SIZE_TRIMMED];
        System.arraycopy(bitcoinMergedMiningCoinbaseTransactionCompressed, RskMiningConstants.MIDSTATE_SIZE_TRIMMED,
                bitcoinMergedMiningCoinbaseTransactionTail, 0, bitcoinMergedMiningCoinbaseTransactionTail.length);

        byte[] expectedCoinbaseMessageBytes = org.spongycastle.util.Arrays.concatenate(RskMiningConstants.RSK_TAG, header.getHashForMergedMining());


        List<Byte> bitcoinMergedMiningCoinbaseTransactionTailAsList = Arrays.asList(ArrayUtils.toObject(bitcoinMergedMiningCoinbaseTransactionTail));
        List<Byte> expectedCoinbaseMessageBytesAsList = Arrays.asList(ArrayUtils.toObject(expectedCoinbaseMessageBytes));

        int rskTagPosition = Collections.lastIndexOfSubList(bitcoinMergedMiningCoinbaseTransactionTailAsList, expectedCoinbaseMessageBytesAsList);
        if (rskTagPosition == -1) {
            logger.warn("bitcoin coinbase transaction tail message does not contain expected RSKBLOCK:RskBlockHeaderHash. Expected: {} . Actual: {} .", Arrays.toString(expectedCoinbaseMessageBytes), Arrays.toString(bitcoinMergedMiningCoinbaseTransactionTail));
            return false;
        }

        /*
        * We check that the there is no other block before the rsk tag, to avoid a possible malleability attack:
        * If we have a mid state with 10 blocks, and the rsk tag, we can also have
        * another mid state with 9 blocks, 64bytes + the rsk tag, giving us two blocks with different hashes but the same spv proof.
        * */
        if (rskTagPosition >= 64) {
            logger.warn("bitcoin coinbase transaction tag position is bigger than expected 64. Actual: {}.", Integer.toString(rskTagPosition));
            return false;
        }

        List<Byte> rskTagAsList = Arrays.asList(ArrayUtils.toObject(RskMiningConstants.RSK_TAG));
        int lastTag = Collections.lastIndexOfSubList(bitcoinMergedMiningCoinbaseTransactionTailAsList, rskTagAsList);
        if (rskTagPosition !=lastTag) {
            logger.warn("The valid RSK tag is not the last RSK tag. Tail: {}.", Arrays.toString(bitcoinMergedMiningCoinbaseTransactionTail));
            return false;
        }

        int remainingByteCount = bitcoinMergedMiningCoinbaseTransactionTail.length -
                rskTagPosition -
                RskMiningConstants.RSK_TAG.length -
                RskMiningConstants.BLOCK_HEADER_HASH_SIZE;

        if (remainingByteCount > RskMiningConstants.MAX_BYTES_AFTER_MERGED_MINING_HASH) {
            logger.warn("More than 128 bytes after RSK tag");
            return false;
        }

        SHA256Digest digest = new SHA256Digest(bitcoinMergedMiningCoinbaseTransactionMidstate);
        digest.update(bitcoinMergedMiningCoinbaseTransactionTail,0,bitcoinMergedMiningCoinbaseTransactionTail.length);
        byte[] bitcoinMergedMiningCoinbaseTransactionOneRoundOfHash = new byte[32];
        digest.doFinal(bitcoinMergedMiningCoinbaseTransactionOneRoundOfHash, 0);
        Sha256Hash bitcoinMergedMiningCoinbaseTransactionHash = Sha256Hash.wrapReversed(Sha256Hash.hash(bitcoinMergedMiningCoinbaseTransactionOneRoundOfHash));

        List<Sha256Hash> txHashesInTheMerkleBranch = new ArrayList<>();
        Sha256Hash merkleRoot = bitcoinMergedMiningMerkleBranch.getTxnHashAndMerkleRoot(txHashesInTheMerkleBranch);
        if (!merkleRoot.equals(bitcoinMergedMiningBlock.getMerkleRoot())) {
            logger.warn("bitcoin merkle root of bitcoin block does not match the merkle root of merkle branch");
            return false;
        }
        if (!txHashesInTheMerkleBranch.contains(bitcoinMergedMiningCoinbaseTransactionHash)) {
            logger.warn("bitcoin coinbase transaction {} not included in merkle branch", bitcoinMergedMiningCoinbaseTransactionHash);
            return false;
        }

        return true;
    }

    public static boolean validFallbackBlockSignature(Constants constants, BlockHeader header, byte[] signatureBytesRLP) {

        if (header.getBitcoinMergedMiningCoinbaseTransaction() != null) {
            return false;
        }

        if (header.getBitcoinMergedMiningMerkleProof() != null) {
            return false;
        }

        byte[] fallbackMiningPubKeyBytes;
        boolean isEvenBlockNumber = header.getNumber() % 2 == 0;
        if (isEvenBlockNumber) {
            fallbackMiningPubKeyBytes = constants.getFallbackMiningPubKey0();
        } else {
            fallbackMiningPubKeyBytes = constants.getFallbackMiningPubKey1();
        }

        ECKey fallbackMiningPubKey = ECKey.fromPublicOnly(fallbackMiningPubKeyBytes);
        List<RLPElement> signatureRLP = (RLPList) RLP.decode2(signatureBytesRLP).get(0);
        if (signatureRLP.size() != 3) {
            return false;
        }

        byte[] v = signatureRLP.get(0).getRLPData();
        byte[] r = signatureRLP.get(1).getRLPData();
        byte[] s = signatureRLP.get(2).getRLPData();

        ECKey.ECDSASignature signature = ECKey.ECDSASignature.fromComponents(r, s, v[0]);

        return fallbackMiningPubKey.verify(header.getHashForMergedMining(), signature);
    }
}
