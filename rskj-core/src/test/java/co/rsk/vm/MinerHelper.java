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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.mine.GasLimitCalculator;
import co.rsk.panic.PanicProcessor;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.vm.PrecompiledContracts;
import org.powermock.reflect.Whitebox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Created by Sergio on 18/07/2016. */
public class MinerHelper {
    private static final Logger logger = LoggerFactory.getLogger("minerhelper");
    private static final PanicProcessor panicProcessor = new PanicProcessor();
    private final TestSystemProperties config = new TestSystemProperties();

    private final Blockchain blockchain;
    private final Repository repository;
    private final GasLimitCalculator gasLimitCalculator;
    private final BlockFactory blockFactory;

    private byte[] latestStateRootHash;
    private long totalGasUsed;
    private Coin totalPaidFees = Coin.ZERO;
    private List<TransactionReceipt> txReceipts;

    public MinerHelper(Repository repository, Blockchain blockchain) {
        this.repository = repository;
        this.blockchain = blockchain;
        this.gasLimitCalculator = new GasLimitCalculator(config.getNetworkConstants());
        this.blockFactory = new BlockFactory(config.getActivationConfig());
    }

    public void processBlock(Block block, Block parent) {
        latestStateRootHash = null;
        totalGasUsed = 0;
        totalPaidFees = Coin.ZERO;
        txReceipts = new ArrayList<>();

        // Repository originalRepo  = ((Repository)
        // ethereum.getRepository()).getSnapshotTo(parent.getStateRoot());

        // This creates a snapshot WITHOUT history of the current "parent" reponsitory.
        Repository originalRepo = repository.getSnapshotTo(parent.getStateRoot());

        Repository track = originalRepo.startTracking();

        // Repository track = new RepositoryTrack((Repository)ethereum.getRepository());

        // this variable is set before iterating transactions in case list is empty
        latestStateRootHash = originalRepo.getRoot();

        // RSK test, remove
        String stateHash1 = Hex.toHexString(blockchain.getBestBlock().getStateRoot());
        String stateHash2 = Hex.toHexString(repository.getRoot());
        if (stateHash1.compareTo(stateHash2) != 0) {
            logger.error("Strange state in block {} {}", block.getNumber(), block.getHash());
            panicProcessor.panic(
                    "minerserver",
                    String.format(
                            "Strange state in block %d %s", block.getNumber(), block.getHash()));
        }

        int txindex = 0;

        for (Transaction tx : block.getTransactionsList()) {
            TransactionExecutorFactory transactionExecutorFactory =
                    new TransactionExecutorFactory(
                            config,
                            null,
                            null,
                            blockFactory,
                            null,
                            new PrecompiledContracts(
                                    config,
                                    new RepositoryBtcBlockStoreWithCache.Factory(
                                            config.getNetworkConstants()
                                                    .getBridgeConstants()
                                                    .getBtcParams())));
            TransactionExecutor executor =
                    transactionExecutorFactory.newInstance(
                            tx, txindex++, block.getCoinbase(), track, block, totalGasUsed);

            executor.init();
            executor.execute();
            executor.go();
            executor.finalization();

            long gasUsed = executor.getGasUsed();
            Coin paidFees = executor.getPaidFees();
            totalGasUsed += gasUsed;
            totalPaidFees = totalPaidFees.add(paidFees);

            track.commit();

            TransactionReceipt receipt = new TransactionReceipt();
            receipt.setGasUsed(gasUsed);
            receipt.setCumulativeGas(totalGasUsed);
            latestStateRootHash = originalRepo.getRoot();
            receipt.setPostTxState(latestStateRootHash);
            receipt.setTxStatus(executor.getReceipt().isSuccessful());
            receipt.setStatus(executor.getReceipt().getStatus());
            receipt.setTransaction(tx);
            receipt.setLogInfoList(executor.getVMLogs());

            txReceipts.add(receipt);
        }
    }

    public void completeBlock(Block newBlock, Block parent) {
        processBlock(newBlock, parent);

        boolean isRskip126Enabled =
                config.getActivationConfig().isActive(ConsensusRule.RSKIP126, newBlock.getNumber());
        newBlock.getHeader()
                .setReceiptsRoot(
                        BlockHashesHelper.calculateReceiptsTrieRoot(txReceipts, isRskip126Enabled));
        newBlock.getHeader().setStateRoot(latestStateRootHash);
        newBlock.getHeader().setGasUsed(totalGasUsed);

        Bloom logBloom = new Bloom();
        txReceipts.stream().map(TransactionReceipt::getBloomFilter).forEach(logBloom::or);

        newBlock.getHeader().setLogsBloom(logBloom.getData());

        BigInteger minGasLimit = BigInteger.valueOf(config.getNetworkConstants().getMinGasLimit());
        BigInteger targetGasLimit = BigInteger.valueOf(config.getTargetGasLimit());
        BigInteger parentGasLimit = new BigInteger(1, parent.getGasLimit());
        BigInteger gasLimit =
                gasLimitCalculator.calculateBlockGasLimit(
                        parentGasLimit,
                        BigInteger.valueOf(totalGasUsed),
                        minGasLimit,
                        targetGasLimit,
                        false);

        Whitebox.setInternalState(newBlock.getHeader(), "gasLimit", gasLimit.toByteArray());
        newBlock.getHeader().setPaidFees(totalPaidFees);
    }
}
