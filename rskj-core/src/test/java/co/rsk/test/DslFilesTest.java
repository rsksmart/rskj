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

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockChainStatus;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Created by ajlopez on 8/7/2016.
 */
class DslFilesTest {
    @Test
    void runAccounts01Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/accounts01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assertions.assertNotNull(world.getAccountByName("acc1"));
    }

    @Test
    void runTransfers01Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/transfers01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assertions.assertNotNull(world.getAccountByName("acc1"));
        Assertions.assertNotNull(world.getAccountByName("acc2"));
        Assertions.assertNotNull(world.getTransactionByName("tx01"));
        Assertions.assertNotNull(world.getBlockByName("b01"));
    }

    @Test
    void runCreate01Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/create01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("tx01");

        Assertions.assertNotNull(transaction);

        TransactionInfo txinfo = world.getBlockChain().getTransactionInfo(transaction.getHash().getBytes());

        Assertions.assertNotNull(txinfo);
        BigInteger gasUsed = BigIntegers.fromUnsignedByteArray(txinfo.getReceipt().getGasUsed());

        Assertions.assertNotEquals(BigInteger.ZERO, gasUsed);
        // According to TestRPC and geth, the gas used is 0x010c2d
        Assertions.assertEquals(BigIntegers.fromUnsignedByteArray(Hex.decode("fd59")), gasUsed);
    }

    @Test
    void runBridgeDelegateCallResource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/bridgeDelegateCall.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        /// In this test we call the bridge with different methods
        /// CALL, DELEGATECALL, CALLCODE, STATICCALL
        /// The only one having to produce a releaseBtc blog is the CALL opcode which is the first one
        /// This test relies on the current bridge behavior of emitting logs
        Transaction transaction = world.getTransactionByName("tx01");
        Assertions.assertNotNull(transaction);
        TransactionInfo txinfo = world.getBlockChain().getTransactionInfo(transaction.getHash().getBytes());
        Assertions.assertEquals(1, txinfo.getReceipt().getLogInfoList().size());

        transaction = world.getTransactionByName("tx02");
        Assertions.assertNotNull(transaction);
        txinfo = world.getBlockChain().getTransactionInfo(transaction.getHash().getBytes());
        Assertions.assertEquals(0, txinfo.getReceipt().getLogInfoList().size());

        transaction = world.getTransactionByName("tx03");
        Assertions.assertNotNull(transaction);
        txinfo = world.getBlockChain().getTransactionInfo(transaction.getHash().getBytes());
        Assertions.assertEquals(0, txinfo.getReceipt().getLogInfoList().size());

        transaction = world.getTransactionByName("tx04");
        Assertions.assertNotNull(transaction);
        txinfo = world.getBlockChain().getTransactionInfo(transaction.getHash().getBytes());
        Assertions.assertEquals(0, txinfo.getReceipt().getLogInfoList().size());
    }
    @Test
    void runCreate02Resource() throws FileNotFoundException, DslProcessorException {
        //byte[] code2 =Hex.decode("608060405234801561001057600080fd5b504361001a61017b565b80828152602001915050604051809103906000f080158015610040573d6000803e3d6000fd5b506000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055507f80ae3ec8027d0c5d1f3e47fb4bf1d9fc28225e7f4bcb1971b36efb81fe40574d6000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1663209652556040518163ffffffff167c0100000000000000000000000000000000000000000000000000000000028152600401602060405180830381600087803b15801561012657600080fd5b505af115801561013a573d6000803e3d6000fd5b505050506040513d602081101561015057600080fd5b81019080805190602001909291905050506040518082815260200191505060405180910390a161018b565b6040516101e38061028283390190565b60e9806101996000396000f300608060405260043610603f576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff16806361bc221a146044575b600080fd5b348015604f57600080fd5b5060566098565b604051808273ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200191505060405180910390f35b6000809054906101000a900473ffffffffffffffffffffffffffffffffffffffff16815600a165627a7a7230582091284634a2c8e5cbd0e4153a1422a41670914d8ef8b4f7dc71bd54cf80baf8f50029608060405234801561001057600080fd5b506040516020806101e383398101806040528101908080519060200190929190505050806000819055507f06acbfb32bcf8383f3b0a768b70ac9ec234ea0f2d3b9c77fa6a2de69b919aad16000546040518082815260200191505060405180910390a150610160806100836000396000f30060806040526004361061004c576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680632096525514610051578063d09de08a1461007c575b600080fd5b34801561005d57600080fd5b50610066610093565b6040518082815260200191505060405180910390f35b34801561008857600080fd5b506100916100d6565b005b60007f1ee041944547858a75ebef916083b6d4f5ae04bea9cd809334469dd07dbf441b6000546040518082815260200191505060405180910390a1600054905090565b600080815460010191905081905550600160026000548115156100f557fe5b061415157f6e61ef44ac2747ff8b84d353a908eb8bd5c3fb118334d57698c5cfc7041196ad6000546040518082815260200191505060405180910390a25600a165627a7a7230582041617f72986040ac8590888e68e070d9d05aeb99361c0c77d1f67540db5ff6b10029");
        //System.out.print(Program.stringifyMultiline(code2));


        DslParser parser = DslParser.fromResource("dsl/create02.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assertions.assertNotNull(world.getBlockChain().getBlockByHash(world.getBlockByName("g00").getHash().getBytes()));
        Assertions.assertNotNull(world.getBlockChain().getBlockByHash(world.getBlockByName("b01").getHash().getBytes()));
        Assertions.assertNotNull(world.getBlockChain().getBlockByHash(world.getBlockByName("b01b").getHash().getBytes()));
        Assertions.assertNotNull(world.getBlockChain().getBlockByHash(world.getBlockByName("b02b").getHash().getBytes()));

        Block top1 = world.getBlockByName("b01");
        Block top2 = world.getBlockByName("b02b");

        // Creates a new view of the repository, standing on top1 state
        Repository repo1 = new MutableRepository(world.getTrieStore(),
                world.getTrieStore().retrieve(top1.getStateRoot()).get());

        // Creates a new view of the repository, standing on top2 state
        Repository repo2 = new MutableRepository(world.getTrieStore(),
                world.getTrieStore().retrieve(top2.getStateRoot()).get());
        // addr1: sender's account
        RskAddress addr1 = new RskAddress("a0663f719962ec10bb57865532bef522059dfd96");
        // addr2: Parent Contract account
        RskAddress addr2 = new RskAddress("6252703f5ba322ec64d3ac45e56241b7d9e481ad");
        // add3: child contract account
        RskAddress addr3 = new RskAddress("8bdb1bf28586425b976b06a3079bd2c09a6f8e8b");

        // Sender account in branch 1
        // Null means no code
        Assertions.assertNull(repo1.getCode(addr1));
        Assertions.assertFalse(repo1.isContract(addr1));


        // "Creator" Contract account in branch 1
        // This contract has storage.
        Assertions.assertTrue(repo1.isContract(addr2));

        Assertions.assertNotNull(repo1.getCode(addr2));
        Assertions.assertNotEquals(0, repo1.getCode(addr2).length);

        // Subcontract account in branch 1.
        Assertions.assertNotNull(repo1.getCode(addr3));
        Assertions.assertNotEquals(0, repo1.getCode(addr3).length);

        // Sender account in branch 2
        Assertions.assertNull(repo2.getCode(addr1));
        Assertions.assertFalse(repo2.isContract(addr1));

        // "Creator" Contract account in branch 2
        // This contract has no childs?
        Assertions.assertTrue(repo2.isContract(addr2));
        Assertions.assertNotNull(repo2.getCode(addr2));
        Assertions.assertNotEquals(0, repo2.getCode(addr2).length);

        // Subcontract account in branch 2
        Assertions.assertTrue(repo2.isContract(addr3));
        Assertions.assertNotNull(repo2.getCode(addr3));
        Assertions.assertNotEquals(0, repo2.getCode(addr3).length);
    }

    @Test
    void runCreateContractAndPreserveBalance() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/create_and_preserve_balance.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assertions.assertEquals(Coin.valueOf(100L), getBalance(world, "6252703f5ba322ec64d3ac45e56241b7d9e481ad"));
    }

    /**
     * This test covers the expected behavior BEFORE implementing the RSKIP174
     * https://github.com/rsksmart/RSKIPs/pull/260
     * */
    @Test
    void runCreateContractAndPreserveNoBalance() throws FileNotFoundException, DslProcessorException {
        TestSystemProperties rskip174Disabled = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.hardforkActivationHeights.iris300", ConfigValueFactory.fromAnyRef(-1))
        );
        DslParser parser = DslParser.fromResource("dsl/create_and_preserve_no_balance.txt");
        World world = new World(rskip174Disabled);

        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assertions.assertEquals(Coin.valueOf(0L), getBalance(world, "6252703f5ba322ec64d3ac45e56241b7d9e481ad"));
    }

    @Test
    void runContracts01Resource() throws FileNotFoundException {
        DslParser parser = DslParser.fromResource("dsl/contracts01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        Assertions.assertDoesNotThrow(() -> processor.processCommands(parser));
    }

    @Test
    void runContracts02Resource() throws FileNotFoundException {
        DslParser parser = DslParser.fromResource("dsl/contracts02.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        Assertions.assertDoesNotThrow(() -> processor.processCommands(parser));
    }

    @Test
    void runContracts03Resource() throws FileNotFoundException {
        DslParser parser = DslParser.fromResource("dsl/contracts03.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        Assertions.assertDoesNotThrow(() -> processor.processCommands(parser));
    }

    @Test
    void runContracts04Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts04.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        Assertions.assertDoesNotThrow(() -> processor.processCommands(parser));
    }

    @Test
    void runContracts05Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts05.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        Assertions.assertDoesNotThrow(() -> processor.processCommands(parser));
    }

    @Test
    void runContracts06Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts06.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        Assertions.assertDoesNotThrow(() -> processor.processCommands(parser));
    }

    @Test
    void runLogs01Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/logs01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        // the transaction receipt should have three logs
        BlockChainStatus status = world.getBlockChain().getStatus();
        Assertions.assertEquals(1, status.getBestBlockNumber());

        Block block = status.getBestBlock();

        Assertions.assertEquals(1, block.getTransactionsList().size());
        byte[] txhash = block.getTransactionsList().get(0).getHash().getBytes();
        TransactionInfo txinfo = world.getBlockChain().getTransactionInfo(txhash);

        // only three events, raised by
        // Counter constructor
        // Counter getValue
        // Creator constructor
        Assertions.assertEquals(3, txinfo.getReceipt().getLogInfoList().size());

        // only one topic in each event
        Assertions.assertEquals(1, txinfo.getReceipt().getLogInfoList().get(0).getTopics().size());
        Assertions.assertEquals(1, txinfo.getReceipt().getLogInfoList().get(1).getTopics().size());
        Assertions.assertEquals(1, txinfo.getReceipt().getLogInfoList().get(2).getTopics().size());

        // the topics are different
        DataWord topic1 = txinfo.getReceipt().getLogInfoList().get(0).getTopics().get(0);
        DataWord topic2 = txinfo.getReceipt().getLogInfoList().get(1).getTopics().get(0);
        DataWord topic3 = txinfo.getReceipt().getLogInfoList().get(2).getTopics().get(0);

        Assertions.assertNotEquals(topic1, topic2);
        Assertions.assertNotEquals(topic1, topic3);
        Assertions.assertNotEquals(topic2, topic3);

        // only the third log was directly produced by the created contract
        byte[] contractAddress = txinfo.getReceipt().getTransaction().getContractAddress().getBytes();

        Assertions.assertFalse(Arrays.equals(contractAddress, txinfo.getReceipt().getLogInfoList().get(0).getAddress()));
        Assertions.assertFalse(Arrays.equals(contractAddress, txinfo.getReceipt().getLogInfoList().get(1).getAddress()));
        Assertions.assertArrayEquals(contractAddress, txinfo.getReceipt().getLogInfoList().get(2).getAddress());
    }

    @Test
    void runContracts07Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/contracts07.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        Assertions.assertDoesNotThrow(() -> processor.processCommands(parser));
    }

    @Test
    void runBlocks01Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/blocks01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assertions.assertNotNull(world.getBlockByName("b01"));
        Assertions.assertNotNull(world.getBlockByName("b02"));
        Assertions.assertEquals(2, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }

    @Test
    void runBlocks02Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/blocks02.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assertions.assertNotNull(world.getBlockByName("b01"));
        Assertions.assertNotNull(world.getBlockByName("c01"));
        Assertions.assertEquals(1, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }

    @Test
    void runBlocks03Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/blocks03.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assertions.assertNotNull(world.getBlockByName("b01"));
        Assertions.assertNotNull(world.getBlockByName("b02"));
        Assertions.assertEquals(2, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }

    @Test
    void runUncles01Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/uncles01.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assertions.assertNotNull(world.getBlockByName("b01"));
        Assertions.assertNotNull(world.getBlockByName("u01"));
        Assertions.assertNotNull(world.getBlockByName("u02"));
        Assertions.assertNotNull(world.getBlockByName("b02"));
        Assertions.assertEquals(2, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }

    @Test
    void runUncles02Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/uncles02.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assertions.assertNotNull(world.getBlockByName("b01"));
        Assertions.assertNotNull(world.getBlockByName("u01"));
        Assertions.assertNotNull(world.getBlockByName("u02"));
        Assertions.assertNotNull(world.getBlockByName("b02"));
        Assertions.assertNotNull(world.getBlockByName("c02"));
        Assertions.assertEquals(2, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }

    @Test
    void runUncles03Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/uncles03.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assertions.assertNotNull(world.getBlockByName("b01"));
        Assertions.assertNotNull(world.getBlockByName("u01"));
        Assertions.assertNotNull(world.getBlockByName("u02"));
        Assertions.assertNotNull(world.getBlockByName("b02"));
        Assertions.assertNotNull(world.getBlockByName("c02"));
        Assertions.assertEquals(2, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }

    @Test
    void runUncles04Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/uncles04.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assertions.assertNotNull(world.getBlockByName("b01"));
        Assertions.assertNotNull(world.getBlockByName("u01"));
        Assertions.assertNotNull(world.getBlockByName("u02"));
        Assertions.assertNotNull(world.getBlockByName("b02"));
        Assertions.assertNotNull(world.getBlockByName("c02"));
        Assertions.assertEquals(2, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }

    @Test
    void runUncles05Resource() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/uncles05.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Assertions.assertNotNull(world.getBlockByName("b01"));
        Assertions.assertNotNull(world.getBlockByName("u01"));
        Assertions.assertNotNull(world.getBlockByName("u02"));
        Assertions.assertNotNull(world.getBlockByName("u03"));
        Assertions.assertNotNull(world.getBlockByName("u04"));
        Assertions.assertNotNull(world.getBlockByName("b02"));
        Assertions.assertNotNull(world.getBlockByName("c02"));
        Assertions.assertNotNull(world.getBlockByName("c03"));
        Assertions.assertEquals(3, world.getBlockChain().getStatus().getBestBlock().getNumber());
    }

    @Test
    void runCreateAfterSuicide() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/createAfterSuicide.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("callSecondCreator");

        Assertions.assertNotNull(transaction);

        TransactionInfo txinfo = world.getBlockChain().getTransactionInfo(transaction.getHash().getBytes());

        Assertions.assertNotNull(txinfo);
        long gasUsed = BigIntegers.fromUnsignedByteArray(txinfo.getReceipt().getGasUsed()).longValue();

        Assertions.assertEquals(200000, gasUsed);
        Assertions.assertFalse(world.getRepository().isExist(new RskAddress("0xa943B74640c466Fc700AF929Cabacb1aC6CC8895")), "Address should not exist");
    }

    @Test
    void runCodeSizeAfterSuicide() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/codeSizeAfterSuicide.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        Transaction transaction = world.getTransactionByName("callCodeSizeChecker");

        Assertions.assertNotNull(transaction);

        TransactionInfo txinfo = world.getBlockChain().getTransactionInfo(transaction.getHash().getBytes());

        Assertions.assertNotNull(txinfo);
        long gasUsed = BigIntegers.fromUnsignedByteArray(txinfo.getReceipt().getGasUsed()).longValue();

        // Gas consumed SHOULD NOT be all there is available
        Assertions.assertNotEquals(200000, gasUsed);
        Assertions.assertFalse(txinfo.getReceipt().isSuccessful(), "Transaction should be reverted");
    }


    @Test
    void onReorganizationTxGetsReaddedToTxPool() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/reorganization.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        //But the tx is now on the tx pool
        Assertions.assertEquals(1, world.getTransactionPool().getPendingTransactions().size());
        Assertions.assertEquals(Coin.valueOf(1000), world.getTransactionPool().getPendingTransactions().get(0).getValue());
    }

    @Test
    void onReorganizationTxDoesNotGetsReaddedToTxPoolIfPresentOnBothChains() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/reorganization_same_tx_on_both.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        //The tx is NOT in the tx pool
        Assertions.assertEquals(0, world.getTransactionPool().getPendingTransactions().size());
    }

    @Test
    void onReorganizationTxDoesNotGetsReaddedIfCompetingTxIsOnBlock() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/reorganization_different_tx_on_both_same_nonce.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        //The tx is NOT in the tx pool
        Assertions.assertEquals(0, world.getTransactionPool().getPendingTransactions().size());
    }

    @Test
    void onReorganizationTxDoesGetsReaddedIfNonCompetingTxIsOnBlock() throws FileNotFoundException, DslProcessorException {
        DslParser parser = DslParser.fromResource("dsl/reorganization_different_non_competing_tx.txt");
        World world = new World();
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        //The transaction that was on the discarded block is on the tx pool
        Assertions.assertEquals(1, world.getTransactionPool().getPendingTransactions().size());
        Assertions.assertEquals(Coin.valueOf(1000), world.getTransactionPool().getPendingTransactions().get(0).getValue());
        Assertions.assertEquals(world.getAccountByName("acc1").getAddress(), world.getTransactionPool().getPendingTransactions().get(0).getSender());
    }

    private static Coin getBalance(World world, String address) {
        Block bestBlock = world.getBlockChain().getBestBlock();
        Repository repo = new MutableRepository(world.getTrieStore(), world.getTrieStore().retrieve(bestBlock.getStateRoot()).get());
        RskAddress rskAddress = new RskAddress(address);

        return repo.getBalance(rskAddress);
    }
}
