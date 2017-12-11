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

package co.rsk.vm;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.mine.GasLimitCalculator;
import co.rsk.panic.PanicProcessor;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Sergio on 18/07/2016.
 */
public class MinerHelper {
    private static final Logger logger = LoggerFactory.getLogger("minerhelper");
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    @Autowired
    private BlockStore blockStore;

    @Autowired
    protected Blockchain blockchain;

    @Autowired
    protected Repository repository;

    @Autowired
    ProgramInvokeFactory programInvokeFactory;



    byte[] latestStateRootHash = null;
    long totalGasUsed = 0;
    long totalPaidFees = 0;
    List<TransactionReceipt> txReceipts;
    private GasLimitCalculator gasLimitCalculator;

    public MinerHelper(Repository repository , Blockchain blockchain) // , BlockStore blockStore)
    {
        this.repository = repository;
        this.blockchain = blockchain;
       // this.blockStore =blockStore;
        gasLimitCalculator = new GasLimitCalculator(RskSystemProperties.CONFIG);
    }

    public void processBlock( Block block, Block parent) {
        latestStateRootHash = null;
        totalGasUsed = 0;
        totalPaidFees = 0;
        txReceipts = new ArrayList<>();

        //Repository originalRepo  = ((Repository) ethereum.getRepository()).getSnapshotTo(parent.getStateRoot());

        // This creates a snapshot WITHOUT history of the current "parent" reponsitory.
        Repository originalRepo  = repository.getSnapshotTo(parent.getStateRoot());

        Repository track = originalRepo.startTracking();

        // Repository track = new RepositoryTrack((Repository)ethereum.getRepository());

        // this variable is set before iterating transactions in case list is empty
        latestStateRootHash = originalRepo.getRoot();

        // RSK test, remove
        String stateHash1 = Hex.toHexString(blockchain.getBestBlock().getStateRoot());
        String stateHash2 = Hex.toHexString(repository.getRoot());
        if (stateHash1.compareTo(stateHash2) != 0) {
            logger.error("Strange state in block {} {}", block.getNumber(), Hex.toHexString(block.getHash()));
            panicProcessor.panic("minerserver", String.format("Strange state in block %d %s", block.getNumber(), Hex.toHexString(block.getHash())));
        }

        int txindex = 0;

        for (Transaction tx : block.getTransactionsList()) {

            TransactionExecutor executor = new TransactionExecutor(tx, txindex++, block.getCoinbase(),
                    track, blockStore, blockchain.getReceiptStore(),
                    programInvokeFactory, block, new EthereumListenerAdapter(), totalGasUsed);

            executor.init();
            executor.execute();
            executor.go();
            executor.finalization();

            long gasUsed = executor.getGasUsed();
            long paidFees = executor.getPaidFees();
            totalGasUsed += gasUsed;
            totalPaidFees += paidFees;

            track.commit();

            TransactionReceipt receipt = new TransactionReceipt();
            receipt.setGasUsed(gasUsed);
            receipt.setCumulativeGas(totalGasUsed);
            latestStateRootHash = originalRepo.getRoot();
            receipt.setPostTxState(latestStateRootHash);
            receipt.setTransaction(tx);
            receipt.setLogInfoList(executor.getVMLogs());

            txReceipts.add(receipt);

        }
    }

    public void completeBlock(Block newBlock, Block parent) {
        processBlock(newBlock, parent);

        newBlock.getHeader().setReceiptsRoot(BlockChainImpl.calcReceiptsTrie(txReceipts));
        newBlock.getHeader().setStateRoot(latestStateRootHash);
        newBlock.getHeader().setGasUsed(totalGasUsed);

        Bloom logBloom = new Bloom();
        for (TransactionReceipt receipt : txReceipts) {
            logBloom.or(receipt.getBloomFilter());
        }

        newBlock.getHeader().setLogsBloom(logBloom.getData());

        BigInteger minGasLimit = BigInteger.valueOf(RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getMinGasLimit());
        BigInteger targetGasLimit = BigInteger.valueOf(RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getTargetGasLimit());
        BigInteger parentGasLimit = new BigInteger(1, parent.getGasLimit());
        BigInteger gasLimit = gasLimitCalculator.calculateBlockGasLimit(parentGasLimit, BigInteger.valueOf(totalGasUsed), minGasLimit, targetGasLimit, false);

        newBlock.getHeader().setGasLimit(gasLimit.toByteArray());
        newBlock.getHeader().setPaidFees(totalPaidFees);
    }
}
