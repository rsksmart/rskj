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
import org.ethereum.vm.GasCost;
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
     *
     *     function wasteGas(uint gas) external {
     *         uint i = uint(keccak256(abi.encode(0x12349876)));
     *         another = i;
     *         uint gasLeft = gasleft();
     *         while (gasLeft < gas + gasleft()) {
     *             unchecked {
     *                 i = (i / 7 + 10) * 8;
     *             }
     *         }
     *     }
     * }
     */

    private final String creationData = "608060405234801561001057600080fd5b506103f5806100206000396000f3fe6080604052600436106100745760003560e01c806357de26a41161004e57806357de26a4146100e95780637fa4dcd81461010057806382ab890a14610129578063e2033a13146101525761007b565b80630d2a2d8d146100805780631b892f87146100975780632f048afa146100c05761007b565b3661007b57005b600080fd5b34801561008c57600080fd5b5061009561017b565b005b3480156100a357600080fd5b506100be60048036038101906100b99190610290565b610188565b005b3480156100cc57600080fd5b506100e760048036038101906100e29190610290565b6101a4565b005b3480156100f557600080fd5b506100fe6101ae565b005b34801561010c57600080fd5b5061012760048036038101906101229190610290565b6101b9565b005b34801561013557600080fd5b50610150600480360381019061014b9190610290565b61022f565b005b34801561015e57600080fd5b5061017960048036038101906101749190610290565b61024a565b005b6000546001819055600080fd5b8060008082825461019991906102ec565b925050819055600080fd5b8060008190555050565b600054600181905550565b600063123498766040516020016101d09190610375565b6040516020818303038152906040528051906020012060001c90508060018190555060005a90505b5a8361020491906102ec565b81101561022a576008600a600784816102205761021f610390565b5b04010291506101f8565b505050565b8060008082825461024091906102ec565b9250508190555050565b806000819055600080fd5b600080fd5b6000819050919050565b61026d8161025a565b811461027857600080fd5b50565b60008135905061028a81610264565b92915050565b6000602082840312156102a6576102a5610255565b5b60006102b48482850161027b565b91505092915050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052601160045260246000fd5b60006102f78261025a565b91506103028361025a565b925082820190508082111561031a576103196102bd565b5b92915050565b6000819050919050565b600063ffffffff82169050919050565b6000819050919050565b600061035f61035a61035584610320565b61033a565b61032a565b9050919050565b61036f81610344565b82525050565b600060208201905061038a6000830184610366565b92915050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052601260045260246000fdfea26469706673582212205b0597a6850277acf28d04e99d281b91a759c03cdf277adbc86c3d0ba87c44ba64736f6c63430008110033";

    // call data
    private final String writeTen = "2f048afa000000000000000000000000000000000000000000000000000000000000000a";
    private final String readData = "57de26a4";
    private final String updateByTen = "82ab890a000000000000000000000000000000000000000000000000000000000000000a";
    private final String writeTenWithRevert = "0d2a2d8d";
    private final String readDataWithRevert = "e2033a13000000000000000000000000000000000000000000000000000000000000000a";
    private final String updateByTenWithRevert = "1b892f87000000000000000000000000000000000000000000000000000000000000000a";
    private final String wasteTwoMillionGas = "7fa4dcd800000000000000000000000000000000000000000000000000000000001e8480";
    private final String wasteHundredThousandGas = "7fa4dcd800000000000000000000000000000000000000000000000000000000000186a0";

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

    private String skeleton(String txs, boolean validate) {
        return createThreeAccounts +
                createContractInBlock01 +
                txs +
                buildBlockWithToTxs +
                (validate ? validateTxs : "");
    }

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
        this.testProcessingPreAndPostRSKIP144(skeleton(
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
                "\n", validate), edges);
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

    // 2. A is in a parallel sublist without enough gas available: B is placed in the sequential sublist
    @Test
    void useSequentialThread() throws DslProcessorException {
        World parallel = this.createWorld(skeleton("transaction_build tx02\n" +
                "    sender acc1\n" +
                "    contract tx01\n" +
                "    data " + wasteTwoMillionGas + "\n" +
                "    gas 2500000\n" +
                "    build\n" +
                "\n" +
                "transaction_build tx03\n" +
                "    sender acc2\n" +
                "    contract tx01\n" +
                "    data " + wasteTwoMillionGas + "\n" +
                "    gas 2500000\n" +
                "    build\n" +
                "\n", true), 0);

        Assertions.assertEquals(3000000L, GasCost.toGas(parallel.getBlockChain().getBestBlock().getHeader().getGasLimit()));
        Assertions.assertEquals(2, parallel.getBlockChain().getBestBlock().getTransactionsList().size());
        Assertions.assertArrayEquals(new short[] { 1 }, parallel.getBlockChain().getBestBlock().getHeader().getTxExecutionSublistsEdges());
    }

    // 3. A is in the sequential sublist: B is placed in the sequential sublist
    @Test
    void bothUseSequentialThread() throws DslProcessorException {
        World parallel = this.createWorld(createThreeAccounts +
                createContractInBlock01 +
                "transaction_build tx02\n" +
                "    sender acc1\n" +
                "    contract tx01\n" +
                "    data " + wasteTwoMillionGas + "\n" +
                "    gas 2500000\n" +
                "    nonce 0\n" +
                "    build\n" +
                "\n" +
                "transaction_build tx03\n" +
                "    sender acc1\n" +
                "    contract tx01\n" +
                "    data " + wasteTwoMillionGas + "\n" +
                "    gas 2500000\n" +
                "    nonce 1\n" +
                "    build\n" +
                "\n" + // goes to sequential
                "transaction_build tx04\n" +
                "    sender acc2\n" +
                "    contract tx01\n" +
                "    data " + wasteHundredThousandGas + "\n" +
                "    gas 200000\n" +
                "    build\n" +
                "\n" +
                "block_build b02\n" +
                "    parent b01\n" +
                "    transactions tx02 tx03 tx04\n" +
                "    build\n" +
                "\n" +
                "block_connect b02\n" +
                "\n" +
                "assert_best b02\n" +
                "assert_tx_success tx01\n" +
                "assert_tx_success tx02\n" +
                "assert_tx_success tx03\n" +
                "assert_tx_success tx04\n" +
                "\n", 0);

        Assertions.assertEquals(3000000L, GasCost.toGas(parallel.getBlockChain().getBestBlock().getHeader().getGasLimit()));
        Assertions.assertEquals(3, parallel.getBlockChain().getBestBlock().getTransactionsList().size());
        Assertions.assertArrayEquals(new short[] { 1 }, parallel.getBlockChain().getBestBlock().getHeader().getTxExecutionSublistsEdges());
    }
}