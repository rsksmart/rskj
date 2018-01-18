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

package org.ethereum.rpc.Simples;

import org.ethereum.core.Block;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.facade.Blockchain;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListener;
import org.ethereum.listener.GasPriceTracker;
import org.ethereum.manager.WorldManager;
import org.ethereum.rpc.Web3;
import org.ethereum.vm.program.ProgramResult;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Created by Ruben Altman on 09/06/2016.
 */
public class SimpleEthereum implements Ethereum {

    public Transaction tx;
    public WorldManager worldManager;
    public Repository repository;

    public SimpleEthereum() {
        this(null);
    }

    public SimpleEthereum(SimpleWorldManager worldManager) {
        this.worldManager = worldManager;
    }

    @Override
    public Blockchain getBlockchain() {
        return null;
    }

    @Override
    public void addListener(EthereumListener listener) {
        this.worldManager.addListener(listener);
    }

    @Override
    public void removeListener(EthereumListener listener) {
        this.worldManager.removeListener(listener);
    }

    @Override
    public void close() {

    }

    @Override
    public ImportResult addNewMinedBlock(final @Nonnull Block block) {
        final ImportResult importResult = worldManager.getBlockchain().tryToConnect(block);

        return importResult;
    }

    @Override
    public Transaction createTransaction(BigInteger nonce, BigInteger gasPrice, BigInteger gas, byte[] receiveAddress, BigInteger value, byte[] data) {
        return null;
    }

    @Override
    public Future<Transaction> submitTransaction(Transaction transaction) {
        tx = transaction;
        return null;
    }

    public Repository getRepository() {
        return repository;
    }

    @Override
    public void init() {

    }

    @Override
    public List<Transaction> getWireTransactions() {
        return null;
    }

    @Override
    public long getGasPrice() {
        return new GasPriceTracker().getGasPrice();
    }

    @Override
    public ProgramResult callConstant(Web3.CallArguments args) {
        return null;
    }
}
