/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk.pcc.blockheader;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.blockchain.utils.BlockMiner;
import co.rsk.config.RskMiningConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.mine.MinerUtils;
import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.NativeContract;
import co.rsk.pcc.NativeMethod;
import co.rsk.test.World;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.util.DifficultyUtils;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.Constants;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlockHeaderContractTest {
    private ExecutionEnvironment executionEnvironment;

    private static final BigInteger AMOUNT = new BigInteger("1000000000000000000");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("3000000");
    private static final BigInteger GAS_USED = new BigInteger("0");
    private static final BigInteger MIN_GAS_PRICE = new BigInteger("500000000000000000");
    private static final BigInteger RSK_DIFFICULTY = new BigInteger("1");
    private static final long DIFFICULTY_TARGET = 562036735;
    private static final String DATA = "80af2871";
    private static final String BLOCK_HEADER_CONTRACT_ADDRESS = "0000000000000000000000000000000000000000000000000000000001000010";
    private static final byte[] ADDITIONAL_TAG = {'A','L','T','B','L','O','C','K',':'};

    private TestSystemProperties config;
    private BlockFactory blockFactory;
    private World world;
    private Transaction rskTx;
    private BlockHeaderContract contract;
    private CallTransaction.Function getCoinbaseFunction;
    private CallTransaction.Function getMinGasPriceFunction;
    private CallTransaction.Function getBlockHashFunction;
    private CallTransaction.Function getMergedMiningTagsFunction;
    private CallTransaction.Function getGasLimitFunction;
    private CallTransaction.Function getGasUsedFunction;
    private CallTransaction.Function getDifficultyFunction;
    private CallTransaction.Function getBitcoinHeaderFunction;
    private CallTransaction.Function getUncleCoinbaseAddressFunction;

    @Before
    public void setUp() {
        config = new TestSystemProperties();
        blockFactory = new BlockFactory(config.getActivationConfig());
        PrecompiledContracts precompiledContracts = new PrecompiledContracts(config);

        BlockchainConfig bcConfig = mock(BlockchainConfig.class);

        // Enabling necessary RSKIPs for every precompiled contract to be available
        when(bcConfig.isRskip119()).thenReturn(true);

        world = new World();
        contract = (BlockHeaderContract) precompiledContracts.getContractForAddress(bcConfig, DataWord.valueFromHex(BLOCK_HEADER_CONTRACT_ADDRESS));

        // contract methods
        getCoinbaseFunction = getContractFunction(contract, GetCoinbaseAddress.class);
        getMinGasPriceFunction = getContractFunction(contract, GetMinimumGasPrice.class);
        getBlockHashFunction = getContractFunction(contract, GetBlockHash.class);
        getMergedMiningTagsFunction = getContractFunction(contract, GetMergedMiningTags.class);
        getGasLimitFunction = getContractFunction(contract, GetGasLimit.class);
        getGasUsedFunction = getContractFunction(contract, GetGasUsed.class);
        getDifficultyFunction = getContractFunction(contract, GetDifficulty.class);
        getBitcoinHeaderFunction = getContractFunction(contract, GetBitcoinHeader.class);
        getUncleCoinbaseAddressFunction = getContractFunction(contract, GetUncleCoinbaseAddress.class);

        // invoke transaction
        rskTx = new Transaction(PrecompiledContracts.BLOCK_HEADER_ADDR_STR, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA, Constants.REGTEST_CHAIN_ID);
        rskTx.sign(new ECKey().getPrivKeyBytes());

        executionEnvironment = mock(ExecutionEnvironment.class);
        Whitebox.setInternalState(contract, "executionEnvironment", executionEnvironment);
    }

    @Test
    public void getCoinbase() {
        buildBlockchainOfLength(2);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        byte[] encodedResult = contract.execute(getCoinbaseFunction.encode(new BigInteger("0")));
        Object[] decodedResult = getCoinbaseFunction.decodeResult(encodedResult);

        assertEquals(1, decodedResult.length);

        byte[] coinbase = (byte[]) decodedResult[0];
        byte[] expected = "33333333333333333333".getBytes();

        assertArrayEquals(expected, coinbase);
    }

    @Test
    public void getMinimumGasPrice() {
        buildBlockchainOfLength(2);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        byte[] encodedResult = contract.execute(getMinGasPriceFunction.encode(new BigInteger("0")));
        Object[] decodedResult = getMinGasPriceFunction.decodeResult(encodedResult);

        assertEquals(1, decodedResult.length);

        byte[] minGasPriceBytes = (byte[]) decodedResult[0];

        assertTrue(minGasPriceBytes.length > 0);
        assertEquals(MIN_GAS_PRICE, new BigInteger(minGasPriceBytes));
    }

    @Test
    public void getBlockHash() {
        buildBlockchainOfLength(2);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());
        byte[] expectedHash = world.getBlockChain().getBestBlock().getParentHash().getBytes();

        byte[] encodedResult = contract.execute(getBlockHashFunction.encode(new BigInteger("0")));
        Object[] decodedResult = getBlockHashFunction.decodeResult(encodedResult);

        assertEquals(1, decodedResult.length);

        byte[] blockHash = (byte[]) decodedResult[0];

        assertEquals(Hex.toHexString(expectedHash), Hex.toHexString(blockHash));
    }

    @Test
    public void getMergedMiningTags() {
        buildBlockchainOfLength(2);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        byte[] encodedResult = contract.execute(getMergedMiningTagsFunction.encode(new BigInteger("0")));
        Object[] decodedResult = getMergedMiningTagsFunction.decodeResult(encodedResult);

        assertEquals(1, decodedResult.length);

        byte[] tags = (byte[]) decodedResult[0];

        String tagsString = new String(tags, StandardCharsets.UTF_8);
        String altTagString = new String(ADDITIONAL_TAG, StandardCharsets.UTF_8);

        assertEquals(tagsString.indexOf(altTagString), 0);
    }

    @Test
    public void getEmptyMergedMiningTags() {
        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        byte[] encodedResult = contract.execute(getMergedMiningTagsFunction.encode(new BigInteger("0")));
        Object[] decodedResult = getMergedMiningTagsFunction.decodeResult(encodedResult);

        assertEquals(1, decodedResult.length);

        byte[] tags = (byte[]) decodedResult[0];

        assertArrayEquals(tags, ByteUtil.EMPTY_BYTE_ARRAY);
    }

    @Test
    public void getGasLimit() {
        buildBlockchainOfLength(2);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        byte[] encodedResult = contract.execute(getGasLimitFunction.encode(new BigInteger("0")));
        Object[] decodedResult = getGasLimitFunction.decodeResult(encodedResult);

        assertEquals(1, decodedResult.length);

        byte[] gasLimitBytes = (byte[]) decodedResult[0];

        assertTrue(gasLimitBytes.length > 0);
        assertEquals(GAS_LIMIT, new BigInteger(gasLimitBytes));
    }

    @Test
    public void getGasUsed() {
        buildBlockchainOfLength(2);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        byte[] encodedResult = contract.execute(getGasUsedFunction.encode(new BigInteger("0")));
        Object[] decodedResult = getGasUsedFunction.decodeResult(encodedResult);

        assertEquals(1, decodedResult.length);

        byte[] gasUsedBytes = (byte[]) decodedResult[0];

        assertTrue(gasUsedBytes.length > 0);
        assertEquals(GAS_USED, new BigInteger(gasUsedBytes));
    }

    @Test
    public void getDifficulty() {
        buildBlockchainOfLength(2);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        byte[] encodedResult = contract.execute(getDifficultyFunction.encode(new BigInteger("0")));
        Object[] decodedResult = getDifficultyFunction.decodeResult(encodedResult);

        assertEquals(1, decodedResult.length);

        byte[] rskDifficulty = (byte[]) decodedResult[0];

        assertTrue(rskDifficulty.length > 0);
        assertEquals(RSK_DIFFICULTY, new BigInteger(rskDifficulty));
    }

    @Test
    public void getBitcoinHeader() {
        buildBlockchainOfLength(2);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        byte[] encodedResult = contract.execute(getBitcoinHeaderFunction.encode(new BigInteger("0")));
        Object[] decodedResult = getBitcoinHeaderFunction.decodeResult(encodedResult);

        assertEquals(1, decodedResult.length);

        byte[] bitcoinHeader = (byte[]) decodedResult[0];

        assertTrue(bitcoinHeader.length > 0);

        NetworkParameters bitcoinNetworkParameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

        assertNotNull(bitcoinNetworkParameters);

        BtcBlock bitcoinMergedMiningBlock = bitcoinNetworkParameters.getDefaultSerializer().makeBlock(bitcoinHeader);

        assertEquals(DIFFICULTY_TARGET, bitcoinMergedMiningBlock.getDifficultyTarget());
        assertNull(bitcoinMergedMiningBlock.getTransactions());
    }

    @Test
    public void getUncleCoinbaseAddress() {
        // creates a blockchain where every block has two uncles
        buildBlockchainOfLengthWithUncles(6);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        // getting first uncle
        byte[] encodedResult = contract.execute(getUncleCoinbaseAddressFunction.encode(new BigInteger("1"), new BigInteger("0")));
        Object[] decodedResult = getUncleCoinbaseAddressFunction.decodeResult(encodedResult);

        assertEquals(1, decodedResult.length);

        byte[] uncleCoinbase = (byte[]) decodedResult[0];
        byte[] expected = "33333333333333333333".getBytes();

        assertArrayEquals(expected, uncleCoinbase);

        // getting second uncle
        encodedResult = contract.execute(getUncleCoinbaseAddressFunction.encode(new BigInteger("1"), new BigInteger("1")));
        decodedResult = getUncleCoinbaseAddressFunction.decodeResult(encodedResult);

        assertEquals(1, decodedResult.length);

        uncleCoinbase = (byte[]) decodedResult[0];
        expected = "33333333333333333333".getBytes();

        assertArrayEquals(expected, uncleCoinbase);

        // should have no more uncles
        encodedResult = contract.execute(getUncleCoinbaseAddressFunction.encode(new BigInteger("1"), new BigInteger("2")));
        decodedResult = getUncleCoinbaseAddressFunction.decodeResult(encodedResult);

        assertEquals(1, decodedResult.length);

        uncleCoinbase = (byte[]) decodedResult[0];

        assertArrayEquals(ByteUtil.EMPTY_BYTE_ARRAY, uncleCoinbase);
    }

    @Test
    public void getDifficultyForBlockAtDepth1000() {
        buildBlockchainOfLength(4000);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        byte[] encodedResult = contract.execute(getDifficultyFunction.encode(new BigInteger("1000")));
        Object[] decodedResult = getDifficultyFunction.decodeResult(encodedResult);

        assertEquals(1, decodedResult.length);

        byte[] rskDifficulty = (byte[]) decodedResult[0];

        assertTrue(rskDifficulty.length > 0);
        assertEquals(RSK_DIFFICULTY, new BigInteger(rskDifficulty));
    }

    @Test
    public void blockBeyondMaximumBlockDepth() {
        buildBlockchainOfLength(5000);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        byte[] encodedResult = contract.execute(getMergedMiningTagsFunction.encode(new BigInteger("4992")));
        Object[] decodedResult = getMergedMiningTagsFunction.decodeResult(encodedResult);

        assertEquals(1, decodedResult.length);

        byte[] tags = (byte[]) decodedResult[0];

        assertArrayEquals(ByteUtil.EMPTY_BYTE_ARRAY, tags);
    }

    @Test
    public void invalidBlockDepth() {
        buildBlockchainOfLength(300);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        byte[] encodedResult = contract.execute(getMergedMiningTagsFunction.encode(new BigInteger("500")));
        Object[] decodedResult = getMergedMiningTagsFunction.decodeResult(encodedResult);

        assertEquals(1, decodedResult.length);

        byte[] tags = (byte[]) decodedResult[0];

        assertArrayEquals(ByteUtil.EMPTY_BYTE_ARRAY, tags);
    }

    @Test
    public void getBlockHeaderFieldsFromBranch(){
        String bestChainCoinbase = "22222222222222222222";
        String initialChainCoinbase = "33333333333333333333";

        // build initial chain with the third block using a different coinbase to differentiate it
        buildBlockchainOfLengthUsingCoinbase(2, new RskAddress(initialChainCoinbase.getBytes()));
        buildBlockchainOfLengthUsingCoinbase(1, new RskAddress(bestChainCoinbase.getBytes()));
        buildBlockchainOfLengthUsingCoinbase(7, new RskAddress(initialChainCoinbase.getBytes()));

        // keep a reference to the best block before adding a branch
        Block initialBestBlock = world.getBlockChain().getBestBlock();

        // best block number should be 10 at this moment
        assertEquals(10, initialBestBlock.getNumber());

        // extend chain from the third block to create a four-block branch, this branch becomes the best chain
        BlockChainBuilder.extend(world.getBlockChain(), 4, true, true, 3);

        // get a reference to new best block
        Block bestBlock = world.getBlockChain().getBestBlock();

        // best block number should be 7
        assertEquals(7, bestBlock.getNumber());

        // initialize contract with current best block
        initContract(contract, rskTx, bestBlock, world);

        // get coinbase of block at depth 1 from branch (current best chain), coinbase should be bestChainCoinbase
        executeAndAssertCoinbase(new BigInteger("1"), bestChainCoinbase);

        // get coinbase of block at depth 4 from branch (block belongs to initial chain), coinbase should be initialChainCoinbase
        executeAndAssertCoinbase(new BigInteger("4"), initialChainCoinbase);

        // initialize contract with best block from initial chain
        initContract(contract, rskTx, initialBestBlock, world);

        // get coinbase of block at depth 3 from branch (block belongs to initial chain), coinbase should be initialChainCoinbase
        executeAndAssertCoinbase(new BigInteger("3"), initialChainCoinbase);
    }

    @Test(expected = RuntimeException.class)
    public void negativeBlockDepth() {
        buildBlockchainOfLength(10);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        contract.execute(getCoinbaseFunction.encode(new BigInteger("-1")));

        Assert.fail("Trying to access a block using a negative block depth should throw an exception");
    }

    @Test(expected = RuntimeException.class)
    public void negativeUncleIndex() {
        buildBlockchainOfLength(10);

        contract.init(rskTx, world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());

        contract.execute(getUncleCoinbaseAddressFunction.encode(new BigInteger("1"), new BigInteger("-1")));

        Assert.fail("Trying to access an uncle coinbase using a negative uncle index should throw an exception");
    }

    private void executeAndAssertCoinbase(BigInteger blockDepth, String expectedCoinbase){
        byte[] encodedResult = contract.execute(getCoinbaseFunction.encode(blockDepth));
        Object[] decodedResult = getCoinbaseFunction.decodeResult(encodedResult);

        // coinbase should be expectedCoinbase
        assertEquals(1, decodedResult.length);
        byte[] coinbase = (byte[]) decodedResult[0];
        byte[] expected = expectedCoinbase.getBytes();
        assertArrayEquals(expected, coinbase);
    }

    private void initContract(BlockHeaderContract contract, Transaction tx, Block block, World world) {
        contract.init(tx, block, world.getRepository(), world.getBlockChain().getBlockStore(), null, new LinkedList<>());
    }

    private Block mineBlock(Block parent) {
        return mineBlock(parent, parent.getCoinbase());
    }

    private Block mineBlock(Block parent, RskAddress coinbase) {
        BlockGenerator blockGenerator = new BlockGenerator(config.getNetworkConstants(), config.getActivationConfig());
        byte[] prefix = new byte[1000];
        byte[] compressedTag = Arrays.concatenate(prefix, RskMiningConstants.RSK_TAG);

        Keccak256 mergedMiningHash = new Keccak256(parent.getHashForMergedMining());

        NetworkParameters networkParameters = RegTestParams.get();
        BtcTransaction mergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(networkParameters, mergedMiningHash.getBytes());
        BtcBlock mergedMiningBlock = MinerUtils.getBitcoinMergedMiningBlock(networkParameters, mergedMiningCoinbaseTransaction);

        BigInteger targetDifficulty = DifficultyUtils.difficultyToTarget(parent.getDifficulty());

        new BlockMiner(config.getActivationConfig()).findNonce(mergedMiningBlock, targetDifficulty);

        // We need to clone to allow modifications
        Block newBlock = blockFactory.cloneBlockForModification(blockGenerator.createChildBlock(
                parent, new ArrayList<>(), new ArrayList<>(), parent.getDifficulty().asBigInteger().longValue(),
                MIN_GAS_PRICE, parent.getGasLimit(), coinbase
        ));

        newBlock.setBitcoinMergedMiningHeader(mergedMiningBlock.cloneAsHeader().bitcoinSerialize());

        byte[] merkleProof = MinerUtils.buildMerkleProof(
                config.getActivationConfig(),
                pb -> pb.buildFromBlock(mergedMiningBlock),
                newBlock.getNumber()
        );

        byte[] additionalTag = Arrays.concatenate(ADDITIONAL_TAG, mergedMiningHash.getBytes());
        byte[] mergedMiningTx = org.bouncycastle.util.Arrays.concatenate(compressedTag, mergedMiningHash.getBytes(), additionalTag);

        newBlock.setBitcoinMergedMiningCoinbaseTransaction(mergedMiningTx);
        newBlock.setBitcoinMergedMiningMerkleProof(merkleProof);

        return newBlock;
    }

    private void buildBlockchainOfLengthUsingCoinbase(int length, RskAddress coinbase) {
        if (length < 0) return;

        for (int i = 0; i < length; i++) {
            Block block = mineBlock(world.getBlockChain().getBestBlock(), coinbase);
            world.getBlockChain().tryToConnect(block);
        }
    }

    // creates a blockchain where every block has two uncles
    private void buildBlockchainOfLengthWithUncles(int length) {
        if (length < 0) return;

        BlockChainBuilder.extend(world.getBlockChain(), length, true, true);
    }

    private void buildBlockchainOfLength(int length) {
        if (length < 0) return;

        for (int i = 0; i < length; i++) {
            Block block = mineBlock(world.getBlockChain().getBestBlock());
            world.getBlockChain().tryToConnect(block);
        }
    }



    private CallTransaction.Function getContractFunction(NativeContract contract, Class methodClass) {
        Optional<NativeMethod> method = contract.getMethods().stream().filter(m -> m.getClass() == methodClass).findFirst();
        assertTrue(method.isPresent());
        return method.get().getFunction();
    }

    @Test
    public void hasNoDefaultMethod() {
        Assert.assertFalse(contract.getDefaultMethod().isPresent());
    }

    @Test
    public void hasNineMethods() {
        Assert.assertEquals(9, contract.getMethods().size());
    }

    @Test
    public void hasGetCoinbaseAddress() {
        assertHasMethod(GetCoinbaseAddress.class, true);
    }

    @Test
    public void hasGetBlockHash() {
        assertHasMethod(GetBlockHash.class, true);
    }

    @Test
    public void hasGetMergedMiningTags() {
        assertHasMethod(GetMergedMiningTags.class, true);
    }

    @Test
    public void hasGetMinimumGasPrice() {
        assertHasMethod(GetMinimumGasPrice.class, true);
    }

    @Test
    public void hasGetGasLimit() {
        assertHasMethod(GetGasLimit.class, true);
    }

    @Test
    public void hasGetGasUsed() {
        assertHasMethod(GetGasUsed.class, true);
    }

    @Test
    public void hasGetDifficulty() {
        assertHasMethod(GetDifficulty.class, true);
    }

    @Test
    public void hasGetBitcoinHeader() {
        assertHasMethod(GetBitcoinHeader.class, true);
    }

    @Test
    public void hasGetUncleCoinbaseAddress() {
        assertHasMethod(GetUncleCoinbaseAddress.class, true);
    }

    private void assertHasMethod(Class clazz, boolean withAccessor) {
        Optional<NativeMethod> method = contract.getMethods().stream()
                .filter(m -> m.getClass() == clazz).findFirst();
        Assert.assertTrue(method.isPresent());
        Assert.assertEquals(executionEnvironment, method.get().getExecutionEnvironment());
        if (withAccessor) {
            Object accessor = Whitebox.getInternalState(method.get(), "blockAccessor");
            Assert.assertNotNull(accessor);
            Assert.assertEquals(BlockAccessor.class, accessor.getClass());
        }
    }
}