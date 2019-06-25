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
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import java.math.BigInteger;
import java.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Test;

/** Created by Sergio on 26/02/2017. */
public class CodeReplaceTest {

    private TestSystemProperties config =
            new TestSystemProperties() {
                @Override
                public ActivationConfig getActivationConfig() {
                    return ActivationConfigsForTest.allBut(ConsensusRule.RSKIP94);
                }
            };
    private final BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());

    @Test
    public void replaceCodeTest1() throws InterruptedException {

        BigInteger nonce = config.getNetworkConstants().getInitialNonce();
        BlockChainImpl blockchain =
                org.ethereum.core.ImportLightTest.createBlockchain(
                        GenesisLoader.loadGenesis(
                                nonce,
                                getClass().getResourceAsStream("/genesis/genesis-light.json"),
                                false,
                                true,
                                true),
                        config);

        ECKey sender =
                ECKey.fromPrivate(
                        Hex.decode(
                                "3ec771c31cac8c0dba77a69e503765701d3c2bb62435888d4ffa38fed60c445c"));
        System.out.println("address: " + Hex.toHexString(sender.getAddress()));

        String asm =
                // (7b) Extract real code into address 0, skip first 12 bytes, copy 20 bytes
                "0x14 0x0C 0x00 CODECOPY "
                        // (5b) offset 0, size 0x14, now return the first code
                        + "0x14 0x00  RETURN "
                        // (4b) header script v1
                        + "HEADER !0x01 !0x01 !0x00 "
                        // (3b) store at offset 0, read data from offset 0. Transfer 32 bytes
                        + "0x00 CALLDATALOAD "
                        // (3b) store the data at address 0
                        + "0x00 MSTORE "
                        // We replace TWO TIMES to make sure that only the last replacement takes
                        // place
                        // (5b) set new code: offset 0, size 1 bytes.
                        + "0x01 0x00 CODEREPLACE "
                        // This is the good one.
                        // (5b) set new code: offset 0, size 16 bytes.
                        + "0x10 0x00 CODEREPLACE";

        EVMAssembler assembler = new EVMAssembler();
        byte[] code = assembler.assemble(asm);

        // Creates a contract
        Transaction tx1 = createTx(blockchain, sender, new byte[0], code);
        executeTransaction(blockchain, tx1);
        // Now we can directly check the store and see the new code.
        RskAddress createdContract = tx1.getContractAddress();
        byte[] expectedCode = Arrays.copyOfRange(code, 12, 12 + 20);
        byte[] installedCode = blockchain.getRepository().getCode(createdContract);
        // assert the contract has been created
        Assert.assertTrue(Arrays.equals(expectedCode, installedCode));

        // Note that this code does not have a header, then its version == 0
        String asm2 =
                // (5b) Store at address 0x00, the value 0xFF
                "0xFF 0x00 MSTORE "
                        // (5b) And return such value 0xFF, at offset 0x1F fill with nops to make it
                        // 16 bytes in length
                        + "0x01 0x1F RETURN "
                        // 16
                        + "STOP STOP STOP STOP STOP STOP";

        byte[] code2 = assembler.assemble(asm2);

        // The second transaction changes the contract code
        Transaction tx2 = createTx(blockchain, sender, tx1.getContractAddress().getBytes(), code2);
        TransactionExecutor executor2 = executeTransaction(blockchain, tx2);
        byte[] installedCode2 = blockchain.getRepository().getCode(createdContract);
        // assert the contract code has been created
        Assert.assertTrue(Arrays.equals(installedCode2, code2));
        Assert.assertEquals(
                1, executor2.getResult().getCodeChanges().size()); // there is one code change

        // We could add a third tx to execute the new code
        Transaction tx3 =
                createTx(blockchain, sender, tx1.getContractAddress().getBytes(), new byte[0]);
        TransactionExecutor executor3 = executeTransaction(blockchain, tx3);
        // check return code from contract call
        Assert.assertArrayEquals(Hex.decode("FF"), executor3.getResult().getHReturn());
    }

    @Test
    public void replaceCodeTest2() throws InterruptedException {
        // We test code replacement during initialization: this is forbitten.

        BigInteger nonce = config.getNetworkConstants().getInitialNonce();
        BlockChainImpl blockchain =
                org.ethereum.core.ImportLightTest.createBlockchain(
                        GenesisLoader.loadGenesis(
                                nonce,
                                getClass().getResourceAsStream("/genesis/genesis-light.json"),
                                false,
                                true,
                                true),
                        config);

        ECKey sender =
                ECKey.fromPrivate(
                        Hex.decode(
                                "3ec771c31cac8c0dba77a69e503765701d3c2bb62435888d4ffa38fed60c445c"));
        System.out.println("address: " + Hex.toHexString(sender.getAddress()));

        String asm =
                // (4b) header script v1
                "HEADER !0x01 !0x01 !0x00 "
                        // (5b) we attempt to replace the code
                        + "0x01 0x00 CODEREPLACE "
                        // (7b) Extract real code into address 0, skip first 12 bytes, copy 1
                        // bytes
                        + "0x01 0x15 0x00 CODECOPY "
                        // (5b) offset 0, size 0x01, now return the first code
                        + "0x01 0x00  RETURN "
                        // (1b) REAL code to install
                        + "STOP ";

        EVMAssembler assembler = new EVMAssembler();
        byte[] code = assembler.assemble(asm);

        // Creates a contract
        Transaction tx1 = createTx(blockchain, sender, new byte[0], code);
        TransactionExecutor executor1 = executeTransaction(blockchain, tx1);
        // Now we can directly check the store and see the new code.
        Assert.assertTrue(executor1.getResult().getException() != null);
    }

    @Test
    public void replaceCodeTest3() throws InterruptedException {
        TestSystemProperties oldConfig = config;
        config = new TestSystemProperties();
        BigInteger nonce = config.getNetworkConstants().getInitialNonce();
        BlockChainImpl blockchain =
                org.ethereum.core.ImportLightTest.createBlockchain(
                        GenesisLoader.loadGenesis(
                                nonce,
                                getClass().getResourceAsStream("/genesis/genesis-light.json"),
                                false,
                                true,
                                true),
                        config);

        ECKey sender =
                ECKey.fromPrivate(
                        Hex.decode(
                                "3ec771c31cac8c0dba77a69e503765701d3c2bb62435888d4ffa38fed60c445c"));
        System.out.println("address: " + Hex.toHexString(sender.getAddress()));

        String asm =
                // (7b) Extract real code into address 0, skip first 12 bytes, copy 20 bytes
                "0x14 0x0C 0x00 CODECOPY "
                        // (5b) offset 0, size 0x14, now return the first code
                        + "0x14 0x00  RETURN "
                        // (4b) header script v1
                        + "HEADER !0x01 !0x01 !0x00 "
                        // (3b) store at offset 0, read data from offset 0. Transfer 32 bytes
                        + "0x00 CALLDATALOAD "
                        // (3b) store the data at address 0
                        + "0x00 MSTORE "
                        // We replace TWO TIMES to make sure that only the last replacement takes
                        // place
                        // (5b) set new code: offset 0, size 1 bytes.
                        + "0x01 0x00 CODEREPLACE "
                        // This is the good one.
                        // (5b) set new code: offset 0, size 16 bytes.
                        + "0x10 0x00 CODEREPLACE";

        EVMAssembler assembler = new EVMAssembler();
        byte[] code = assembler.assemble(asm);

        // Creates a contract
        Transaction tx1 = createTx(blockchain, sender, new byte[0], code);
        executeTransaction(blockchain, tx1);
        // Now we can directly check the store and see the new code.
        RskAddress createdContract = tx1.getContractAddress();
        byte[] expectedCode = Arrays.copyOfRange(code, 12, 12 + 20);
        byte[] installedCode = blockchain.getRepository().getCode(createdContract);
        // assert the contract has been created
        Assert.assertTrue(Arrays.equals(expectedCode, installedCode));

        String asm2 =
                // (5b) Store at address 0x00, the value 0xFF
                "0xFF 0x00 MSTORE "
                        // (5b) And return such value 0xFF, at offset 0x1F fill with nops to make it
                        // 16 bytes in length
                        + "0x01 0x1F RETURN "
                        // 16
                        + "STOP STOP STOP STOP STOP STOP";

        byte[] code2 = assembler.assemble(asm2);

        Transaction tx2 = createTx(blockchain, sender, tx1.getContractAddress().getBytes(), code2);
        TransactionExecutor executor2 = executeTransaction(blockchain, tx2);
        // code remains the same
        Assert.assertTrue(Arrays.equals(code2, code2));
        // there is no code change
        Assert.assertEquals(0, executor2.getResult().getCodeChanges().size());
        // invalid opcode exception
        Assert.assertNotNull(executor2.getResult().getException());

        config = oldConfig;
    }

    protected Transaction createTx(
            BlockChainImpl blockchain, ECKey sender, byte[] receiveAddress, byte[] data)
            throws InterruptedException {
        return createTx(blockchain, sender, receiveAddress, data, 0);
    }

    protected Transaction createTx(
            BlockChainImpl blockchain, ECKey sender, byte[] receiveAddress, byte[] data, long value)
            throws InterruptedException {
        BigInteger nonce = blockchain.getRepository().getNonce(new RskAddress(sender.getAddress()));
        Transaction tx =
                new Transaction(
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

    public TransactionExecutor executeTransaction(BlockChainImpl blockchain, Transaction tx) {
        Repository track = blockchain.getRepository().startTracking();
        TransactionExecutorFactory transactionExecutorFactory =
                new TransactionExecutorFactory(
                        config,
                        blockchain.getBlockStore(),
                        null,
                        blockFactory,
                        new ProgramInvokeFactoryImpl(),
                        new PrecompiledContracts(
                                config,
                                new RepositoryBtcBlockStoreWithCache.Factory(
                                        config.getNetworkConstants()
                                                .getBridgeConstants()
                                                .getBtcParams())));
        TransactionExecutor executor =
                transactionExecutorFactory.newInstance(
                        tx,
                        0,
                        RskAddress.nullAddress(),
                        blockchain.getRepository(),
                        blockchain.getBestBlock(),
                        0);

        executor.init();
        executor.execute();
        executor.go();
        executor.finalization();

        track.commit();
        return executor;
    }
}
