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

package co.rsk.core.parallel;

import co.rsk.config.TestSystemProperties;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ParallelExecutionStateTest {
    private World createWorld(String dsl, int rskip144) throws DslProcessorException {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.consensusRules.rskip144", ConfigValueFactory.fromAnyRef(rskip144))
        );

        World world = new World(receiptStore, config);

        DslParser parser = new DslParser(dsl);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        return world;
    }

    private byte[] getStateRoot (World world) {
        return world.getBlockChain().getBestBlock().getHeader().getStateRoot();
    }

    /**
     * compares the state root of a blockchain with and without rskip 144
     * @param dsl the dsl string of the blockchain
     * @param expectedEdges the tx execution edges to assert equals
     * @throws DslProcessorException
     */
    private void testProcessingPreAndPostRSKIP144(String dsl, short[] expectedEdges) throws DslProcessorException {
        World parallel = this.createWorld(dsl, 0);
        World series = this.createWorld(dsl, -1);

        Assertions.assertArrayEquals(
                this.getStateRoot(series),
                this.getStateRoot(parallel)
        );

        Assertions.assertArrayEquals(expectedEdges, parallel.getBlockChain().getBestBlock().getHeader().getTxExecutionSublistsEdges());
    }

    @Test
    void empty() throws DslProcessorException {
        this.testProcessingPreAndPostRSKIP144("block_chain g00", null);
    }

    @Test
    void oneTx() throws DslProcessorException {
        this.testProcessingPreAndPostRSKIP144("account_new acc1 10000000\n" +
                "account_new acc2 0\n" +
                "\n" +
                "transaction_build tx01\n" +
                "    sender acc1\n" +
                "    receiver acc2\n" +
                "    value 1000\n" +
                "    build\n" +
                "\n" +
                "block_build b01\n" +
                "    parent g00\n" +
                "    transactions tx01\n" +
                "    build\n" +
                "\n" +
                "block_connect b01\n" +
                "\n" +
                "assert_best b01\n" +
                "assert_tx_success tx01\n" +
                "assert_balance acc2 1000\n" +
                "\n", new short[]{ 1 });
    }

    /**
     * pragma solidity ^0.8.9;
     *
     * contract ReadWrite {
     *     uint x;
     *     uint another;
     *
     *     receive() external payable {}
     *     function read() external { another = x; }
     *     function write(uint value) external { x = value; }
     *     function update(uint increment) external { x += increment; }
     *
     *     function readWithRevert() external { another = x; revert(); }
     *     function writeWithRevert(uint value) external { x = value; revert(); }
     *     function updateWithRevert(uint increment) external { x += increment; revert(); }
     * }
     */

    private final String creationData = "608060405234801561001057600080fd5b5061029c806100206000396000f3fe6080604052600436106100595760003560e01c80630d2a2d8d146100655780631b892f871461007c5780632f048afa146100a557806357de26a4146100ce57806382ab890a146100e5578063e2033a131461010e57610060565b3661006057005b600080fd5b34801561007157600080fd5b5061007a610137565b005b34801561008857600080fd5b506100a3600480360381019061009e91906101d6565b610144565b005b3480156100b157600080fd5b506100cc60048036038101906100c791906101d6565b610160565b005b3480156100da57600080fd5b506100e361016a565b005b3480156100f157600080fd5b5061010c600480360381019061010791906101d6565b610175565b005b34801561011a57600080fd5b50610135600480360381019061013091906101d6565b610190565b005b6000546001819055600080fd5b806000808282546101559190610232565b925050819055600080fd5b8060008190555050565b600054600181905550565b806000808282546101869190610232565b9250508190555050565b806000819055600080fd5b600080fd5b6000819050919050565b6101b3816101a0565b81146101be57600080fd5b50565b6000813590506101d0816101aa565b92915050565b6000602082840312156101ec576101eb61019b565b5b60006101fa848285016101c1565b91505092915050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052601160045260246000fd5b600061023d826101a0565b9150610248836101a0565b92508282019050808211156102605761025f610203565b5b9291505056fea264697066735822122026312b78fe7401c6254384280f0829175f2da1a68f8ed8f45a7193b0a04158bc64736f6c63430008110033";

    // call data
    private final String writeTen = "2f048afa000000000000000000000000000000000000000000000000000000000000000a";
    private final String readData = "57de26a4";
    private final String updateByTen = "82ab890a000000000000000000000000000000000000000000000000000000000000000a";
    private final String writeTenWithRevert = "0d2a2d8d";
    private final String readDataWithRevert = "e2033a13000000000000000000000000000000000000000000000000000000000000000a";
    private final String updateByTenWithRevert = "1b892f87000000000000000000000000000000000000000000000000000000000000000a";

    // substrings for dsl text
    private final String createThreeAccounts = "account_new acc1 10000000\n" +
            "account_new acc2 10000000\n" +
            "account_new acc3 10000000\n" +
            "\n";

    private final String createContractInBlock01 =
            "transaction_build tx01\n" +
                    "    sender acc3\n" +
                    "    receiverAddress 00\n" +
                    "    data " + creationData + "\n" +
                    "    gas 1200000\n" +
                    "    build\n" +
                    "\n" +
                    "block_build b01\n" +
                    "    parent g00\n" +
                    "    transactions tx01\n" +
                    "    build\n" +
                    "\n" +
                    "block_connect b01\n" +
                    "\n";

    private final String buildBlockWithToTxs = "block_build b02\n" +
            "    parent b01\n" +
            "    transactions tx02 tx03\n" +
            "    build\n" +
            "\n" +
            "block_connect b02\n" +
            "\n" +
            "assert_best b02\n" +
            "\n";

    private final String validateTxs = "assert_tx_success tx01\n" +
            "assert_tx_success tx02\n" +
            "assert_tx_success tx03\n" +
            "\n";

    private void createContractAndTestCallWith(String firstCall, String secondCall, short[] edges) throws DslProcessorException {
        createContractAndTestCallWith(firstCall, secondCall, edges, true);
    }

    /**
     * creates the contract, performs two calls from different accounts
     * tests the state root and the tx edges
     * @param firstCall call data for the first tx
     * @param secondCall call data for the second tx
     * @param edges expected tx edges
     * @param validate allows to prevent validating txs are successful
     * @throws DslProcessorException
     */
    private void createContractAndTestCallWith(String firstCall, String secondCall, short[] edges, boolean validate) throws DslProcessorException {
        this.testProcessingPreAndPostRSKIP144(createThreeAccounts +
                createContractInBlock01 +
                "transaction_build tx02\n" +
                "    sender acc1\n" +
                "    contract tx01\n" +
                "    data " + firstCall + "\n" +
                "    gas 100000\n" +
                "    build\n" +
                "\n" +
                "transaction_build tx03\n" +
                "    sender acc2\n" +
                "    contract tx01\n" +
                "    data " + secondCall + "\n" +
                "    gas 100000\n" +
                "    build\n" +
                "\n" +
                buildBlockWithToTxs +
                (validate ? validateTxs : ""), edges);
    }

    // 1. A and B have the same sender account
    @Test
    void sameSender() throws DslProcessorException {
        this.testProcessingPreAndPostRSKIP144("account_new acc1 10000000\n" +
                "account_new acc2 0\n" +
                "\n" +
                "transaction_build tx01\n" +
                "    sender acc1\n" +
                "    receiver acc2\n" +
                "    value 1000\n" +
                "    nonce 0\n" +
                "    build\n" +
                "\n" +
                "transaction_build tx02\n" +
                "    sender acc1\n" +
                "    receiver acc2\n" +
                "    value 1000\n" +
                "    nonce 1\n" +
                "    build\n" +
                "\n" +
                "block_build b01\n" +
                "    parent g00\n" +
                "    transactions tx01 tx02\n" +
                "    build\n" +
                "\n" +
                "block_connect b01\n" +
                "\n" +
                "assert_best b01\n" +
                "assert_tx_success tx01\n" +
                "assert_tx_success tx02\n" +
                "assert_balance acc2 2000\n" +
                "\n", new short[]{ 2 });
    }

    // 2. A and B transfer value to the same destination account
    @Test
    void sameRecipient() throws DslProcessorException {
        this.testProcessingPreAndPostRSKIP144("account_new acc1 10000000\n" +
                "account_new acc2 10000000\n" +
                "account_new acc3 0\n" +
                "\n" +
                "transaction_build tx01\n" +
                "    sender acc1\n" +
                "    receiver acc3\n" +
                "    value 1000\n" +
                "    build\n" +
                "\n" +
                "transaction_build tx02\n" +
                "    sender acc2\n" +
                "    receiver acc3\n" +
                "    value 1000\n" +
                "    build\n" +
                "\n" +
                "block_build b01\n" +
                "    parent g00\n" +
                "    transactions tx01 tx02\n" +
                "    build\n" +
                "\n" +
                "block_connect b01\n" +
                "\n" +
                "assert_best b01\n" +
                "assert_tx_success tx01\n" +
                "assert_tx_success tx02\n" +
                "assert_balance acc3 2000\n" +
                "\n", new short[]{ 2 });
    }

    // 3. A and B transfer value to the same smart contract
    @Test
    void sameContractRecipient() throws DslProcessorException {
        this.testProcessingPreAndPostRSKIP144(createThreeAccounts +
                createContractInBlock01 +
                "transaction_build tx02\n" +
                "    sender acc1\n" +
                "    contract tx01\n" +
                "    value 1000\n" +
                "    gas 100000\n" +
                "    build\n" +
                "\n" +
                "transaction_build tx03\n" +
                "    sender acc2\n" +
                "    contract tx01\n" +
                "    value 1000\n" +
                "    gas 100000\n" +
                "    build\n" +
                "\n"
                + buildBlockWithToTxs + validateTxs, new short[]{ 2 });
    }

    // 4. B reads a smart contract variable that A writes
    @Test
    void readWrite() throws DslProcessorException {
        this.createContractAndTestCallWith(writeTen, readData, new short[]{ 2 });
    }

    @Test
    void readWriteWithRevert() throws DslProcessorException {
        this.createContractAndTestCallWith(writeTen, readDataWithRevert, new short[]{ 2 }, false);
    }

    // 5. B reads a smart contract variable that A updates (i.e., +=)
    @Test
    void readUpdate() throws DslProcessorException {
        this.createContractAndTestCallWith(updateByTen, readData, new short[]{ 2 });
    }

    @Test
    void readUpdateWithRevert() throws DslProcessorException {
        this.createContractAndTestCallWith(updateByTen, readDataWithRevert, new short[]{ 2 }, false);
    }


    //6. B writes a smart contract variable that A writes
    @Test
    void writeWrite() throws DslProcessorException {
        this.createContractAndTestCallWith(writeTen, writeTen, new short[]{ 2 });
    }

    @Test
    void writeWriteWithRevert() throws DslProcessorException {
        this.createContractAndTestCallWith(writeTen, writeTenWithRevert, new short[]{ 2 }, false);
    }

    // 7. B writes a smart contract variable that A updates
    @Test
    void writeUpdate() throws DslProcessorException {
        this.createContractAndTestCallWith(updateByTen, writeTen, new short[]{ 2 });
    }

    @Test
    void writeUpdateWithRevert() throws DslProcessorException {
        this.createContractAndTestCallWith(updateByTen, writeTenWithRevert, new short[]{ 2 }, false);
    }

    // 8. B writes a smart contract variable that A reads
    @Test
    void writeRead() throws DslProcessorException {
        this.createContractAndTestCallWith(readData, writeTen, new short[]{ 2 });
    }

    @Test
    void writeReadWithRevert() throws DslProcessorException {
        this.createContractAndTestCallWith(readData, writeTenWithRevert, new short[]{ 2 }, false);
    }

    // 9. B updates a smart contract variable that A writes
    @Test
    void updateWrite() throws DslProcessorException {
        this.createContractAndTestCallWith(writeTen, updateByTen, new short[]{ 2 });
    }

    @Test
    void updateWriteWithRevert() throws DslProcessorException {
        this.createContractAndTestCallWith(writeTen, updateByTenWithRevert, new short[]{ 2 }, false);
    }

    // 10. B updates a smart contract variable that A reads
    @Test
    void updateRead() throws DslProcessorException {
        this.createContractAndTestCallWith(readData, updateByTen, new short[]{ 2 });
    }

    // 11. B updates a smart contract variable that A updates
    @Test
    void updateUpdate() throws DslProcessorException {
        this.createContractAndTestCallWith(updateByTen, updateByTen, new short[]{ 2 });
    }

    // 12. B calls a smart contract that A creates
    @Test
    void callCreatedContract() throws DslProcessorException {
        this.testProcessingPreAndPostRSKIP144("account_new acc1 10000000\n" +
                "account_new acc2 10000000\n" +
                "\n" +
                "transaction_build tx01\n" +
                "    sender acc1\n" +
                "    receiverAddress 00\n" +
                "    data " + creationData + "\n" +
                "    gas 1200000\n" +
                "    build\n" +
                "\n" +
                "transaction_build tx02\n" +
                "    sender acc2\n" +
                "    contract tx01\n" +
                "    data " + writeTen + "\n" +
                "    gas 100000\n" +
                "    build\n" +
                "\n" +
                "block_build b01\n" +
                "    parent g00\n" +
                "    transactions tx01 tx02\n" +
                "    build\n" +
                "\n" +
                "block_connect b01\n" +
                "assert_tx_success tx01\n" +
                "assert_tx_success tx02\n" +
                "\n", new short[]{ 2 });
    }

    // 13. B transfers value to a smart contract that A creates
    @Test
    void sendToCreatedContract() throws DslProcessorException {
        this.testProcessingPreAndPostRSKIP144("account_new acc1 10000000\n" +
                "account_new acc2 10000000\n" +
                "\n" +
                "transaction_build tx01\n" +
                "    sender acc1\n" +
                "    receiverAddress 00\n" +
                "    data " + creationData + "\n" +
                "    gas 1200000\n" +
                "    build\n" +
                "\n" +
                "transaction_build tx02\n" +
                "    sender acc2\n" +
                "    contract tx01\n" +
                "    value 10000\n" +
                "    gas 100000\n" +
                "    build\n" +
                "\n" +
                "block_build b01\n" +
                "    parent g00\n" +
                "    transactions tx01 tx02\n" +
                "    build\n" +
                "\n" +
                "block_connect b01\n" +
                "assert_tx_success tx01\n" +
                "assert_tx_success tx02\n" +
                "assert_balance tx01 10000\n" +
                "\n", new short[]{ 2 });
    }
}