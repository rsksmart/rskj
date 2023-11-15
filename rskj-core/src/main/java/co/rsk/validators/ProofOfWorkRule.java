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
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.config.BridgeConstants;
import co.rsk.config.RskMiningConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.util.DifficultyUtils;
import co.rsk.util.ListArrayUtil;
import com.google.common.annotations.VisibleForTesting;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.util.Pack;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.crypto.signature.Secp256k1;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Checks proof value against its boundary for the block header.
 */
public class ProofOfWorkRule implements BlockHeaderValidationRule, BlockValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");
    private static final BigInteger SECP256K1N_HALF = Constants.getSECP256K1N().divide(BigInteger.valueOf(2));

    private final BridgeConstants bridgeConstants;
    private final Constants constants;
    private final ActivationConfig activationConfig;
    private boolean fallbackMiningEnabled = true;

    public ProofOfWorkRule(RskSystemProperties config) {
        this.activationConfig = config.getActivationConfig();
        this.constants = config.getNetworkConstants();
        this.bridgeConstants = constants.getBridgeConstants();
    }

    @VisibleForTesting
    public ProofOfWorkRule setFallbackMiningEnabled(boolean e) {
        fallbackMiningEnabled = e;
        return this;
    }

    @Override
    public boolean isValid(Block block) {
        return isValid(block.getHeader());
    }

    private boolean isFallbackMiningPossible(BlockHeader header) {
        if (activationConfig.isActive(ConsensusRule.RSKIP98, header.getNumber())) {
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

    private boolean isFallbackMiningPossibleAndBlockSigned(BlockHeader header) {

        if (header.getBitcoinMergedMiningCoinbaseTransaction() != null) {
            return false;
        }

        byte[] merkleProof = header.getBitcoinMergedMiningMerkleProof();
        if (merkleProof != null && merkleProof.length > 0) {
            return false;
        }

        if (!fallbackMiningEnabled) {
            return false;
        }

        return isFallbackMiningPossible(header);

    }

    @Override
    public boolean isValid(BlockHeader header) {
        // TODO: refactor this an move it to another class. Change the Global ProofOfWorkRule to AuthenticationRule.
        // TODO: Make ProofOfWorkRule one of the classes that inherits from AuthenticationRule.
        if (isFallbackMiningPossibleAndBlockSigned(header)) {
            boolean isValidFallbackSignature = validFallbackBlockSignature(constants, header, header.getBitcoinMergedMiningHeader());
            if (!isValidFallbackSignature) {
                logger.warn("Fallback signature failed. Header {}", header.getPrintableHash());
            }
            return isValidFallbackSignature;
        }

        co.rsk.bitcoinj.core.NetworkParameters bitcoinNetworkParameters = bridgeConstants.getBtcParams();
        MerkleProofValidator mpValidator;
        try {
            if (activationConfig.isActive(ConsensusRule.RSKIP92, header.getNumber())) {
                boolean isRskip180Enabled = activationConfig.isActive(ConsensusRule.RSKIP180, header.getNumber());
                mpValidator = new Rskip92MerkleProofValidator(header.getBitcoinMergedMiningMerkleProof(), isRskip180Enabled);
            } else {
                mpValidator = new GenesisMerkleProofValidator(bitcoinNetworkParameters, header.getBitcoinMergedMiningMerkleProof());
            }
        } catch (RuntimeException ex) {
            logger.warn("Merkle proof can't be validated. Header {}", header.getPrintableHash(), ex);
            return false;
        }

        byte[] bitcoinMergedMiningCoinbaseTransactionCompressed = header.getBitcoinMergedMiningCoinbaseTransaction();

        if (bitcoinMergedMiningCoinbaseTransactionCompressed == null) {
            logger.warn("Compressed coinbase transaction does not exist. Header {}", header.getPrintableHash());
            return false;
        }

        if (header.getBitcoinMergedMiningHeader() == null) {
            logger.warn("Bitcoin merged mining header does not exist. Header {}", header.getPrintableHash());
            return false;
        }

        BtcBlock bitcoinMergedMiningBlock = bitcoinNetworkParameters.getDefaultSerializer().makeBlock(header.getBitcoinMergedMiningHeader());

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

        byte[] expectedCoinbaseMessageBytes = org.bouncycastle.util.Arrays.concatenate(RskMiningConstants.RSK_TAG, header.getHashForMergedMining());

        int rskTagPosition = ListArrayUtil.lastIndexOfSubList(bitcoinMergedMiningCoinbaseTransactionTail, expectedCoinbaseMessageBytes);
        if (rskTagPosition == -1) {
            logger.warn("bitcoin coinbase transaction tail message does not contain expected" +
                    " RSKBLOCK:RskBlockHeaderHash. Expected: {} . Actual: {} .",
                    Arrays.toString(expectedCoinbaseMessageBytes),
                    Arrays.toString(bitcoinMergedMiningCoinbaseTransactionTail));
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

        int lastTag = ListArrayUtil.lastIndexOfSubList(bitcoinMergedMiningCoinbaseTransactionTail, RskMiningConstants.RSK_TAG);
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

        // TODO test
        long byteCount = Pack.bigEndianToLong(bitcoinMergedMiningCoinbaseTransactionMidstate, 8);
        long coinbaseLength = bitcoinMergedMiningCoinbaseTransactionTail.length + byteCount;
        if (coinbaseLength <= 64) {
            logger.warn("Coinbase transaction must always be greater than 64-bytes long. But it was: {}", coinbaseLength);
            return false;
        }

        SHA256Digest digest = new SHA256Digest(bitcoinMergedMiningCoinbaseTransactionMidstate);
        digest.update(bitcoinMergedMiningCoinbaseTransactionTail,0,bitcoinMergedMiningCoinbaseTransactionTail.length);
        byte[] bitcoinMergedMiningCoinbaseTransactionOneRoundOfHash = new byte[32];
        digest.doFinal(bitcoinMergedMiningCoinbaseTransactionOneRoundOfHash, 0);
        Sha256Hash bitcoinMergedMiningCoinbaseTransactionHash = Sha256Hash.wrapReversed(Sha256Hash.hash(bitcoinMergedMiningCoinbaseTransactionOneRoundOfHash));

        if (!mpValidator.isValid(bitcoinMergedMiningBlock.getMerkleRoot(), bitcoinMergedMiningCoinbaseTransactionHash)) {
            logger.warn("bitcoin merkle branch doesn't match coinbase and state root");
            return false;
        }

        return true;
    }

    private static boolean validFallbackBlockSignature(Constants constants, BlockHeader header, byte[] signatureBytesRLP) {

        byte[] fallbackMiningPubKeyBytes;
        boolean isEvenBlockNumber = header.getNumber() % 2 == 0;
        if (isEvenBlockNumber) {
            fallbackMiningPubKeyBytes = constants.getFallbackMiningPubKey0();
        } else {
            fallbackMiningPubKeyBytes = constants.getFallbackMiningPubKey1();
        }

        ECKey fallbackMiningPubKey = ECKey.fromPublicOnly(fallbackMiningPubKeyBytes);

        RLPList signatureRLP = RLP.decodeList(signatureBytesRLP);

        if (signatureRLP.size() != 3) {
            return false;
        }

        byte[] v = signatureRLP.get(0).getRLPData();
        byte[] r = signatureRLP.get(1).getRLPData();
        byte[] s = signatureRLP.get(2).getRLPData();

        if (v == null || v.length != 1) {
            return false;
        }

        ECDSASignature signature = ECDSASignature.fromComponents(r, s, v[0]);

        if (!Arrays.equals(r, signature.getR().toByteArray())) {
            return false;
        }

        if (!Arrays.equals(s, signature.getS().toByteArray())) {
            return false;
        }

        if (signature.getV() > 31 || signature.getV() < 27) {
            return false;
        }

        if (signature.getS().compareTo(SECP256K1N_HALF) >= 0) {
            return false;
        }

        ECKey pub = Secp256k1.getInstance().recoverFromSignature(signature.getV() - 27, signature, header.getHashForMergedMining(), false);

        return pub.getPubKeyPoint().equals(fallbackMiningPubKey.getPubKeyPoint());
    }
}