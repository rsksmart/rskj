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

package co.rsk.rpc;

import co.rsk.config.RskMiningConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.mine.MinerWork;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.personal.PersonalModule;
import org.apache.commons.lang3.ArrayUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.SHA3Helper;
import org.ethereum.db.BlockStore;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3Impl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by adrian.eidelman on 3/11/2016.
 */
public class Web3RskImpl extends Web3Impl {
    private static final Logger logger = LoggerFactory.getLogger("web3");

    public Web3RskImpl(Ethereum eth,
                       RskSystemProperties properties,
                       MinerClient minerClient,
                       MinerServer minerServer,
                       PersonalModule personalModule,
                       EthModule ethModule,
                       ChannelManager channelManager) {
        super(eth, properties, minerClient, minerServer, personalModule, ethModule, channelManager);
    }

    public MinerWork mnr_getWork() {
        if (logger.isDebugEnabled()) {
            logger.debug("mnr_getWork()");
        }
        return minerServer.getWork();
    }

    public void mnr_submitBitcoinBlock(String bitcoinBlockHex) {
        if (logger.isDebugEnabled()) {
            logger.debug("mnr_submitBitcoinBlock(): " + bitcoinBlockHex.length());
        }
        
        co.rsk.bitcoinj.core.NetworkParameters params = co.rsk.bitcoinj.params.RegTestParams.get();
        new co.rsk.bitcoinj.core.Context(params);
        byte[] bitcoinBlockByteArray = Hex.decode(bitcoinBlockHex);
        co.rsk.bitcoinj.core.BtcBlock bitcoinBlock = params.getDefaultSerializer().makeBlock(bitcoinBlockByteArray);
        co.rsk.bitcoinj.core.BtcTransaction coinbase = bitcoinBlock.getTransactions().get(0);
        byte[] coinbaseAsByteArray = coinbase.bitcoinSerialize();
        List<Byte> coinbaseAsByteList = java.util.Arrays.asList(ArrayUtils.toObject(coinbaseAsByteArray));

        List<Byte> rskTagAsByteList = java.util.Arrays.asList(ArrayUtils.toObject(RskMiningConstants.RSK_TAG));

        int rskTagPosition = Collections.lastIndexOfSubList(coinbaseAsByteList, rskTagAsByteList);
        byte[] blockHashForMergedMiningArray = new byte[SHA3Helper.Size.S256.getValue()/8];
        System.arraycopy(coinbaseAsByteArray, rskTagPosition+ RskMiningConstants.RSK_TAG.length, blockHashForMergedMiningArray, 0, blockHashForMergedMiningArray.length);
        String blockHashForMergedMining = TypeConverter.toJsonHex(blockHashForMergedMiningArray);

        minerServer.submitBitcoinBlock(blockHashForMergedMining, bitcoinBlock);
    }

    public void ext_dumpState()  {
        Block bestBlcock = worldManager.getBlockStore().getBestBlock();
        logger.info("Dumping state for block hash {}, block number {}", Hex.toHexString(bestBlcock.getHash()), bestBlcock.getNumber());
        this.worldManager.getNetworkStateExporter().exportStatus(System.getProperty("user.dir") + "/" + "rskdump.json");
    }

    /**
     * Export the blockchain tree as a tgf file to user.dir/rskblockchain.tgf
     * @param numberOfBlocks Number of block heights to include. Eg if best block is block 2300 and numberOfBlocks is 10, the graph will include blocks in heights 2290 to 2300.
     * @param includeUncles Whether to show uncle links (recommended value is false)
     */
    public void ext_dumpBlockchain(long numberOfBlocks, boolean includeUncles)  {
        BlockStore bs = worldManager.getBlockStore();
        Block bestBlock = bs.getBestBlock();
        logger.info("Dumping blockchain starting on block number {}, to best block number {}", bestBlock.getNumber()-numberOfBlocks, bestBlock.getNumber());
        PrintWriter writer = null;
        try {
            File graphFile = new File(System.getProperty("user.dir") + "/" + "rskblockchain.tgf");
            writer = new PrintWriter(new FileWriter(graphFile));

            List<Block> result = new LinkedList<>();
            long firstBlock = bestBlock.getNumber() - numberOfBlocks;
            if (firstBlock < 0) {
                firstBlock = 0;
            }
            for (long i = firstBlock; i < bestBlock.getNumber(); i++) {
                result.addAll(bs.getChainBlocksByNumber(i));
            }
            for (Block block : result) {
                writer.println(toSmallHash(block.getHash()) + " " + block.getNumber()+"-"+toSmallHash(block.getHash()));
            }
            writer.println("#");
            for (Block block : result) {
                writer.println(toSmallHash(block.getHash()) + " " + toSmallHash(block.getParentHash()) + " P");
                if (includeUncles) {
                    for (BlockHeader uncleHeader : block.getUncleList()) {
                        writer.println(toSmallHash(block.getHash()) + " " + toSmallHash(uncleHeader.getHash()) + " U");
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Could nos save node graph to file", e);
        } finally {
            if (writer!=null) {
                try {
                    writer.close();
                } catch (Exception e) {}
            }
        }
    }

    private String toSmallHash(byte[] input) {
        return Hex.toHexString(input).substring(56,64);
    }

}
