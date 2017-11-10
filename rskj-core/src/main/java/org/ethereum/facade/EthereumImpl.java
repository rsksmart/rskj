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

package org.ethereum.facade;

import co.rsk.core.ReversibleTransactionExecutor;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.*;
import org.ethereum.core.PendingState;
import org.ethereum.core.Repository;
import org.ethereum.db.ReceiptStore;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.GasPriceTracker;
import org.ethereum.manager.AdminInfo;
import org.ethereum.manager.WorldManager;
import org.ethereum.net.client.PeerClient;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.net.submit.TransactionExecutor;
import org.ethereum.net.submit.TransactionTask;
import org.ethereum.rpc.Web3;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.springframework.util.concurrent.FutureAdapter;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class EthereumImpl implements Ethereum {

    private static final Logger logger = LoggerFactory.getLogger("facade");
    private static final Logger gLogger = LoggerFactory.getLogger("general");

    private final WorldManager worldManager;
    private final AdminInfo adminInfo;
    private final ChannelManager channelManager;
    private final PeerServer peerServer;
    private final ProgramInvokeFactory programInvokeFactory;
    private final PendingState pendingState;
    private final SystemProperties config;
    private final CompositeEthereumListener compositeEthereumListener;
    private final ReceiptStore receiptStore;

    private GasPriceTracker gasPriceTracker = new GasPriceTracker();

    public EthereumImpl(WorldManager worldManager,
                        AdminInfo adminInfo,
                        ChannelManager channelManager,
                        PeerServer peerServer,
                        ProgramInvokeFactory programInvokeFactory,
                        PendingState pendingState,
                        SystemProperties config,
                        CompositeEthereumListener compositeEthereumListener,
                        ReceiptStore receiptStore) {
        this.worldManager = worldManager;
        this.adminInfo = adminInfo;
        this.channelManager = channelManager;
        this.peerServer = peerServer;
        this.programInvokeFactory = programInvokeFactory;
        this.pendingState = pendingState;
        this.config = config;
        this.compositeEthereumListener = compositeEthereumListener;
        this.receiptStore = receiptStore;
    }

    @Override
    public void init() {
        if (config.listenPort() > 0) {
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable);
                thread.setUncaughtExceptionHandler((exceptionThread, exception) -> {
                    gLogger.error("Unable to start peer server", exception);
                });
                return thread;
            }).execute(() -> peerServer.start(config.listenPort()));
        }
        compositeEthereumListener.addListener(gasPriceTracker);

        gLogger.info("RskJ node started: enode://" + Hex.toHexString(config.nodeId()) + "@" + config.externalIp() + ":" + config.listenPort());
    }

    @Override
    public org.ethereum.facade.Blockchain getBlockchain() {
        return (org.ethereum.facade.Blockchain)worldManager.getBlockchain();
    }

    @Override
    public ImportResult addNewMinedBlock(final @Nonnull Block block) {
        final ImportResult importResult = worldManager.getBlockchain().tryToConnect(block);

        if (worldManager.getBlockchain().getBlockByHash(block.getHash()) != null) {
            channelManager.broadcastBlock(block, null);
        }
        return importResult;
    }

    @Override
    public void addListener(EthereumListener listener) {
        worldManager.addListener(listener);
    }

    @Override
    public void close() {
//        worldManager.close();
    }

    @Override
    public Transaction createTransaction(BigInteger nonce,
                                         BigInteger gasPrice,
                                         BigInteger gas,
                                         byte[] receiveAddress,
                                         BigInteger value, byte[] data) {

        byte[] nonceBytes = ByteUtil.bigIntegerToBytes(nonce);
        byte[] gasPriceBytes = ByteUtil.bigIntegerToBytes(gasPrice);
        byte[] gasBytes = ByteUtil.bigIntegerToBytes(gas);
        byte[] valueBytes = ByteUtil.bigIntegerToBytes(value);
        byte chainId = config.getBlockchainConfig().getCommonConstants().getChainId();

        return new Transaction(nonceBytes, gasPriceBytes, gasBytes,
                receiveAddress, valueBytes, data, chainId);
    }

    @Override
    public Future<Transaction> submitTransaction(Transaction transaction) {

        TransactionTask transactionTask = new TransactionTask(transaction, channelManager);

        final Future<List<Transaction>> listFuture =
                TransactionExecutor.getInstance().submitTransaction(transactionTask);

        pendingState.addPendingTransaction(transaction);

        return new FutureAdapter<Transaction, List<Transaction>>(listFuture) {
            @Override
            protected Transaction adapt(List<Transaction> adapteeResult) throws ExecutionException {
                return adapteeResult.get(0);
            }
        };
    }

    @Override
    public ProgramResult callConstant(Web3.CallArguments args) {
        Block bestBlock = getBlockchain().getBestBlock();
        return ReversibleTransactionExecutor.executeTransaction(
                bestBlock.getCoinbase(),
                (Repository) getRepository(),
                worldManager.getBlockStore(),
                receiptStore,
                programInvokeFactory,
                bestBlock,
                args
        ).getResult();
    }

    @Override
    public ProgramResult callConstantFunction(String receiveAddress, CallTransaction.Function function,
                                              Object... funcArgs) {
        Transaction tx = CallTransaction.createCallTransaction(0, 0, 100000000000000L,
                receiveAddress, 0, function, funcArgs);
        tx.sign(new byte[32]);

        Block bestBlock = worldManager.getBlockchain().getBestBlock();

        Repository repository = ((Repository) worldManager.getRepository()).startTracking();

        try {
            org.ethereum.core.TransactionExecutor executor = new org.ethereum.core.TransactionExecutor
                    (tx, bestBlock.getCoinbase(), repository, worldManager.getBlockStore(), receiptStore,
                    programInvokeFactory, bestBlock)
                    .setLocalCall(true);

            executor.init();
            executor.execute();
            executor.go();
            executor.finalization();

            return executor.getResult();
        } finally {
            repository.rollback();
        }
    }

    @Override
    public org.ethereum.facade.Repository getRepository() {
        return worldManager.getRepository();
    }

    @Override
    public org.ethereum.facade.Repository getPendingState() {
        return (org.ethereum.facade.Repository) worldManager.getPendingState().getRepository();
    }

    @Override
    public org.ethereum.facade.Repository getSnapshootTo(byte[] root){

        Repository repository = (Repository) worldManager.getRepository();
        org.ethereum.facade.Repository snapshot = (org.ethereum.facade.Repository) repository.getSnapshotTo(root);

        return snapshot;
    }

    @Override
    public AdminInfo getAdminInfo() {
        return adminInfo;
    }

    @Override
    public ChannelManager getChannelManager() {
        return channelManager;
    }


    @Override
    public List<Transaction> getWireTransactions() {
        return worldManager.getPendingState().getWireTransactions();
    }

    @Override
    public List<Transaction> getPendingStateTransactions() {
        return worldManager.getPendingState().getPendingTransactions();
    }

    @Override
    public long getGasPrice() {
        return gasPriceTracker.getGasPrice();
    }

    @Override
    public void exitOn(long number) {
        worldManager.getBlockchain().setExitOn(number);
    }
    // TODO Review world manager expose

    @Override
    public WorldManager getWorldManager() { return worldManager; }
    // TODO Review peer server expose

    @Override
    public PeerServer getPeerServer() { return peerServer; }

    @Override
    public SystemProperties getSystemProperties() {
        return this.config;
    }
}
