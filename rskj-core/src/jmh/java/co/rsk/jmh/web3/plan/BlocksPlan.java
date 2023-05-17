/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

package co.rsk.jmh.web3.plan;

import co.rsk.jmh.web3.BenchmarkWeb3Exception;
import co.rsk.jmh.web3.e2e.HttpRpcException;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.math.BigInteger;

@State(Scope.Benchmark)
public class BlocksPlan extends BasePlan {
    private String blockHash;
    private BigInteger blockNumber;
    private String txHash;
    private String txBlockHash;
    private int txIndex;
    //TODO get a valid address
    private String address;
    private BigInteger uncleIndex;

    @Override
    @Setup(Level.Trial)
    public void setUp(BenchmarkParams params) throws BenchmarkWeb3Exception {
        super.setUp(params);
        try {
            initParams();
        } catch (Exception e) {
            throw new BenchmarkWeb3Exception("Could not initialize plan. ", e);
        }
    }

    private void initParams() throws HttpRpcException {
        if (config.startsWith("regtest")) {
            initRegTest();
        }else{
            initTestNet();
        }
    }

    private void initTestNet() {
        blockHash = configuration.getString("block.hash");
        blockNumber = BigInteger.valueOf(configuration.getLong("block.number"));
        txHash = configuration.getString("tx.hash");
        txBlockHash = configuration.getString("tx.blockHash");
        txIndex = configuration.getInt("tx.index");
        address = configuration.getString("address");
        uncleIndex = BigInteger.valueOf(configuration.getInt("uncle.index"));
    }

    private void initRegTest() throws HttpRpcException {
        String lastBlockNumber = this.getWeb3Connector().ethBlockNumber();
        BigInteger blockNumberInt = BigInteger.valueOf(Long.parseLong(lastBlockNumber));
        EthBlock.Block block = web3Connector.ethGetBlockByNumber(blockNumberInt, true).getBlock();

        blockHash = block.getHash();
        blockNumber = block.getNumber();
        EthBlock.TransactionObject tx = (EthBlock.TransactionObject) block.getTransactions().get(0).get();
        txHash = tx.getHash();
        txBlockHash = tx.getBlockHash();
        txIndex = tx.getTransactionIndex().intValue();
        address = tx.getFrom();
        uncleIndex = BigInteger.valueOf(0);
    }

    public String getBlockHash() {
        return blockHash;
    }

    public BigInteger getBlockNumber() {
        return blockNumber;
    }

    public String getTxHash() {
        return txHash;
    }

    public int getTxIndex() {
        return txIndex;
    }

    public String getAddress() {
        return address;
    }

    public BigInteger getUncleIndex() {
        return uncleIndex;
    }

    public String getTxBlockHash() {
        return txBlockHash;
    }
}
