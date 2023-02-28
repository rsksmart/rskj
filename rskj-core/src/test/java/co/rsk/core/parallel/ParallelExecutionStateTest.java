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
import co.rsk.core.RskAddress;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.test.dsl.WorldDslProcessor;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.ethereum.core.Transaction;
import org.ethereum.vm.GasCost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class ParallelExecutionStateTest {
    private World createWorld(int rskip144) {
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        TestSystemProperties config = new TestSystemProperties(rawConfig ->
                rawConfig.withValue("blockchain.config.consensusRules.rskip144", ConfigValueFactory.fromAnyRef(rskip144))
        );

        World world = new World(receiptStore, config);
        return world;
    }

    private World createWorldAndProcess(String dsl, int rskip144) throws DslProcessorException {
        World world = createWorld(rskip144);

        DslParser parser = new DslParser(dsl);
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        return world;
    }

    /**
     * compares the state root of a blockchain with and without rskip 144
     * @param dsl the dsl string of the blockchain
     * @param expectedEdges the tx execution edges to assert equals
     * @throws DslProcessorException
     */
    private void testProcessingPreAndPostRSKIP144(String dsl, short[] expectedEdges) throws DslProcessorException {
        World parallel = this.createWorldAndProcess(dsl, 0);
        World series = this.createWorldAndProcess(dsl, -1);

        compareTwoWorldsAndTestEdges(series, parallel, expectedEdges);
    }

    private void compareTwoWorldsAndTestEdges(World series, World parallel, short[] expectedEdges) {
        Assertions.assertArrayEquals(
                this.getStateRoot(series),
                this.getStateRoot(parallel)
        );

        Assertions.assertArrayEquals(expectedEdges, parallel.getBlockChain().getBestBlock().getHeader().getTxExecutionSublistsEdges());
    }

    private byte[] getStateRoot (World world) {
        return world.getBlockChain().getBestBlock().getHeader().getStateRoot();
    }

    /**
     * // SPDX-License-Identifier: UNLICENSED
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
     *     mapping (uint => uint) r;
     *
     *     function wasteGas(uint gas, uint writeToX, uint writeToY) external {
     *         uint i = uint(keccak256(abi.encode(0x12349876)));
     *         r[writeToX] = i;
     *         r[writeToY] = i;
     *         uint gasLeft = gasleft();
     *         while (gasLeft < gas + gasleft()) {
     *             unchecked {
     *                 i = (i / 7 + 10) * 8;
     *             }
     *         }
     *     }
     * }
     *
     * contract Proxy {
     *     ReadWrite readWrite;
     *     constructor (ReadWrite _readWrite) { readWrite = _readWrite; }
     *     function read() external { readWrite.read(); }
     *     function write(uint value) external { readWrite.write(value); }
     *     function update(uint increment) external { readWrite.update(increment); }
     * }
     */

    // creation codes

    private final String creationData = "608060405234801561001057600080fd5b50610473806100206000396000f3fe6080604052600436106100745760003560e01c806334b091931161004e57806334b09193146100e957806357de26a41461011257806382ab890a14610129578063e2033a13146101525761007b565b80630d2a2d8d146100805780631b892f87146100975780632f048afa146100c05761007b565b3661007b57005b600080fd5b34801561008c57600080fd5b5061009561017b565b005b3480156100a357600080fd5b506100be60048036038101906100b991906102bb565b610188565b005b3480156100cc57600080fd5b506100e760048036038101906100e291906102bb565b6101a4565b005b3480156100f557600080fd5b50610110600480360381019061010b91906102e8565b6101ae565b005b34801561011e57600080fd5b5061012761024f565b005b34801561013557600080fd5b50610150600480360381019061014b91906102bb565b61025a565b005b34801561015e57600080fd5b50610179600480360381019061017491906102bb565b610275565b005b6000546001819055600080fd5b80600080828254610199919061036a565b925050819055600080fd5b8060008190555050565b600063123498766040516020016101c591906103f3565b6040516020818303038152906040528051906020012060001c905080600260008581526020019081526020016000208190555080600260008481526020019081526020016000208190555060005a90505b5a85610222919061036a565b811015610248576008600a6007848161023e5761023d61040e565b5b0401029150610216565b5050505050565b600054600181905550565b8060008082825461026b919061036a565b9250508190555050565b806000819055600080fd5b600080fd5b6000819050919050565b61029881610285565b81146102a357600080fd5b50565b6000813590506102b58161028f565b92915050565b6000602082840312156102d1576102d0610280565b5b60006102df848285016102a6565b91505092915050565b60008060006060848603121561030157610300610280565b5b600061030f868287016102a6565b9350506020610320868287016102a6565b9250506040610331868287016102a6565b9150509250925092565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052601160045260246000fd5b600061037582610285565b915061038083610285565b92508282019050808211156103985761039761033b565b5b92915050565b6000819050919050565b600063ffffffff82169050919050565b6000819050919050565b60006103dd6103d86103d38461039e565b6103b8565b6103a8565b9050919050565b6103ed816103c2565b82525050565b600060208201905061040860008301846103e4565b92915050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052601260045260246000fdfea264697066735822122081578079990ee4f4eaa55ebeeedcb31b8f178ab346b989e62541a894e60d381164736f6c63430008110033";
    private String getProxyCreationCode (String address) {
        return "608060405234801561001057600080fd5b50604051610417380380610417833981810160405281019061003291906100ed565b806000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055505061011a565b600080fd5b600073ffffffffffffffffffffffffffffffffffffffff82169050919050565b60006100a88261007d565b9050919050565b60006100ba8261009d565b9050919050565b6100ca816100af565b81146100d557600080fd5b50565b6000815190506100e7816100c1565b92915050565b60006020828403121561010357610102610078565b5b6000610111848285016100d8565b91505092915050565b6102ee806101296000396000f3fe608060405234801561001057600080fd5b50600436106100415760003560e01c80632f048afa1461004657806357de26a41461006257806382ab890a1461006c575b600080fd5b610060600480360381019061005b9190610261565b610088565b005b61006a610116565b005b61008660048036038101906100819190610261565b610198565b005b60008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16632f048afa826040518263ffffffff1660e01b81526004016100e1919061029d565b600060405180830381600087803b1580156100fb57600080fd5b505af115801561010f573d6000803e3d6000fd5b5050505050565b60008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff166357de26a46040518163ffffffff1660e01b8152600401600060405180830381600087803b15801561017e57600080fd5b505af1158015610192573d6000803e3d6000fd5b50505050565b60008054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff166382ab890a826040518263ffffffff1660e01b81526004016101f1919061029d565b600060405180830381600087803b15801561020b57600080fd5b505af115801561021f573d6000803e3d6000fd5b5050505050565b600080fd5b6000819050919050565b61023e8161022b565b811461024957600080fd5b50565b60008135905061025b81610235565b92915050565b60006020828403121561027757610276610226565b5b60006102858482850161024c565b91505092915050565b6102978161022b565b82525050565b60006020820190506102b2600083018461028e565b9291505056fea264697066735822122034c249e40cf35f03e8970cf8c91dbaf8426d01814edfcb782e7548b32f6d9e7964736f6c63430008110033000000000000000000000000"
                + address;
    }

    // call data

    // write(10)
    private final String writeTen = "2f048afa000000000000000000000000000000000000000000000000000000000000000a";
    // read()
    private final String readData = "57de26a4";
    // update(10)
    private final String updateByTen = "82ab890a000000000000000000000000000000000000000000000000000000000000000a";
    // writeWithRevert(10)
    private final String writeTenWithRevert = "0d2a2d8d";
    // readWithRevert()
    private final String readDataWithRevert = "e2033a13000000000000000000000000000000000000000000000000000000000000000a";
    // updateWithRevert(10)
    private final String updateByTenWithRevert = "1b892f87000000000000000000000000000000000000000000000000000000000000000a";
    // wasteGas(2000000, 0, 0)
    private final String wasteTwoMillionGas = "34b0919300000000000000000000000000000000000000000000000000000000001e848000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    // wasteGas(100000, 0, 0)
    private final String wasteHundredThousandGas = "34b0919300000000000000000000000000000000000000000000000000000000000186a000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    // wasteGas(100000, 1, 1)
    private final String writeToX = "34b0919300000000000000000000000000000000000000000000000000000000000186a000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000001";
    // wasteGas(100000, 2, 2)
    private final String writeToY = "34b0919300000000000000000000000000000000000000000000000000000000000186a000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000002";
    // wasteGas(100000, 1, 2)
    private final String writeToXAndY = "34b0919300000000000000000000000000000000000000000000000000000000000186a000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000002";
    // wasteGas(2000000, 1, 1)
    private final String writeToXWastingGas = "34b0919300000000000000000000000000000000000000000000000000000000001e848000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000001";
    // wasteGas(2000000, 2, 2)
    private final String writeToYWastingGas = "34b0919300000000000000000000000000000000000000000000000000000000001e848000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000002";
    // wasteGas(2000000, 1, 2)
    private final String writeToXAndYWastingGas = "34b0919300000000000000000000000000000000000000000000000000000000001e848000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000002";

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

    private void createContractAndTestCall(String firstCall, String secondCall, short[] edges) throws DslProcessorException {
        createContractAndTestCallWith(firstCall, secondCall, edges, true);
    }

    private void createContractAndTestCallWithRevert(String firstCall, String secondCall, short[] edges) throws DslProcessorException {
        createContractAndTestCallWith(firstCall, secondCall, edges, false);
    }

    // For tests calling with proxy contract
    private World processCallWithContract(String firstCall, String secondCall, int rskip144) throws DslProcessorException {
        World world = createWorld(rskip144);

        DslParser parser = new DslParser(createThreeAccounts +
                createContractInBlock01 +
                "assert_tx_success tx01\n");
        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        String readWriteAddress = world.getTransactionByName("tx01").getContractAddress().toHexString();

        String createProxy = "transaction_build tx02\n" +
                "    sender acc3\n" +
                "    receiverAddress 00\n" +
                "    data " + getProxyCreationCode(readWriteAddress) + "\n" +
                "    gas 1200000\n" +
                "    nonce 1\n" +
                "    build\n" +
                "\n" +
                "block_build b02\n" +
                "    parent b01\n" +
                "    transactions tx02\n" +
                "    build\n" +
                "\n" +
                "block_connect b02\n" +
                "assert_tx_success tx02\n" +
                "\n";

        String sendTwoTxs = "transaction_build tx03\n" +
                "    sender acc1\n" +
                "    contract tx02\n" +
                "    data " + firstCall + "\n" +
                "    gas 100000\n" +
                "    build\n" +
                "\n" +
                "transaction_build tx04\n" +
                "    sender acc2\n" +
                "    contract tx02\n" +
                "    data " + secondCall + "\n" +
                "    gas 100000\n" +
                "    build\n" +
                "\n" +
                "block_build b03\n" +
                "    parent b02\n" +
                "    transactions tx03 tx04\n" +
                "    build\n" +
                "\n" +
                "block_connect b03\n" +
                "assert_tx_success tx03 tx04\n";

        parser = new DslParser(createProxy + sendTwoTxs);
        processor.processCommands(parser);

        RskAddress proxyAddress = world.getTransactionByName("tx02").getContractAddress();
        Assertions.assertNotNull(world.getRepository().getCode(proxyAddress));

        List<Transaction> txList = world.getBlockChain().getBestBlock().getTransactionsList();
        Assertions.assertTrue(proxyAddress.equals(txList.get(0).getReceiveAddress()));
        Assertions.assertTrue(proxyAddress.equals(txList.get(0).getReceiveAddress()));

        return world;
    }

    private void createContractAndTestCallWithContract(String firstCall, String secondCall, short[] expectedEdges) throws DslProcessorException {
        World parallel = processCallWithContract(firstCall, secondCall, 0);
        World series = processCallWithContract(firstCall, secondCall, -1);

        compareTwoWorldsAndTestEdges(series, parallel, expectedEdges);
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
        this.createContractAndTestCall(writeTen, readData, new short[]{ 2 });
    }

    @Test
    void readWriteWithRevert() throws DslProcessorException {
        this.createContractAndTestCallWithRevert(writeTen, readDataWithRevert, new short[]{ 2 });
    }

    @Test
    void readWriteWithContract() throws DslProcessorException {
        this.createContractAndTestCallWithContract(writeTen, readData, new short[]{ 2 });
    }

    // 5. B reads a smart contract variable that A updates (i.e., +=)
    @Test
    void readUpdate() throws DslProcessorException {
        this.createContractAndTestCall(updateByTen, readData, new short[]{ 2 });
    }

    @Test
    void readUpdateWithRevert() throws DslProcessorException {
        this.createContractAndTestCallWithRevert(updateByTen, readDataWithRevert, new short[]{ 2 });
    }

    @Test
    void readUpdateWithContract() throws DslProcessorException {
        this.createContractAndTestCallWithContract(updateByTen, readData, new short[]{ 2 });
    }

    //6. B writes a smart contract variable that A writes
    @Test
    void writeWrite() throws DslProcessorException {
        this.createContractAndTestCall(writeTen, writeTen, new short[]{ 2 });
    }

    @Test
    void writeWriteWithRevert() throws DslProcessorException {
        this.createContractAndTestCallWithRevert(writeTen, writeTenWithRevert, new short[]{ 2 });
    }

    @Test
    void writeWriteWithContract() throws DslProcessorException {
        this.createContractAndTestCallWithContract(writeTen, writeTen, new short[]{ 2 });
    }

    // 7. B writes a smart contract variable that A updates
    @Test
    void writeUpdate() throws DslProcessorException {
        this.createContractAndTestCall(updateByTen, writeTen, new short[]{ 2 });
    }

    @Test
    void writeUpdateWithRevert() throws DslProcessorException {
        this.createContractAndTestCallWithRevert(updateByTen, writeTenWithRevert, new short[]{ 2 });
    }

    @Test
    void writeUpdateWithContract() throws DslProcessorException {
        this.createContractAndTestCallWithContract(updateByTen, writeTen, new short[]{ 2 });
    }

    // 8. B writes a smart contract variable that A reads
    @Test
    void writeRead() throws DslProcessorException {
        this.createContractAndTestCall(readData, writeTen, new short[]{ 2 });
    }

    @Test
    void writeReadWithRevert() throws DslProcessorException {
        this.createContractAndTestCallWithRevert(readData, writeTenWithRevert, new short[]{ 2 });
    }

    @Test
    void writeReadWithContract() throws DslProcessorException {
        this.createContractAndTestCallWithContract(readData, writeTen, new short[]{ 2 });
    }

    // 9. B updates a smart contract variable that A writes
    @Test
    void updateWrite() throws DslProcessorException {
        this.createContractAndTestCall(writeTen, updateByTen, new short[]{ 2 });
    }

    @Test
    void updateWriteWithRevert() throws DslProcessorException {
        this.createContractAndTestCallWithRevert(writeTen, updateByTenWithRevert, new short[]{ 2 });
    }

    @Test
    void updateWriteWithContract() throws DslProcessorException {
        this.createContractAndTestCallWithContract(writeTen, updateByTen, new short[]{ 2 });
    }

    // 10. B updates a smart contract variable that A reads
    @Test
    void updateRead() throws DslProcessorException {
        this.createContractAndTestCall(readData, updateByTen, new short[]{ 2 });
    }

    // 11. B updates a smart contract variable that A updates
    @Test
    void updateUpdate() throws DslProcessorException {
        this.createContractAndTestCall(updateByTen, updateByTen, new short[]{ 2 });
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
    void useSequentialForGas() throws DslProcessorException {
        World parallel = this.createWorldAndProcess(skeleton("transaction_build tx02\n" +
                "    sender acc1\n" +
                "    contract tx01\n" +
                "    data " + wasteTwoMillionGas + "\n" +
                "    gas 2700000\n" +
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
    void useSequentialForCollisionWithSequential() throws DslProcessorException {
        World parallel = this.createWorldAndProcess(createThreeAccounts +
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

    // 1. A and B are in different parallel sublists with enough gas: C is placed in the sequential sublist
    @Test
    void useSequentialForCollisionWithTwoParallel() throws DslProcessorException {
        World parallel = this.createWorldAndProcess(createThreeAccounts +
                "account_new acc4 10000000\n" +
                createContractInBlock01 +
                "transaction_build tx02\n" +
                "    sender acc1\n" +
                "    contract tx01\n" +
                "    data " + writeToX + "\n" +
                "    gas 200000\n" +
                "    build\n" +
                "\n" +
                "transaction_build tx03\n" +
                "    sender acc2\n" +
                "    contract tx01\n" +
                "    data " + writeToY + "\n" +
                "    gas 200000\n" +
                "    build\n" +
                "\n" +
                "transaction_build tx04\n" +
                "    sender acc4\n" +
                "    contract tx01\n" +
                "    data " + writeToXAndY + "\n" +
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
        Assertions.assertArrayEquals(new short[] { 1, 2 }, parallel.getBlockChain().getBestBlock().getHeader().getTxExecutionSublistsEdges());
    }

    // 2. A and B are in different parallel sublists without enough gas: C is placed in the sequential sublist
    @Test
    void useSequentialForCollisionWithTwoParallelWithoutGas() throws DslProcessorException {
        World parallel = this.createWorldAndProcess(createThreeAccounts +
                "account_new acc4 10000000\n" +
                createContractInBlock01 +
                "transaction_build tx02\n" +
                "    sender acc1\n" +
                "    contract tx01\n" +
                "    data " + writeToXWastingGas + "\n" +
                "    gas 2500000\n" +
                "    build\n" +
                "\n" +
                "transaction_build tx03\n" +
                "    sender acc2\n" +
                "    contract tx01\n" +
                "    data " + writeToYWastingGas + "\n" +
                "    gas 2500000\n" +
                "    build\n" +
                "\n" + // goes to sequential
                "transaction_build tx04\n" +
                "    sender acc4\n" +
                "    contract tx01\n" +
                "    data " + writeToXAndYWastingGas + "\n" +
                "    gas 2500000\n" +
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
        Assertions.assertArrayEquals(new short[] { 1, 2 }, parallel.getBlockChain().getBestBlock().getHeader().getTxExecutionSublistsEdges());
    }

    // 3. A is in a parallel sublist and B is in the sequential sublist: C is placed in the sequential sublist
    @Test
    void useSequentialForCollisionWithSequentialAndParallel() throws DslProcessorException {
        World parallel = this.createWorldAndProcess(createThreeAccounts +
                "account_new acc4 10000000\n" +
                createContractInBlock01 +
                "transaction_build tx02\n" +
                "    sender acc1\n" +
                "    contract tx01\n" +
                "    data " + writeToXWastingGas + "\n" +
                "    gas 2500000\n" +
                "    build\n" +
                "\n" +
                "transaction_build tx03\n" +
                "    sender acc1\n" +
                "    contract tx01\n" +
                "    data " + writeToXWastingGas + "\n" +
                "    gas 2500000\n" +
                "    nonce 1\n" +
                "    build\n" +
                "\n" + // goes to sequential
                "transaction_build tx04\n" +
                "    sender acc2\n" +
                "    contract tx01\n" +
                "    data " + writeToY + "\n" +
                "    gas 200000\n" +
                "    build\n" +
                "\n" +
                "transaction_build tx05\n" +
                "    sender acc4\n" +
                "    contract tx01\n" +
                "    data " + writeToXAndY + "\n" +
                "    gas 200000\n" +
                "    build\n" +
                "\n" +
                "block_build b02\n" +
                "    parent b01\n" +
                "    transactions tx02 tx03 tx04 tx05\n" +
                "    build\n" +
                "\n" +
                "block_connect b02\n" +
                "\n" +
                "assert_best b02\n" +
                "assert_tx_success tx01\n" +
                "assert_tx_success tx02\n" +
                "assert_tx_success tx03\n" +
                "assert_tx_success tx04\n" +
                "assert_tx_success tx05\n" +
                "\n", 0);

        Assertions.assertEquals(3000000L, GasCost.toGas(parallel.getBlockChain().getBestBlock().getHeader().getGasLimit()));
        Assertions.assertEquals(4, parallel.getBlockChain().getBestBlock().getTransactionsList().size());
        Assertions.assertArrayEquals(new short[] { 1, 2 }, parallel.getBlockChain().getBestBlock().getHeader().getTxExecutionSublistsEdges());
    }
}