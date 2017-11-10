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

import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Transaction;
import org.ethereum.listener.EthereumListener;
import org.ethereum.manager.AdminInfo;
import org.ethereum.manager.WorldManager;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.rpc.Web3;
import org.ethereum.vm.program.ProgramResult;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author Roman Mandeleil
 * @since 27.07.2014
 */
public interface Ethereum {

    Blockchain getBlockchain();

    void addListener(EthereumListener listener);

    ImportResult addNewMinedBlock(Block block);

    void close();

    /**
     * Factory for general transaction
     *
     *
     * @param nonce - account nonce, based on number of transaction submited by
     *                this account
     * @param gasPrice - gas price bid by miner , the user ask can be based on
     *                   lastr submited block
     * @param gas - the quantity of gas requested for the transaction
     * @param receiveAddress - the target address of the transaction
     * @param value - the ether value of the transaction
     * @param data - can be init procedure for creational transaction,
     *               also msg data for invoke transaction for only value
     *               transactions this one is empty.
     * @return newly created transaction
     */
    Transaction createTransaction(BigInteger nonce,
                                 BigInteger gasPrice,
                                 BigInteger gas,
                                 byte[] receiveAddress,
                                 BigInteger value, byte[] data);


    /**
     * @param transaction submit transaction to the net, return option to wait for net
     *                    return this transaction as approved
     */
    Future<Transaction> submitTransaction(Transaction transaction);


    /**
     * Call a contract function locally without sending transaction to the network
     * and without changing contract storage.
     * @param receiveAddress hex encoded contract address
     * @param function  contract function
     * @param funcArgs  function arguments
     * @return function result. The return value can be fetched via {@link ProgramResult#getHReturn()}
     * and decoded with {@link org.ethereum.core.CallTransaction.Function#decodeResult(byte[])}.
     */
    ProgramResult callConstantFunction(String receiveAddress, CallTransaction.Function function,
                                       Object... funcArgs);


    /**
     * @return - repository for all state data.
     */
    Repository getRepository();

    /**
     * @return - pending state repository
     */
    Repository getPendingState();


    public void init();
//  2.   // is blockchain still loading - if buffer is not empty

    Repository getSnapshootTo(byte[] root);

    AdminInfo getAdminInfo();

    ChannelManager getChannelManager();

    /**
     * @return - currently pending transactions received from the net
     */
    List<Transaction> getWireTransactions();

    /**
     * @return - currently pending transactions sent to the net
     */
    List<Transaction> getPendingStateTransactions();

    /**
     * Calculates a 'reasonable' Gas price based on statistics of the latest transaction's Gas prices
     * Normally the price returned should be sufficient to execute a transaction since ~25% of the latest
     * transactions were executed at this or lower price.
     * If the transaction is wanted to be executed promptly with higher chances the returned price might
     * be increased at some ratio (e.g. * 1.2)
     */
    long getGasPrice();

    void exitOn(long number);

    // TODO review world manager expose
    WorldManager getWorldManager();

    // TODO review peer server expose
    PeerServer getPeerServer();

    // TODO added method, to review
    ProgramResult callConstant(Web3.CallArguments args);

    SystemProperties getSystemProperties();
}
