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

package co.rsk.core;

import co.rsk.asm.EVMAssembler;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.BridgeSupportFactory;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.crypto.ECKey;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.ethereum.db.IndexedBlockStore;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by Sergio on 26/02/2017.
 */
public class CodeReplaceTest {

    private TestSystemProperties config = new TestSystemProperties() {
        @Override
        public ActivationConfig getActivationConfig() {
            return ActivationConfigsForTest.allBut(ConsensusRule.RSKIP94);
        }
    };
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

    @Test
    public void replaceCodeTest1() throws InterruptedException {
        BigInteger nonce = config.getNetworkConstants().getInitialNonce();
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));
        BlockStore blockStore = getBlockStore();
        BlockChainImpl blockchain = org.ethereum.core.ImportLightTest.createBlockchain(
                new TestGenesisLoader(
                        repository, getClass().getResourceAsStream("/genesis/genesis-light.json"), nonce,
                        false, true, true
                ).load(),
                config,
                repository,
                blockStore,
                trieStore
        );

        ECKey sender = ECKey.fromPrivate(Hex.decode("3ec771c31cac8c0dba77a69e503765701d3c2bb62435888d4ffa38fed60c445c"));
        System.out.println("address: " + Hex.toHexString(sender.getAddress()));

        String asm ="0x14 0x0C 0x00 CODECOPY " + // (7b) Extract real code into address 0, skip first 12 bytes, copy 20 bytes
                "0x14 0x00  RETURN " + // (5b) offset 0, size 0x14, now return the first code
                "HEADER !0x01 !0x01 !0x00 "+ // (4b) header script v1
                "0x00 CALLDATALOAD " + // (3b) store at offset 0, read data from offset 0. Transfer 32 bytes
                "0x00 MSTORE " + // (3b) store the data at address 0
                // We replace TWO TIMES to make sure that only the last replacement takes place
                "0x01 0x00 CODEREPLACE " + // (5b) set new code: offset 0, size 1 bytes.
                // This is the good one.
                "0x10 0x00 CODEREPLACE"; // (5b) set new code: offset 0, size 16 bytes.

        EVMAssembler assembler = new EVMAssembler();
        byte[] code = assembler.assemble(asm);

        // Creates a contract
        Transaction tx1 = createTx(sender, new byte[0], code, repository);
        executeTransaction(blockchain, tx1, repository, blockStore);
        // Now we can directly check the store and see the new code.
        RskAddress createdContract = tx1.getContractAddress();
        byte[] expectedCode  = Arrays.copyOfRange(code, 12, 12+20);
        byte[] installedCode = repository.getCode(createdContract);
        // assert the contract has been created
        Assert.assertArrayEquals(expectedCode, installedCode);

        // Note that this code does not have a header, then its version == 0
        String asm2 =
                    "0xFF 0x00 MSTORE "+ // (5b) Store at address 0x00, the value 0xFF
                    "0x01 0x1F RETURN " + // (5b) And return such value 0xFF, at offset 0x1F
                    // fill with nops to make it 16 bytes in length
                    "STOP STOP STOP STOP STOP STOP"; // 16

        byte[] code2 = assembler.assemble(asm2);

        // The second transaction changes the contract code
        Transaction tx2 = createTx(sender, tx1.getContractAddress().getBytes(), code2, repository);
        TransactionExecutor executor2 = executeTransaction(blockchain, tx2, repository, blockStore);
        byte[] installedCode2 = repository.getCode(createdContract);
        // assert the contract code has been created
        Assert.assertArrayEquals(installedCode2, code2);
        Assert.assertEquals(1, executor2.getResult().getCodeChanges().size()); // there is one code change

        // We could add a third tx to execute the new code
        Transaction tx3 = createTx(sender, tx1.getContractAddress().getBytes(), new byte[0], repository);
        TransactionExecutor executor3 = executeTransaction(blockchain, tx3, repository, blockStore);
        // check return code from contract call
        Assert.assertArrayEquals(Hex.decode("FF"), executor3.getResult().getHReturn());
    }

    @Test
    public void replaceCodeTest2() throws InterruptedException {
        // We test code replacement during initialization: this is forbitten.

        BigInteger nonce = config.getNetworkConstants().getInitialNonce();
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));
        BlockStore blockStore = getBlockStore();
        BlockChainImpl blockchain = org.ethereum.core.ImportLightTest.createBlockchain(
                new TestGenesisLoader(
                        repository, getClass().getResourceAsStream("/genesis/genesis-light.json"), nonce,
                        false, true, true
                ).load(),
                config,
                repository,
                blockStore,
                trieStore
        );

        ECKey sender = ECKey.fromPrivate(Hex.decode("3ec771c31cac8c0dba77a69e503765701d3c2bb62435888d4ffa38fed60c445c"));
        System.out.println("address: " + Hex.toHexString(sender.getAddress()));

        String asm =
                "HEADER !0x01 !0x01 !0x00 "+ // (4b) header script v1
                "0x01 0x00 CODEREPLACE "+ // (5b) we attempt to replace the code
                "0x01 0x15 0x00 CODECOPY " + // (7b) Extract real code into address 0, skip first 12 bytes, copy 1 bytes
                "0x01 0x00  RETURN " + // (5b) offset 0, size 0x01, now return the first code
                "STOP "; // (1b) REAL code to install

        EVMAssembler assembler = new EVMAssembler();
        byte[] code = assembler.assemble(asm);

        // Creates a contract
        Transaction tx1 = createTx(sender, new byte[0], code, repository);
        TransactionExecutor executor1 = executeTransaction(blockchain, tx1, repository, blockStore);
        // Now we can directly check the store and see the new code.
        Assert.assertNotNull(executor1.getResult().getException());
    }

    @Test
    public void replaceCodeTest3() throws InterruptedException {
        TestSystemProperties oldConfig = config;
        config = new TestSystemProperties();
        BigInteger nonce = config.getNetworkConstants().getInitialNonce();
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));
        BlockStore blockStore = getBlockStore();
        BlockChainImpl blockchain = org.ethereum.core.ImportLightTest.createBlockchain(
                new TestGenesisLoader(
                        repository, getClass().getResourceAsStream("/genesis/genesis-light.json"), nonce,
                        false, true, true
                ).load(),
                config,
                repository,
                blockStore,
                trieStore
        );

        ECKey sender = ECKey.fromPrivate(Hex.decode("3ec771c31cac8c0dba77a69e503765701d3c2bb62435888d4ffa38fed60c445c"));
        System.out.println("address: " + Hex.toHexString(sender.getAddress()));

        String asm ="0x14 0x0C 0x00 CODECOPY " + // (7b) Extract real code into address 0, skip first 12 bytes, copy 20 bytes
                "0x14 0x00  RETURN " + // (5b) offset 0, size 0x14, now return the first code
                "HEADER !0x01 !0x01 !0x00 "+ // (4b) header script v1
                "0x00 CALLDATALOAD " + // (3b) store at offset 0, read data from offset 0. Transfer 32 bytes
                "0x00 MSTORE " + // (3b) store the data at address 0
                // We replace TWO TIMES to make sure that only the last replacement takes place
                "0x01 0x00 CODEREPLACE " + // (5b) set new code: offset 0, size 1 bytes.
                // This is the good one.
                "0x10 0x00 CODEREPLACE"; // (5b) set new code: offset 0, size 16 bytes.

        EVMAssembler assembler = new EVMAssembler();
        byte[] code = assembler.assemble(asm);

        // Creates a contract
        Transaction tx1 = createTx(sender, new byte[0], code, repository);
        executeTransaction(blockchain, tx1, repository, blockStore);
        // Now we can directly check the store and see the new code.
        RskAddress createdContract = tx1.getContractAddress();
        byte[] expectedCode  = Arrays.copyOfRange(code, 12, 12+20);
        byte[] installedCode = repository.getCode(createdContract);
        // assert the contract has been created
        Assert.assertArrayEquals(expectedCode, installedCode);

        String asm2 =
                "0xFF 0x00 MSTORE "+ // (5b) Store at address 0x00, the value 0xFF
                        "0x01 0x1F RETURN " + // (5b) And return such value 0xFF, at offset 0x1F
                        // fill with nops to make it 16 bytes in length
                        "STOP STOP STOP STOP STOP STOP"; // 16

        byte[] code2 = assembler.assemble(asm2);

        Transaction tx2 = createTx(sender, tx1.getContractAddress().getBytes(), code2, repository);
        TransactionExecutor executor2 = executeTransaction(blockchain, tx2, repository, blockStore);
        // code remains the same
        Assert.assertArrayEquals(code2, code2);
        Assert.assertEquals(0, executor2.getResult().getCodeChanges().size()); // there is no code change
        // invalid opcode exception
        Assert.assertNotNull(executor2.getResult().getException());

        config = oldConfig;
    }

    private BlockStore getBlockStore() {
        return new IndexedBlockStore(blockFactory, new HashMap<>(), new HashMapDB(), null);
    }

    protected Transaction createTx(ECKey sender, byte[] receiveAddress, byte[] data, Repository repository) throws InterruptedException {
        return createTx(sender, receiveAddress, data, 0, repository.getNonce(new RskAddress(sender.getAddress())));
    }

    protected Transaction createTx(ECKey sender, byte[] receiveAddress,
                                   byte[] data, long value, final BigInteger nonce) throws InterruptedException {
        Transaction tx = new Transaction(
                ByteUtil.bigIntegerToBytes(nonce),
                ByteUtil.longToBytesNoLeadZeroes(1),
                ByteUtil.longToBytesNoLeadZeroes(3_000_000),
                receiveAddress,
                ByteUtil.longToBytesNoLeadZeroes(value),
                data,
                config.getNetworkConstants().getChainId());
        tx.sign(sender.getPrivKeyBytes());
        return tx;
    }

    public TransactionExecutor executeTransaction(BlockChainImpl blockchain,
                                                  Transaction tx,
                                                  Repository repository,
                                                  BlockStore blockStore) {
        Repository track = repository.startTracking();

        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(config.getNetworkConstants().getBridgeConstants().getBtcParams()),
                config.getNetworkConstants().getBridgeConstants(),
                config.getActivationConfig());
        TransactionExecutorFactory transactionExecutorFactory = new TransactionExecutorFactory(
                config,
                blockStore,
                null,
                blockFactory,
                new ProgramInvokeFactoryImpl(),
                new PrecompiledContracts(config, bridgeSupportFactory));
        TransactionExecutor executor = transactionExecutorFactory
                .newInstance(tx, 0, RskAddress.nullAddress(), repository, blockchain.getBestBlock(), 0);

        executor.init();
        executor.execute();
        executor.go();
        executor.finalization();

        track.commit();
        return executor;
    }
}
