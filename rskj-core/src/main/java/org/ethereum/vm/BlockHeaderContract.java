/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.vm;

import co.rsk.config.RskMiningConstants;
import co.rsk.core.Coin;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.util.BIUtil;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;

import static org.ethereum.util.ByteUtil.parseBytes;

/**
 * @author Diego Masini
 *
 * Extracts coinbase, minimum gas price, block hash, merged mining tags, bitcoin header, gas limit, gas used or RSK difficulty from block.
 */
public class BlockHeaderContract extends PrecompiledContracts.PrecompiledContract {
    private static final Logger logger = LoggerFactory.getLogger("BlockHeaderContract");

    private static final long MAX_DEPTH = 4000;
    private BlockStore blockStore;
    private Block currentExecutionBlock;

    private Block getBlock(long blockDepth) {
        // If blockDepth is bigger or equal to the max depth, return null.
        if (blockDepth >= MAX_DEPTH) {
            return null;
        }

        return blockStore.getBlockAtDepthStartingAt(blockDepth, currentExecutionBlock.getParentHash().getBytes());
    }

    public byte[] getCoinbaseAddress(Object[] args) {
        int blockDepth = ((BigInteger) args[0]).intValue();

        Block block = getBlock(blockDepth);
        if (block == null) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        return block.getCoinbase().getBytes();
    }

    public byte[] getMinGasPrice(Object[] args) {
        int blockDepth = ((BigInteger) args[0]).intValue();

        Block block = getBlock(blockDepth);
        if (block == null) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        Coin minGasPrice = block.getMinimumGasPrice();
        if (minGasPrice == null) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }
        return minGasPrice.getBytes();
    }

    public byte[] getBlockHash(Object[] args) {
        int blockDepth = ((BigInteger) args[0]).intValue();

        Block block = getBlock(blockDepth);
        if (block == null) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        return block.getHash().getBytes();
    }

    public byte[] getMergedMiningTags(Object[] args) {
        int blockDepth = ((BigInteger) args[0]).intValue();

        Block block = getBlock(blockDepth);
        if (block == null) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        byte[] mergedMiningTx = block.getBitcoinMergedMiningCoinbaseTransaction();

        List<Byte> mergedMiningTxAsList = Arrays.asList(ArrayUtils.toObject(mergedMiningTx));
        List<Byte> rskTagBytesAsList = Arrays.asList(ArrayUtils.toObject(RskMiningConstants.RSK_TAG));

        int rskTagPosition = Collections.lastIndexOfSubList(mergedMiningTxAsList, rskTagBytesAsList);

        // if RSK Tag not found, return an empty byte array
        if (rskTagPosition == -1) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        int additionalTagsStartIndex = rskTagPosition + RskMiningConstants.RSK_TAG.length + RskMiningConstants.BLOCK_HEADER_HASH_SIZE;
        return Arrays.copyOfRange(mergedMiningTx, additionalTagsStartIndex, mergedMiningTx.length);
    }

    public byte[] getGasLimit(Object[] args) {
        int blockDepth = ((BigInteger) args[0]).intValue();

        Block block = getBlock(blockDepth);
        if (block == null) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        return block.getGasLimit();
    }

    public byte[] getGasUsed(Object[] args) {
        int blockDepth = ((BigInteger) args[0]).intValue();

        Block block = getBlock(blockDepth);
        if (block == null) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        return BigInteger.valueOf(block.getGasUsed()).toByteArray();
    }

    public byte[] getRSKDifficulty(Object[] args) {
        int blockDepth = ((BigInteger) args[0]).intValue();

        Block block = getBlock(blockDepth);
        if (block == null) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        return block.getDifficulty().getBytes();
    }

    public byte[] getBitcoinHeader(Object[] args) {
        int blockDepth = ((BigInteger) args[0]).intValue();

        Block block = getBlock(blockDepth);
        if (block == null) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }

        return block.getBitcoinMergedMiningHeader();
    }

    ParsedData parseData(byte[] data) {
        ParsedData parsedData = new ParsedData();

        byte[] functionSignature = Arrays.copyOfRange(data, 0, 4);
        Optional<BlockHeaderContractMethod> invokedMethod = BlockHeaderContractMethod.findBySignature(functionSignature);
        if (!invokedMethod.isPresent()) {
            logger.warn("Invalid function signature {}.", Hex.toHexString(functionSignature));
            return null;
        }
        parsedData.contractMethod = invokedMethod.get();
        try {
            parsedData.args = parsedData.contractMethod.getFunction().decode(data);
        } catch (Exception e) {
            logger.warn("Invalid function arguments {} for function {}.", Hex.toHexString(data), Hex.toHexString(functionSignature));
            return null;
        }

        return parsedData;
    }

    // Parsed rsk transaction data field
    private static class ParsedData {
        BlockHeaderContractMethod contractMethod;
        Object[] args;
    }

    @Override
    public void init(Transaction tx, Block executionBlock, Repository repository, BlockStore blockStore, ReceiptStore receiptStore, List<LogInfo> logs) {
        this.blockStore = blockStore;
        this.currentExecutionBlock = executionBlock;
    }

    @Override
    public long getGasForData(byte[] data) {
        ParsedData parsedData = parseData(data);

        Long functionCost = parsedData.contractMethod.getCost();
        int dataCost = data == null ? 0 : data.length * 2;

        return functionCost + dataCost;
    }

    @Override
    public byte[] execute(byte[] data) {

        // If the user tries to call a non-existent function, parseData() will return null.
        ParsedData parsedData = parseData(data);
        if (parsedData == null) {
            String errorMessage = String.format("Invalid data given: %s.", Hex.toHexString(data));
            logger.info(errorMessage);
            return null;
        }

        Optional<?> result;
        try {

            result = parsedData.contractMethod.getExecutor().execute(this, parsedData.args);
        } catch (Exception e) {
            String errorMessage = String.format("Error executing: %s", parsedData.contractMethod);
            logger.warn(errorMessage, e);
            return null;
        }

        return result.map(parsedData.contractMethod.getFunction()::encodeOutputs).orElse(null);
    }
}

