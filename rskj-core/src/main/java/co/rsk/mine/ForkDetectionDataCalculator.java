/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.mine;

import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.core.types.ints.Uint8;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ForkDetectionDataCalculator {

    private static final int CPV_SIZE = 7;

    private static final int CPV_JUMP_FACTOR = 64;

    private static final int NUMBER_OF_UNCLES = 32;

    // + 1 because genesis block can't be used since it does not contain a valid BTC header
    private static final int MIN_MAINCHAIN_SIZE = CPV_SIZE * CPV_JUMP_FACTOR + 1;

    private NetworkParameters params;

    public ForkDetectionDataCalculator(){
        this.params = RegTestParams.get();
        new Context(params);
    }

    public byte[] calculate(List<Block> mainchainBlocks) {
        if (mainchainBlocks.size() < MIN_MAINCHAIN_SIZE) {
            return new byte[0];
        }

        List<BlockHeader> mainchainBlockHeaders = mainchainBlocks
                .stream()
                .map(Block::getHeader)
                .collect(Collectors.toList());

        return calculateWithBlockHeaders(mainchainBlockHeaders);
    }

    public byte[] calculateWithBlockHeaders(List<BlockHeader> mainchainBlockHeaders) {
        if (mainchainBlockHeaders.size() < MIN_MAINCHAIN_SIZE) {
            return new byte[0];
        }

        byte[] forkDetectionData = new byte[12];

        byte[] commitToParentsVector = buildCommitToParentsVector(mainchainBlockHeaders);
        System.arraycopy(commitToParentsVector, 0, forkDetectionData, 0, 7);

        Uint8 numberOfUncles = getNumberOfUncles(mainchainBlockHeaders);
        forkDetectionData[7] = numberOfUncles.asByte();

        byte[] blockBeingMinedHeight = getBlockBeingMinedHeight(mainchainBlockHeaders);
        System.arraycopy(blockBeingMinedHeight, 0, forkDetectionData, 8, 4);

        return forkDetectionData;
    }

    private byte[] buildCommitToParentsVector(List<BlockHeader> mainchainBlocks) {
        long bestBlockHeight = mainchainBlocks.get(0).getNumber();
        long cpvStartHeight = (bestBlockHeight / CPV_JUMP_FACTOR) * CPV_JUMP_FACTOR;

        byte[] commitToParentsVector = new byte[CPV_SIZE];
        for(int i = 0; i < CPV_SIZE; i++){
            long currentCpvElement = bestBlockHeight - cpvStartHeight + i * CPV_JUMP_FACTOR;
            BlockHeader blockHeader = mainchainBlocks.get((int)currentCpvElement);
            byte[] bitcoinBlock = blockHeader.getBitcoinMergedMiningHeader();

            byte[] bitcoinBlockHash = params.getDefaultSerializer().makeBlock(bitcoinBlock).getHash().getBytes();
            byte leastSignificantByte = bitcoinBlockHash[bitcoinBlockHash.length - 1];

            commitToParentsVector[i] = leastSignificantByte;
        }

        return commitToParentsVector;
    }

    private Uint8 getNumberOfUncles(List<BlockHeader> mainchainBlocks) {
        int sum = IntStream
                .range(0, NUMBER_OF_UNCLES)
                .map(i -> mainchainBlocks.get(i).getUncleCount()).sum();

        final int maxUint = Uint8.MAX_VALUE.intValue();
        if (sum > maxUint) {
            return new Uint8(maxUint);
        }

        return new Uint8(sum);
    }

    private byte[] getBlockBeingMinedHeight(List<BlockHeader> mainchainBlocks) {
        long blockBeingMinedHeight = mainchainBlocks.get(0).getNumber() + 1;
        return ByteBuffer.allocate(4).putInt((int)blockBeingMinedHeight).array();
    }
}