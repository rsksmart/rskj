/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.rpc.modules.mnr;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.Keccak256Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.config.RskMiningConstants;
import co.rsk.mine.MinerServer;
import co.rsk.mine.MinerWork;
import co.rsk.mine.SubmitBlockResult;
import co.rsk.mine.SubmittedBlockInfo;
import co.rsk.rpc.exception.JsonRpcSubmitBlockException;
import co.rsk.util.HexUtils;
import co.rsk.util.ListArrayUtil;

public class MnrModuleImpl implements MnrModule {
    private static final Logger logger = LoggerFactory.getLogger("web3");

    private final MinerServer minerServer;

    public MnrModuleImpl(MinerServer minerServer) {
        this.minerServer = minerServer;
    }

    @Override
    public MinerWork getWork() {
        logger.debug("getWork()");
        return minerServer.getWork();
    }

    @Override
    public SubmittedBlockInfo submitBitcoinBlock(String bitcoinBlockHex) {
        logger.debug("submitBitcoinBlock(): {}", bitcoinBlockHex.length());

        NetworkParameters params = RegTestParams.get();
        new Context(params);

        BtcBlock bitcoinBlock = getBtcBlock(bitcoinBlockHex, params);
        BtcTransaction coinbase = bitcoinBlock.getTransactions().get(0);

        String blockHashForMergedMining = extractBlockHashForMergedMining(coinbase);

        SubmitBlockResult result = minerServer.submitBitcoinBlock(blockHashForMergedMining, bitcoinBlock);

        return parseResultAndReturn(result);
    }

    @Override
    public SubmittedBlockInfo submitBitcoinBlockTransactions(String blockHashHex, String blockHeaderHex, String coinbaseHex, String txnHashesHex) {
        logger.debug("submitBitcoinBlockTransactions(): {}, {}, {}, {}", blockHashHex, blockHeaderHex, coinbaseHex, txnHashesHex);

        NetworkParameters params = RegTestParams.get();
        new Context(params);

        BtcBlock bitcoinBlockWithHeaderOnly = getBtcBlock(blockHeaderHex, params);
        BtcTransaction coinbase = new BtcTransaction(params, Hex.decode(coinbaseHex));

        String blockHashForMergedMining = extractBlockHashForMergedMining(coinbase);

        List<String> txnHashes = parseHashes(txnHashesHex);

        SubmitBlockResult result = minerServer.submitBitcoinBlockTransactions(blockHashForMergedMining, bitcoinBlockWithHeaderOnly, coinbase, txnHashes);

        return parseResultAndReturn(result);
    }

    @Override
    public SubmittedBlockInfo submitBitcoinBlockPartialMerkle(String blockHashHex, String blockHeaderHex, String coinbaseHex, String merkleHashesHex, String blockTxnCountHex) {
        logger.debug("submitBitcoinBlockPartialMerkle(): {}, {}, {}, {}, {}", blockHashHex, blockHeaderHex, coinbaseHex, merkleHashesHex, blockTxnCountHex);

        if (merkleHashesHex.isEmpty()) {
            throw new JsonRpcSubmitBlockException("The list of merkle hashes can't be empty");
        }

        NetworkParameters params = RegTestParams.get();
        new Context(params);

        BtcBlock bitcoinBlockWithHeaderOnly = getBtcBlock(blockHeaderHex, params);
        BtcTransaction coinbase = new BtcTransaction(params, Hex.decode(coinbaseHex));

        String blockHashForMergedMining = extractBlockHashForMergedMining(coinbase);

        List<String> merkleHashes = parseHashes(merkleHashesHex);

        int txnCount = Integer.parseInt(blockTxnCountHex, 16);

        SubmitBlockResult result = minerServer.submitBitcoinBlockPartialMerkle(blockHashForMergedMining, bitcoinBlockWithHeaderOnly, coinbase, merkleHashes, txnCount);

        return parseResultAndReturn(result);
    }

    private BtcBlock getBtcBlock(String blockHeaderHex, NetworkParameters params) {
        byte[] bitcoinBlockByteArray = Hex.decode(blockHeaderHex);
        return params.getDefaultSerializer().makeBlock(bitcoinBlockByteArray);
    }

    private String extractBlockHashForMergedMining(BtcTransaction coinbase) {
        byte[] coinbaseAsByteArray = coinbase.bitcoinSerialize();
        List<Byte> coinbaseAsByteList = ListArrayUtil.asByteList(coinbaseAsByteArray);

        List<Byte> rskTagAsByteList = ListArrayUtil.asByteList(RskMiningConstants.RSK_TAG);

        int rskTagPosition = Collections.lastIndexOfSubList(coinbaseAsByteList, rskTagAsByteList);
        byte[] blockHashForMergedMiningArray = new byte[Keccak256Helper.Size.S256.getValue() / 8];
        System.arraycopy(coinbaseAsByteArray, rskTagPosition + RskMiningConstants.RSK_TAG.length, blockHashForMergedMiningArray, 0, blockHashForMergedMiningArray.length);
        return HexUtils.toJsonHex(blockHashForMergedMiningArray);
    }

    private List<String> parseHashes(String txnHashesHex) {
        String[] split = txnHashesHex.split("\\s+");
        return Arrays.asList(split);
    }

    private SubmittedBlockInfo parseResultAndReturn(SubmitBlockResult result) {
        if ("OK".equals(result.getStatus())) {
            return result.getBlockInfo();
        } else {
            throw new JsonRpcSubmitBlockException(result.getMessage());
        }
    }
}
