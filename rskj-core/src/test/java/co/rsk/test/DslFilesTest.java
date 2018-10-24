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

package co.rsk.test;

import co.rsk.core.RskAddress;
import co.rsk.core.RskAddressTest;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Test;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.Arrays;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

/**
 * Created by ajlopez on 8/7/2016.
 */
public class DslFilesTest {
    @Test
    public void runAccounts01Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/accounts01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assert.assertNotNull(world.getAccountByName("acc1"));
    }

    @Test
    public void runTransfers01Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transfers01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assert.assertNotNull(world.getAccountByName("acc1"));
        Assert.assertNotNull(world.getAccountByName("acc2"));
        Assert.assertNotNull(world.getTransactionByName("tx01"));
        Assert.assertNotNull(world.getBlockByName("b01"));
    }

    @Test
    public void runCreate01Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/create01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        Assert.assertNotNull(transaction);

        TransactionInfo txinfo = world.getBlockChain().getTransactionInfo(transaction.getHash().getBytes());

        Assert.assertNotNull(txinfo);
        BigInteger gasUsed = BigIntegers.fromUnsignedByteArray(txinfo.getReceipt().getGasUsed());

        Assert.assertNotEquals(BigInteger.ZERO, gasUsed);
        // According to TestRPC and geth, the gas used is 0x010c2d
        Assert.assertEquals(BigIntegers.fromUnsignedByteArray(Hex.decode("010c2d")), gasUsed);
    }

    @Test
    public void runCreate02Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/create02.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assert.assertNotNull(world.getBlockChain().getBlockByHash(world.getBlockByName("g00").getHash().getBytes()));
        Assert.assertNotNull(world.getBlockChain().getBlockByHash(world.getBlockByName("b01").getHash().getBytes()));
        Assert.assertNotNull(world.getBlockChain().getBlockByHash(world.getBlockByName("b01b").getHash().getBytes()));
        Assert.assertNotNull(world.getBlockChain().getBlockByHash(world.getBlockByName("b02b").getHash().getBytes()));

        Block top1 = world.getBlockByName("b01");
        Block top2 = world.getBlockByName("b02b");

        Repository repo1 = world.getRepository().getSnapshotTo(top1.getStateRoot());
        Repository repo2 = world.getRepository().getSnapshotTo(top2.getStateRoot());

        RskAddress addr1 = new RskAddress("a0663f719962ec10bb57865532bef522059dfd96");
        RskAddress addr2 = new RskAddress("6252703f5ba322ec64d3ac45e56241b7d9e481ad");
        RskAddress addr3 = new RskAddress("8bdb1bf28586425b976b06a3079bd2c09a6f8e8b");

        // Sender account in branch 1
        Assert.assertNotNull(repo1.getCode(addr1));
        Assert.assertEquals(0, repo1.getCode(addr1).length);
        Assert.assertTrue(Arrays.equals(repo1.getAccountState(addr1).getStateRoot(), EMPTY_TRIE_HASH));

        // Contract account in branch 1
        Assert.assertNotNull(repo1.getCode(addr2));
        Assert.assertNotEquals(0, repo1.getCode(addr2).length);

        // Subcontract account in branch 1
        Assert.assertNotNull(repo1.getCode(addr3));
        Assert.assertNotEquals(0, repo1.getCode(addr3).length);

        // Sender account in branch 2
        Assert.assertNotNull(repo2.getCode(addr1));
        Assert.assertEquals(0, repo2.getCode(addr1).length);
        Assert.assertTrue(Arrays.equals(repo2.getAccountState(addr1).getStateRoot(), EMPTY_TRIE_HASH));

        // Contract account in branch 2
        Assert.assertFalse(Arrays.equals(repo2.getAccountState(addr2).getStateRoot(), EMPTY_TRIE_HASH));
        Assert.assertNotNull(repo2.getCode(addr2));
        Assert.assertNotEquals(0, repo2.getCode(addr2).length);

        // Subcontract account in branch 2
        Assert.assertFalse(Arrays.equals(repo2.getAccountState(addr3).getStateRoot(), EMPTY_TRIE_HASH));
        Assert.assertFalse(Arrays.equals(repo1.getAccountState(addr3).getStateRoot(), repo2.getAccountState(addr3).getStateRoot()));
        Assert.assertNotNull(repo2.getCode(addr3));
        Assert.assertNotEquals(0, repo2.getCode(addr3).length);
    }

    @Test
    public void runCreate03Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/create03.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assert.assertNotNull(world.getBlockChain().getBlockByHash(world.getBlockByName("g00").getHash().getBytes()));
        Assert.assertNotNull(world.getBlockChain().getBlockByHash(world.getBlockByName("b01").getHash().getBytes()));
        Assert.assertNotNull(world.getBlockChain().getBlockByHash(world.getBlockByName("b01b").getHash().getBytes()));

        Block top1 = world.getBlockByName("b01");
        Block top2 = world.getBlockByName("b01b");

        Repository repo1 = world.getRepository().getSnapshotTo(top1.getStateRoot());
        Repository repo2 = world.getRepository().getSnapshotTo(top2.getStateRoot());

        RskAddress addr1 = new RskAddress("a0663f719962ec10bb57865532bef522059dfd96");
        RskAddress addr2 = new RskAddress("6252703f5ba322ec64d3ac45e56241b7d9e481ad");

        // Sender account in branch 1
        Assert.assertNotNull(repo1.getCode(addr1));
        Assert.assertEquals(0, repo1.getCode(addr1).length);

        // Contract account in branch 1
        byte[] code1 = repo1.getCode(addr2);
        Assert.assertNotNull(code1);
        Assert.assertNotEquals(0, code1.length);

        // Sender account in branch 2
        byte[] code2 = repo2.getCode(addr2);
        Assert.assertNotNull(code2);
        Assert.assertNotEquals(0, code2.length);

        // code 1 != code 2
        Assert.assertFalse(Arrays.equals(code1, code2));
    }

    @Test
    public void runCreate04Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/create04.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assert.assertNotNull(world.getBlockChain().getBlockByHash(world.getBlockByName("g00").getHash().getBytes()));
        Assert.assertNotNull(world.getBlockChain().getBlockByHash(world.getBlockByName("b01").getHash().getBytes()));
        Assert.assertNotNull(world.getBlockChain().getBlockByHash(world.getBlockByName("b01b").getHash().getBytes()));

        Block top1 = world.getBlockByName("b01");
        Block top2 = world.getBlockByName("b01b");

        Repository repo1 = world.getRepository().getSnapshotTo(top1.getStateRoot());
        Repository repo2 = world.getRepository().getSnapshotTo(top2.getStateRoot());

        RskAddress addr1 = new RskAddress("a0663f719962ec10bb57865532bef522059dfd96");
        RskAddress addr2 = new RskAddress("6252703f5ba322ec64d3ac45e56241b7d9e481ad");

        // Sender account in branch 1
        Assert.assertNotNull(repo1.getCode(addr1));
        Assert.assertEquals(0, repo1.getCode(addr1).length);

        // Contract account in branch 1
        byte[] code1 = repo1.getCode(addr2);
        Assert.assertNotNull(code1);
        Assert.assertNotEquals(0, code1.length);

        // Sender account in branch 2
        byte[] code2 = repo2.getCode(addr2);
        Assert.assertNotNull(code2);
        Assert.assertEquals(0, code2.length);

        // code 1 != code 2
        Assert.assertFalse(Arrays.equals(code1, code2));
    }

    @Test
    public void runContracts01Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void runContracts02Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void runContracts03Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts03.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void runContracts04Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts04.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void runContracts05Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts05.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void runContracts06Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts06.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void runLogs01Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/logs01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // the transaction receipt should have three logs
        BlockChainStatus status = world.getBlockChain().getStatus();
        Assert.assertEquals(1, status.getBestBlockNumber());

        Block block = status.getBestBlock();

        Assert.assertEquals(1, block.getTransactionsList().size());
        byte[] txhash = block.getTransactionsList().get(0).getHash().getBytes();
        TransactionInfo txinfo = world.getBlockChain().getTransactionInfo(txhash);

        // only three events, raised by
        // Counter constructor
        // Counter getValue
        // Creator constructor
        Assert.assertEquals(3, txinfo.getReceipt().getLogInfoList().size());

        // only one topic in each event
        Assert.assertEquals(1, txinfo.getReceipt().getLogInfoList().get(0).getTopics().size());
        Assert.assertEquals(1, txinfo.getReceipt().getLogInfoList().get(1).getTopics().size());
        Assert.assertEquals(1, txinfo.getReceipt().getLogInfoList().get(2).getTopics().size());

        // the topics are different
        DataWord topic1 = txinfo.getReceipt().getLogInfoList().get(0).getTopics().get(0);
        DataWord topic2 = txinfo.getReceipt().getLogInfoList().get(1).getTopics().get(0);
        DataWord topic3 = txinfo.getReceipt().getLogInfoList().get(2).getTopics().get(0);

        Assert.assertNotEquals(topic1, topic2);
        Assert.assertNotEquals(topic1, topic3);
        Assert.assertNotEquals(topic2, topic3);

        // only the third log was directly produced by the created contract
        byte[] contractAddress = txinfo.getReceipt().getTransaction().getContractAddress().getBytes();

        Assert.assertFalse(Arrays.equals(contractAddress, txinfo.getReceipt().getLogInfoList().get(0).getAddress()));
        Assert.assertFalse(Arrays.equals(contractAddress, txinfo.getReceipt().getLogInfoList().get(1).getAddress()));
        Assert.assertTrue(Arrays.equals(contractAddress, txinfo.getReceipt().getLogInfoList().get(2).getAddress()));
    }

    @Test
    public void runContracts07Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts07.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);
    }

    @Test
    public void runBlocks01Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/blocks01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assert.assertNotNull(world.getBlockByName("b01"));
        Assert.assertNotNull(world.getBlockByName("b02"));
        Assert.assertEquals(2, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }

    @Test
    public void runBlocks02Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/blocks02.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assert.assertNotNull(world.getBlockByName("b01"));
        Assert.assertNotNull(world.getBlockByName("c01"));
        Assert.assertEquals(1, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }

    @Test
    public void runBlocks03Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/blocks03.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assert.assertNotNull(world.getBlockByName("b01"));
        Assert.assertNotNull(world.getBlockByName("b02"));
        Assert.assertEquals(2, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }

    @Test
    public void runUncles01Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/uncles01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assert.assertNotNull(world.getBlockByName("b01"));
        Assert.assertNotNull(world.getBlockByName("u01"));
        Assert.assertNotNull(world.getBlockByName("u02"));
        Assert.assertNotNull(world.getBlockByName("b02"));
        Assert.assertEquals(2, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }

    @Test
    public void runUncles02Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/uncles02.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assert.assertNotNull(world.getBlockByName("b01"));
        Assert.assertNotNull(world.getBlockByName("u01"));
        Assert.assertNotNull(world.getBlockByName("u02"));
        Assert.assertNotNull(world.getBlockByName("b02"));
        Assert.assertNotNull(world.getBlockByName("c02"));
        Assert.assertEquals(2, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }

    @Test
    public void runUncles03Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/uncles03.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assert.assertNotNull(world.getBlockByName("b01"));
        Assert.assertNotNull(world.getBlockByName("u01"));
        Assert.assertNotNull(world.getBlockByName("u02"));
        Assert.assertNotNull(world.getBlockByName("b02"));
        Assert.assertNotNull(world.getBlockByName("c02"));
        Assert.assertEquals(2, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }

    @Test
    public void runUncles04Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/uncles04.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assert.assertNotNull(world.getBlockByName("b01"));
        Assert.assertNotNull(world.getBlockByName("u01"));
        Assert.assertNotNull(world.getBlockByName("u02"));
        Assert.assertNotNull(world.getBlockByName("b02"));
        Assert.assertNotNull(world.getBlockByName("c02"));
        Assert.assertEquals(2, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }

    @Test
    public void runUncles05Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/uncles05.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assert.assertNotNull(world.getBlockByName("b01"));
        Assert.assertNotNull(world.getBlockByName("u01"));
        Assert.assertNotNull(world.getBlockByName("u02"));
        Assert.assertNotNull(world.getBlockByName("u03"));
        Assert.assertNotNull(world.getBlockByName("u04"));
        Assert.assertNotNull(world.getBlockByName("b02"));
        Assert.assertNotNull(world.getBlockByName("c02"));
        Assert.assertNotNull(world.getBlockByName("c03"));
        Assert.assertEquals(3, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }
}
