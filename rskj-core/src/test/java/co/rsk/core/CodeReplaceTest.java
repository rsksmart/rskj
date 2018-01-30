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
import co.rsk.config.RskSystemProperties;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.commons.RskAddress;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Created by Sergio on 26/02/2017.
 */
public class CodeReplaceTest {

    private final RskSystemProperties config = new RskSystemProperties();

    @Test
    public void replaceCodeTest1() throws IOException, InterruptedException {

        BigInteger nonce = config.getBlockchainConfig().getCommonConstants().getInitialNonce();
        BlockChainImpl blockchain = org.ethereum.core.ImportLightTest.createBlockchain(GenesisLoader.loadGenesis(config, nonce,
                getClass().getResourceAsStream("/genesis/genesis-light.json"), false));

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
        Transaction tx1 = createTx(blockchain, sender, new byte[0], code);
        executeTransaction(blockchain, tx1);
        // Now we can directly check the store and see the new code.
        RskAddress createdContract = tx1.getContractAddress();
        byte[] expectedCode  = Arrays.copyOfRange(code, 12, 12+20);
        byte[] installedCode = blockchain.getRepository().getContractDetails(createdContract).getCode();
        // assert the contract has been created
        Assert.assertTrue(Arrays.equals(expectedCode, installedCode));

        // Note that this code does not have a header, then its version == 0
        String asm2 =
                    "0xFF 0x00 MSTORE "+ // (5b) Store at address 0x00, the value 0xFF
                    "0x01 0x1F RETURN " + // (5b) And return such value 0xFF, at offset 0x1F
                    // fill with nops to make it 16 bytes in length
                    "STOP STOP STOP STOP STOP STOP"; // 16

        byte[] code2 = assembler.assemble(asm2);

        // The second transaction changes the contract code
        Transaction tx2 = createTx(blockchain, sender, tx1.getContractAddress().getBytes(), code2);
        TransactionExecutor executor2 = executeTransaction(blockchain, tx2);
        byte[] installedCode2 = blockchain.getRepository().getContractDetails(createdContract).getCode();
        // assert the contract code has been created
        Assert.assertTrue(Arrays.equals(installedCode2, code2));
        Assert.assertEquals(1, executor2.getResult().getCodeChanges().size()); // there is one code change

        // We could add a third tx to execute the new code
        Transaction tx3 = createTx(blockchain, sender, tx1.getContractAddress().getBytes(), new byte[0]);
        TransactionExecutor executor3 = executeTransaction(blockchain, tx3);
        // check return code from contract call
        Assert.assertArrayEquals(Hex.decode("FF"), executor3.getResult().getHReturn());
    }

    @Test
    public void replaceCodeTest2() throws IOException, InterruptedException {
        // We test code replacement during initialization: this is forbitten.

        BigInteger nonce = config.getBlockchainConfig().getCommonConstants().getInitialNonce();
        BlockChainImpl blockchain = org.ethereum.core.ImportLightTest.createBlockchain(GenesisLoader.loadGenesis(config, nonce,
                getClass().getResourceAsStream("/genesis/genesis-light.json"), false));

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
        Transaction tx1 = createTx(blockchain, sender, new byte[0], code);
        TransactionExecutor executor1 = executeTransaction(blockchain, tx1);
        // Now we can directly check the store and see the new code.
        Assert.assertTrue(executor1.getResult().getException() != null);
    }

    protected Transaction createTx(BlockChainImpl blockchain, ECKey sender, byte[] receiveAddress, byte[] data) throws InterruptedException {
        return createTx(blockchain, sender, receiveAddress, data, 0);
    }

    protected Transaction createTx(BlockChainImpl blockchain, ECKey sender, byte[] receiveAddress,
                                   byte[] data, long value) throws InterruptedException {
        BigInteger nonce = blockchain.getRepository().getNonce(new RskAddress(sender.getAddress()));
        Transaction tx = new Transaction(
                ByteUtil.bigIntegerToBytes(nonce),
                ByteUtil.longToBytesNoLeadZeroes(1),
                ByteUtil.longToBytesNoLeadZeroes(3_000_000),
                receiveAddress,
                ByteUtil.longToBytesNoLeadZeroes(value),
                data,
                config.getBlockchainConfig().getCommonConstants().getChainId());
        tx.sign(sender.getPrivKeyBytes());
        return tx;
    }

    public TransactionExecutor executeTransaction(BlockChainImpl blockchain, Transaction tx) {
        Repository track = blockchain.getRepository().startTracking();
        TransactionExecutor executor = new TransactionExecutor(config, tx, 0, RskAddress.nullAddress(), blockchain.getRepository(),
                blockchain.getBlockStore(), blockchain.getReceiptStore(), new ProgramInvokeFactoryImpl(), blockchain.getBestBlock());

        executor.init();
        executor.execute();
        executor.go();
        executor.finalization();

        track.commit();
        return executor;
    }
}
