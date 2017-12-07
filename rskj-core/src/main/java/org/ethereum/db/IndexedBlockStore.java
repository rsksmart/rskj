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

package org.ethereum.db;

import co.rsk.net.BlockCache;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.datasource.KeyValueDataSource;
import org.mapdb.DB;
import org.mapdb.DataIO;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

import java.io.*;
import java.math.BigInteger;
import java.util.*;

import static java.math.BigInteger.ZERO;
import static org.ethereum.crypto.HashUtil.shortHash;
import static org.spongycastle.util.Arrays.areEqual;

public class IndexedBlockStore extends AbstractBlockstore {

    private static final Logger logger = LoggerFactory.getLogger("general");

    private BlockCache blockCache = new BlockCache(200);
    Map<Long, List<BlockInfo>> index;
    KeyValueDataSource blocks;

    DB indexDB;

    public IndexedBlockStore(){
    }

    public void init(Map<Long, List<BlockInfo>> index, KeyValueDataSource blocks, DB indexDB) {
        this.index = index;
        this.blocks = blocks;
        this.indexDB  = indexDB;
    }

    @Override
    public synchronized void removeBlock(Block block) {
        this.blockCache.removeBlock(block);

        this.blocks.delete(block.getHash());

        List<BlockInfo> binfos = this.index.get(block.getNumber());

        if (binfos == null)
            return;

        List<BlockInfo> toremove = new ArrayList<>();

        for (BlockInfo binfo : binfos)
            if (Arrays.equals(binfo.getHash(), block.getHash()))
                toremove.add(binfo);

        binfos.removeAll(toremove);
    }

    @Override
    public synchronized Block getBestBlock() {
        Long maxLevel = getMaxNumber();
        if (maxLevel < 0) {
            return null;
        }

        Block bestBlock = getChainBlockByNumber(maxLevel);
        if (bestBlock != null) {
            return  bestBlock;
        }

        // That scenario can happen
        // if there is a fork branch that is
        // higher than main branch but has
        // less TD than the main branch TD
        while (bestBlock == null && maxLevel >= 0) {
            --maxLevel;
            bestBlock = getChainBlockByNumber(maxLevel);
        }

        return bestBlock;
    }

    @Override
    public synchronized byte[] getBlockHashByNumber(long blockNumber){
        List<BlockInfo> infos = this.index.get(blockNumber);
        if (infos != null) {
            Optional<BlockInfo> info =  infos.stream().filter(BlockInfo::isMainChain).findAny();
            if (info.isPresent())
                return info.get().getHash();

        }
        return getChainBlockByNumber(blockNumber).getHash();
    }

    @Override
    public synchronized void flush() {
        long t1 = System.nanoTime();

        if (indexDB != null)
            indexDB.commit();

        long t2 = System.nanoTime();

        logger.info("Flush block store in: {} ms", ((float)(t2 - t1) / 1_000_000));
    }

    @Override
    public synchronized void saveBlock(Block block, BigInteger cummDifficulty, boolean mainChain) {
        List<BlockInfo> blockInfos = index.get(block.getNumber());
        if (blockInfos == null) {
            blockInfos = new ArrayList<>();
        }

        BlockInfo blockInfo = null;
        for (BlockInfo bi : blockInfos) {
            if (areEqual(bi.getHash(), block.getHash())) {
                blockInfo = bi;
            } else if (mainChain) {
                bi.setMainChain(false);
            }
        }
        if (blockInfo == null) {
            blockInfo = new BlockInfo();
            blockInfos.add(blockInfo);
        }

        blockInfo.setCummDifficulty(cummDifficulty);
        blockInfo.setHash(block.getHash());
        blockInfo.setMainChain(mainChain);

        if (blocks.get(block.getHash()) == null)
            blocks.put(block.getHash(), block.getEncoded());
        index.put(block.getNumber(), blockInfos);
        blockCache.addBlock(block);
    }

    @Override
    public synchronized List<BlockInformation> getBlocksInformationByNumber(long number) {
        List<BlockInformation> result = new ArrayList<>();

        List<BlockInfo> blockInfos = index.get(number);

        if (blockInfos == null)
            return result;

        for (BlockInfo blockInfo : blockInfos) {
            byte[] hash = ByteUtils.clone(blockInfo.getHash());
            BigInteger totalDifficulty = blockInfo.getCummDifficulty();
            boolean isInBlockChain = blockInfo.isMainChain();

            result.add(new BlockInformation(hash, totalDifficulty, isInBlockChain));
        }

        return result;
    }

    @Override
    public synchronized Block getChainBlockByNumber(long number){
        List<BlockInfo> blockInfos = index.get(number);
        if (blockInfos == null) {
            return null;
        }

        for (BlockInfo blockInfo : blockInfos) {

            if (blockInfo.isMainChain()) {

                byte[] hash = blockInfo.getHash();
                return getBlockByHash(hash);
            }
        }

        return null;
    }

    @Override
    public synchronized Block getBlockByHash(byte[] hash) {
        Block block = this.blockCache.getBlockByHash(hash);

        if (block != null)
            return block;

        byte[] blockRlp = blocks.get(hash);
        if (blockRlp == null)
            return null;

        block = new Block(blockRlp);
        this.blockCache.put(new ByteArrayWrapper(hash), block);
        return block;
    }

    @Override
    public synchronized boolean isBlockExist(byte[] hash) {
        return getBlockByHash(hash) != null;
    }

    @Override
    public synchronized BigInteger getTotalDifficultyForHash(byte[] hash){
        Block block = this.getBlockByHash(hash);
        if (block == null) {
            return ZERO;
        }

        Long level  =  block.getNumber();
        List<BlockInfo> blockInfos =  index.get(level);

        if (blockInfos == null)
            return ZERO;

        for (BlockInfo blockInfo : blockInfos)
             if (areEqual(blockInfo.getHash(), hash)) {
                 return blockInfo.cummDifficulty;
             }

        return ZERO;
    }

    @Override
    public synchronized long getMaxNumber() {
        return (long)index.size() - 1L;
    }

    @Override
    public synchronized List<byte[]> getListHashesEndWith(byte[] hash, long number){

        List<Block> blocks = getListBlocksEndWith(hash, number);
        List<byte[]> hashes = new ArrayList<>(blocks.size());

        for (Block b : blocks) {
            hashes.add(b.getHash());
        }

        return hashes;
    }

    @Override
    public synchronized List<BlockHeader> getListHeadersEndWith(byte[] hash, long qty) {

        List<Block> blocks = getListBlocksEndWith(hash, qty);
        List<BlockHeader> headers = new ArrayList<>(blocks.size());

        for (Block b : blocks) {
            headers.add(b.getHeader());
        }

        return headers;
    }

    @Override
    public synchronized List<Block> getListBlocksEndWith(byte[] hash, long qty) {
        Block block = getBlockByHash(hash);

        if (block == null) {
            return new ArrayList<>();
        }

        List<Block> blocks = new ArrayList<>((int) qty);

        for (int i = 0; i < qty; ++i) {

            blocks.add(block);
            block = getBlockByHash(hash);
            if (block == null) {
                break;
            }
        }

        return blocks;
    }

    @Override
    public synchronized void reBranch(Block forkBlock){

        Block bestBlock = getBestBlock();
        long maxLevel = Math.max(bestBlock.getNumber(), forkBlock.getNumber());

        // 1. First ensure that you are on the save level
        long currentLevel = maxLevel;
        Block forkLine = forkBlock;

        if (forkBlock.getNumber() > bestBlock.getNumber()) {

            while(currentLevel > bestBlock.getNumber()) {
                List<BlockInfo> blocks = index.get(currentLevel);
                BlockInfo blockInfo = getBlockInfoForHash(blocks, forkLine.getHash());
                if (blockInfo != null) {
                    blockInfo.setMainChain(true);
                    if (index.containsKey(currentLevel))
                        index.put(currentLevel, blocks);
                }
                forkLine = getBlockByHash(forkLine.getParentHash());
                --currentLevel;
            }
        }

        Block bestLine = bestBlock;
        if (bestBlock.getNumber() > forkBlock.getNumber()){

            while(currentLevel > forkBlock.getNumber()) {
                List<BlockInfo> blocks =  index.get(currentLevel);
                BlockInfo blockInfo = getBlockInfoForHash(blocks, bestLine.getHash());
                if (blockInfo != null) {
                    blockInfo.setMainChain(false);
                    if (index.containsKey(currentLevel))
                        index.put(currentLevel, blocks);
                }
                bestLine = getBlockByHash(bestLine.getParentHash());
                --currentLevel;
            }
        }

        // 2. Loop back on each level until common block
        while( !bestLine.isEqual(forkLine) ) {

            List<BlockInfo> levelBlocks = index.get(currentLevel);
            BlockInfo bestInfo = getBlockInfoForHash(levelBlocks, bestLine.getHash());
            if (bestInfo != null) {
                bestInfo.setMainChain(false);
                if (index.containsKey(currentLevel))
                    index.put(currentLevel, levelBlocks);
            }

            BlockInfo forkInfo = getBlockInfoForHash(levelBlocks, forkLine.getHash());
            if (forkInfo != null) {
                forkInfo.setMainChain(true);
                if (index.containsKey(currentLevel))
                    index.put(currentLevel, levelBlocks);
            }

            bestLine = getBlockByHash(bestLine.getParentHash());
            forkLine = getBlockByHash(forkLine.getParentHash());

            --currentLevel;
        }
    }

    @VisibleForTesting
    public synchronized List<byte[]> getListHashesStartWith(long number, long maxBlocks) {

        List<byte[]> result = new ArrayList<>();

        int i;
        for (i = 0; i < maxBlocks; ++i) {
            List<BlockInfo> blockInfos =  index.get(number);
            if (blockInfos == null) {
                break;
            }

            for (BlockInfo blockInfo : blockInfos)
               if (blockInfo.isMainChain()) {
                   result.add(blockInfo.getHash());
                   break;
               }

            ++number;
        }

        return result;
    }

    public static class BlockInfo implements Serializable {
        byte[] hash;
        BigInteger cummDifficulty;
        boolean mainChain;

        public byte[] getHash() {
            return hash;
        }

        public void setHash(byte[] hash) {
            this.hash = hash;
        }

        public BigInteger getCummDifficulty() {
            return cummDifficulty;
        }

        public void setCummDifficulty(BigInteger cummDifficulty) {
            this.cummDifficulty = cummDifficulty;
        }

        public boolean isMainChain() {
            return mainChain;
        }

        public void setMainChain(boolean mainChain) {
            this.mainChain = mainChain;
        }
    }


    public static final Serializer<List<BlockInfo>> BLOCK_INFO_SERIALIZER = new Serializer<List<BlockInfo>>(){

        @Override
        public void serialize(DataOutput out, List<BlockInfo> value) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(value);

            byte[] data = bos.toByteArray();
            DataIO.packInt(out, data.length);
            out.write(data);
        }

        @Override
        public List<BlockInfo> deserialize(DataInput in, int available) throws IOException {

            List<BlockInfo> value = null;
            try {
                int size = DataIO.unpackInt(in);
                byte[] data = new byte[size];
                in.readFully(data);

                ByteArrayInputStream bis = new ByteArrayInputStream(data, 0, data.length);
                ObjectInputStream ois = new ObjectInputStream(bis);
                value = (List<BlockInfo>)ois.readObject();
            } catch (ClassNotFoundException e) {
                logger.error("Class not found", e);
            }

            return value;
        }
    };

    public synchronized void printChain() {
        Long number = getMaxNumber();

        for (long i = 0; i < number; ++i){
            List<BlockInfo> levelInfos = index.get(i);

            if (levelInfos != null) {
                System.out.print(i);
                for (BlockInfo blockInfo : levelInfos){
                    if (blockInfo.isMainChain())
                        System.out.print(" [" + shortHash(blockInfo.getHash()) + "] ");
                    else
                        System.out.print(" " + shortHash(blockInfo.getHash()) + " ");
                }
                System.out.println();
            }

        }
    }

    private static BlockInfo getBlockInfoForHash(List<BlockInfo> blocks, byte[] hash){
        if (blocks == null)
            return null;

        for (BlockInfo blockInfo : blocks)
            if (areEqual(hash, blockInfo.getHash())) {
                return blockInfo;
            }

        return null;
    }

    @Override
    public void load() {
    }

    @Override
    public synchronized List<Block> getChainBlocksByNumber(long number){
        List<Block> result = new ArrayList<>();

        List<BlockInfo> blockInfos = index.get(number);

        if (blockInfos == null){
            return result;
        }

        for (BlockInfo blockInfo : blockInfos){

            byte[] hash = blockInfo.getHash();
            Block block = getBlockByHash(hash);

            result.add(block);
        }

        return result;
    }

}
