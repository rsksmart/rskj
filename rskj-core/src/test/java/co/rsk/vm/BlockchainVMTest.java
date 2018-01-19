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

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.test.World;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 4/20/2016.
 */
public class BlockchainVMTest {
    private static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};

    @Test
    public void genesisTest() {
        Block genesis = new BlockGenerator().getGenesisBlock();
        Assert.assertEquals(0, genesis.getNumber());
    }

    private static Coin faucetAmount = Coin.valueOf(1000000000L);

    public static class NewBlockChainInfo {
        public Blockchain blockchain;
        public ECKey faucetKey;
        public Repository repository;
    }
    static long addrCounter =1;

    byte[] randomAddress() {
        byte[] ret = ByteBuffer.allocate(20).putLong(addrCounter).array();
        addrCounter++;
        return ret;
    }

    @Test
    public void testSEND_1() {
        NewBlockChainInfo binfo = createNewBlockchain();
        Blockchain blockchain = binfo.blockchain;
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createChildBlock(blockchain.getBestBlock(), null, binfo.repository.getRoot());
        List<Transaction> txs = new ArrayList<>();
        Coin transferAmount = Coin.valueOf(100L);
        // Add a single transaction paying to a new address
        byte[] dstAddress = randomAddress();
        BigInteger transactionGasLimit = new BigInteger("21000");
        Coin transactionGasPrice = Coin.valueOf(1);
        Transaction t = new Transaction(
                ZERO_BYTE_ARRAY,
                transactionGasPrice.getBytes(),
                transactionGasLimit.toByteArray(),
                dstAddress ,
                transferAmount.getBytes(),
                null,
                new RskSystemProperties().getBlockchainConfig().getCommonConstants().getChainId());

        t.sign(binfo.faucetKey.getPrivKeyBytes());
        txs.add(t);

        Block block2 = blockGenerator.createChildBlock(block1, txs, binfo.repository.getRoot());
        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block1));

        MinerHelper mh = new MinerHelper(
                binfo.repository, binfo.blockchain);

        mh.completeBlock(block2, block1);

        Assert.assertEquals(ImportResult.IMPORTED_BEST, blockchain.tryToConnect(block2));

        Assert.assertEquals(blockchain.getBestBlock(), block2);
        Assert.assertEquals(2, block2.getNumber());

        Coin srcAmount = faucetAmount.subtract(transferAmount);
        srcAmount = srcAmount.subtract(transactionGasPrice.multiply(transactionGasLimit));

        Assert.assertEquals(
                binfo.repository.getBalance(new RskAddress(binfo.faucetKey.getAddress())),
                srcAmount);

        Assert.assertEquals(
                binfo.repository.getBalance(new RskAddress(dstAddress)),
                transferAmount);
    }

    private static NewBlockChainInfo createNewBlockchain() {
        World world = new World();
        NewBlockChainInfo binfo = new NewBlockChainInfo();

        binfo.faucetKey = createFaucetAccount(world);
        binfo.blockchain = world.getBlockChain();
        binfo.repository = world.getRepository();
        return binfo;
    }

    private static ECKey createFaucetAccount(World world) {
        co.rsk.test.builders.AccountBuilder builder = new co.rsk.test.builders.AccountBuilder(world);
        builder.name("faucet");

        builder.balance(faucetAmount);

        Account account = builder.build();

        world.saveAccount("faucet", account);

        return account.getEcKey();
    }

}
