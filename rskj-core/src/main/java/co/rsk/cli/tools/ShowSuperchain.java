/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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
package co.rsk.cli.tools;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.MessageSerializer;
import co.rsk.cli.PicoCliToolRskContextAware;
import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.util.DifficultyUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
import java.util.List;

/**
 * The entry point for "show-superchain" CLI tool
 * This is an experimental/unsupported tool
 */
@CommandLine.Command(name = "show-superchain", mixinStandardHelpOptions = true, version = "show-superchain 1.0",
        description = "Show superchain blocks info")
public class ShowSuperchain extends PicoCliToolRskContextAware {

    private static final Logger logger = LoggerFactory.getLogger(ShowSuperchain.class);

    @CommandLine.Option(names = {"-fb", "--fromBlock"}, description = "From block number")
    private Long fromBlockNumber;

    @CommandLine.Option(names = {"-tb", "--toBlock"}, description = "To block number")
    private Long toBlockNumber;

    @CommandLine.Option(names = {"-lb", "--lastBlocks"}, description = "Number of last blocks")
    private Long lastBlocks;

    @CommandLine.Option(names = {"-sa", "--scanUncles"}, description = "To scan or not uncle blocks")
    private Boolean scanUncles = Boolean.FALSE;

    @CommandLine.Option(names = {"-n"}, description = "Number that specifies for a super block at least how many times PoW should be above block target difficulty")
    private Long n = 20L;

    public static void main(String[] args) throws IOException {
        create(MethodHandles.lookup().lookupClass()).execute(args);
    }

    @Override
    public Integer call() throws IOException {
        BlockStore blockStore = ctx.getBlockStore();
        BridgeConstants bridgeConstants = ctx.getRskSystemProperties().getNetworkConstants().getBridgeConstants();

        if (lastBlocks == null) {
            if (fromBlockNumber == null) {
                fromBlockNumber = blockStore.getMinNumber();
            }
            if (toBlockNumber == null) {
                toBlockNumber = blockStore.getMaxNumber();
            }
        } else {
            toBlockNumber = blockStore.getMaxNumber();
            fromBlockNumber = Math.max(blockStore.getMinNumber(), toBlockNumber - lastBlocks + 1);
        }

        if (fromBlockNumber > toBlockNumber) {
            printError("From block number should be less than to block number");
            return 1;
        }

        long startTime = System.currentTimeMillis();

        int code = showSuperchainInfo(bridgeConstants, blockStore);

        long endTime = System.currentTimeMillis();

        printInfo("Duration: " + (endTime - startTime) + " millis");

        return code;
    }

    private int showSuperchainInfo(BridgeConstants bridgeConstants, BlockStore blockStore) {
        BigInteger nBI = BigInteger.valueOf(this.n);
        MessageSerializer serializer = bridgeConstants
                .getBtcParams()
                .getDefaultSerializer();

        long superBlockCount = 0;
        long processedBlockCount = 0;
        long maxEpochLength = 0;
        long epochLengths = 0;
        long curUncleCount = 0;
        long maxUncleCount = -1;
        long unknownPoWFactorCount = 0;
        BigInteger maxPowFactor = BigInteger.ZERO;
        BigInteger powFactors = BigInteger.ZERO;
        Block lastSuperBlock = null;

        Block block = blockStore.getChainBlockByNumber(toBlockNumber);
        if (block == null) {
            printInfo("Block number " + toBlockNumber + " not found");
            return 1;
        }

        printInfo("Scanning [{}; {}] block range ({} blocks); scan uncles: {}", fromBlockNumber, toBlockNumber, toBlockNumber - fromBlockNumber + 1, scanUncles);

        double avgEpochLength;
        BigInteger avgPowFactor;
        while (block.getNumber() >= fromBlockNumber) {
            BlockHeader header = block.getHeader();
            long blockNum = header.getNumber();
            Keccak256 parentHash = header.getParentHash();

            BigInteger powFactorBI = getPowFactor(serializer, header);
            if (powFactorBI == null) {
                unknownPoWFactorCount++;
            } else {
                if (powFactorBI.compareTo(maxPowFactor) > 0) {
                    maxPowFactor = powFactorBI;
                }
                powFactors = powFactors.add(powFactorBI);

                if (powFactorBI.compareTo(nBI) >= 0) {
                    superBlockCount++;

                    if (lastSuperBlock != null) {
                        long epochLength = lastSuperBlock.getNumber() - blockNum;
                        if (epochLength > maxEpochLength) {
                            maxEpochLength = epochLength;
                        }
                        epochLengths += epochLength;
                    }

                    if (scanUncles) {
                        if (curUncleCount > maxUncleCount) {
                            maxUncleCount = curUncleCount;
                        }
                        curUncleCount = 0;
                    }

                    lastSuperBlock = block;
                } else {
                    if (scanUncles) {
                        List<Block> blockList = blockStore.getChainBlocksByNumber(blockNum);
                        for (Block b : blockList) {
                            if (b.getHash().equals(header.getHash()) || !b.getParentHash().equals(parentHash)) {
                                continue;
                            }
                            BigInteger unclePowFactor = getPowFactor(serializer, b.getHeader());
                            if (unclePowFactor != null && unclePowFactor.compareTo(nBI) >= 0) {
                                curUncleCount++;
                            }
                        }
                    }
                }
            }

            processedBlockCount++;

            block = blockStore.getBlockByHash(parentHash.getBytes());
            if (block == null) {
                printInfo("Parent block with number {} not found", blockNum - 1);
                break;
            }

            if (processedBlockCount % 10000 == 0) {
                avgEpochLength = 1.0 * epochLengths / superBlockCount;
                avgPowFactor = powFactors.divide(BigInteger.valueOf(processedBlockCount));

                long processedPercents = 100 * (toBlockNumber - blockNum) / (toBlockNumber - fromBlockNumber);
                printInfo("Processed blocks {} ({}%); super blocks: {}; avg epoch length: {}; max epoch length: {}; avg PoW factor: {}; max PoW factor: {}; unknown PoW factor: {}; max uncle count: {}",
                        processedBlockCount,
                        processedPercents,
                        superBlockCount,
                        avgEpochLength,
                        maxEpochLength,
                        avgPowFactor,
                        maxPowFactor,
                        unknownPoWFactorCount,
                        maxUncleCount);
            }
        }

        avgEpochLength = 1.0 * epochLengths / superBlockCount;
        avgPowFactor = powFactors.divide(BigInteger.valueOf(processedBlockCount));

        printInfo("Processed blocks {}; super blocks: {}; avg epoch length: {}; max epoch length: {}; avg PoW factor: {}; max PoW factor: {}; unknown PoW factor: {}; max uncle count: {}",
                processedBlockCount,
                superBlockCount,
                avgEpochLength,
                maxEpochLength,
                avgPowFactor,
                maxPowFactor,
                unknownPoWFactorCount,
                maxUncleCount);

        return 0;
    }

    /**
     * PoW factor, which is defined as the ratio between the Rootstock block difficulty target
     * and the associated bitcoin merged mining block hash
     *
     * @param serializer bitcoin message serializer/deserializer
     * @param header block header
     * @return PoW factor for a Rootstock block header
     */
    @Nullable
    private static BigInteger getPowFactor(MessageSerializer serializer, BlockHeader header) {
        byte[] bitcoinMergedMiningHeaderBytes = header.getBitcoinMergedMiningHeader();
        if (bitcoinMergedMiningHeaderBytes == null) {
            logger.info("Bitcoin merged mining header is null for block number {}", header.getNumber());
            return null;
        }

        BlockDifficulty difficulty = header.getDifficulty();
        BigInteger difficultyTargetBI = DifficultyUtils.difficultyToTarget(difficulty);

        BtcBlock bitcoinMergedMiningBlock;
        try {
            bitcoinMergedMiningBlock = serializer.makeBlock(bitcoinMergedMiningHeaderBytes);
        } catch (Exception e) {
            logger.error("Error deserializing bitcoin merged mining block for header #{}: {}", header.getNumber(), e.getMessage());
            return null;
        }
        BigInteger bitcoinMergedMiningBlockHashBI = bitcoinMergedMiningBlock.getHash().toBigInteger();

        return difficultyTargetBI.divide(bitcoinMergedMiningBlockHashBI);
    }
}
