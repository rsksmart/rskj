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

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.blockchain.utils.BlockMiner;
import co.rsk.config.RskMiningConstants;
import co.rsk.core.RskAddress;
import co.rsk.mine.MinerUtils;
import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.pcc.NativeContract;
import co.rsk.pcc.NativeMethod;
import co.rsk.test.World;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.util.DifficultyUtils;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Stream;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContractArgs;
import org.ethereum.vm.PrecompiledContractArgsBuilder;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BlockHeaderContractTest {
    private static final BigInteger AMOUNT = new BigInteger("1000000000000000000");
    private static final BigInteger NONCE = BigInteger.valueOf(0);
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("3000000");
    private static final BigInteger GAS_USED = BigInteger.valueOf(0);
    private static final BigInteger MIN_GAS_PRICE = new BigInteger("1"); // to match BlockGenerator min gas price
    private static final int MAXIMUM_BLOCK_DEPTH = 3999;
    private static final BigInteger RSK_BLOCK_DIFFICULTY = new BigInteger("1");
    private static final BigInteger RSK_BLOCK_WITH_UNCLES_DIFFICULTY = new BigInteger("3");
    private static final long DIFFICULTY_TARGET = 562036735;
    private static final String DATA = "80af2871";
    private static final byte[] ADDITIONAL_TAG = {'A','L','T','B','L','O','C','K',':'};
    private static final RskAddress BLOCK_HEADER_CONTRACT_ADDRESS = new RskAddress(PrecompiledContracts.BLOCK_HEADER_ADDR_STR);
    private static final ActivationConfig activationConfig = ActivationConfigsForTest.all();

    private ExecutionEnvironment executionEnvironment;
    private BlockFactory blockFactory;
    private World world;
    private Transaction rskTx;
    private BlockHeaderContract blockHeaderContract;
    private CallTransaction.Function getCoinbaseFunction;
    private CallTransaction.Function getMinGasPriceFunction;
    private CallTransaction.Function getBlockHashFunction;
    private CallTransaction.Function getMergedMiningTagsFunction;
    private CallTransaction.Function getGasLimitFunction;
    private CallTransaction.Function getGasUsedFunction;
    private CallTransaction.Function getDifficultyFunction;
    private CallTransaction.Function getBitcoinHeaderFunction;
    private CallTransaction.Function getUncleCoinbaseAddressFunction;
    private CallTransaction.Function getCumulativeDifficultyFunction;
    private CallTransaction.Function getTotalDifficultyFunction;

    @BeforeEach
    void setUp() {
        world = new World();
        blockHeaderContract = new BlockHeaderContract(activationConfig, BLOCK_HEADER_CONTRACT_ADDRESS);
        blockFactory = new BlockFactory(activationConfig);

        // contract methods
        getCoinbaseFunction = getContractFunction(blockHeaderContract, GetCoinbaseAddress.class);
        getMinGasPriceFunction = getContractFunction(blockHeaderContract, GetMinimumGasPrice.class);
        getBlockHashFunction = getContractFunction(blockHeaderContract, GetBlockHash.class);
        getMergedMiningTagsFunction = getContractFunction(blockHeaderContract, GetMergedMiningTags.class);
        getGasLimitFunction = getContractFunction(blockHeaderContract, GetGasLimit.class);
        getGasUsedFunction = getContractFunction(blockHeaderContract, GetGasUsed.class);
        getDifficultyFunction = getContractFunction(blockHeaderContract, GetDifficulty.class);
        getBitcoinHeaderFunction = getContractFunction(blockHeaderContract, GetBitcoinHeader.class);
        getUncleCoinbaseAddressFunction = getContractFunction(blockHeaderContract, GetUncleCoinbaseAddress.class);
        getCumulativeDifficultyFunction = getContractFunction(blockHeaderContract, GetCumulativeDifficulty.class);
        getTotalDifficultyFunction = getContractFunction(blockHeaderContract, GetTotalDifficulty.class);

        // invoke transaction
        rskTx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(BLOCK_HEADER_CONTRACT_ADDRESS.getBytes())
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(AMOUNT)
            .build();
        rskTx.sign(new ECKey().getPrivKeyBytes());

        executionEnvironment = mock(ExecutionEnvironment.class);
        TestUtils.setInternalState(blockHeaderContract, "executionEnvironment", executionEnvironment);
    }

    @Test
    void hasNoDefaultMethod() {
        assertFalse(blockHeaderContract.getDefaultMethod().isPresent());
    }

    @Test
    void hasExpectedNumberOfMethods() {
        int expectedNumberOfMethods = 11;
        assertEquals(expectedNumberOfMethods, blockHeaderContract.getMethods().size());
    }

    private static Stream<Class<? extends BlockHeaderContractMethod>> blockHeaderMethodsProvider() {
        return Stream.of(
            GetCoinbaseAddress.class,
            GetBlockHash.class,
            GetMergedMiningTags.class,
            GetMinimumGasPrice.class,
            GetGasLimit.class,
            GetGasUsed.class,
            GetDifficulty.class,
            GetBitcoinHeader.class,
            GetUncleCoinbaseAddress.class,
            GetCumulativeDifficulty.class,
            GetTotalDifficulty.class
        );
    }

    @ParameterizedTest
    @MethodSource("blockHeaderMethodsProvider")
    void hasMethod(Class<? extends BlockHeaderContractMethod> methodClass) {
        Optional<NativeMethod> method = blockHeaderContract.getMethods()
            .stream()
            .filter(m -> m.getClass() == methodClass)
            .findFirst();
        assertTrue(method.isPresent());
        assertEquals(executionEnvironment, method.get().getExecutionEnvironment());

        Object accessor = TestUtils.getInternalState(method.get(), "blockAccessor");
        assertNotNull(accessor);
        assertEquals(BlockAccessor.class, accessor.getClass());
    }

    private static Stream<Integer> blockDepthProvider() {
        int zeroBlockDepth = 0;
        int blockDepth = 1000;

        return Stream.of(zeroBlockDepth, blockDepth);
    }

    @ParameterizedTest
    @MethodSource("blockDepthProvider")
    void getCoinbaseAddress(int blockDepth) throws VMException {
        int blockchainLength = 4000;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        byte[] encodedResult = blockHeaderContract.execute(getCoinbaseFunction.encode(BigInteger.valueOf(blockDepth)));

        Object[] decodedResult = getCoinbaseFunction.decodeResult(encodedResult);
        assertDecodedResultLength(decodedResult);

        byte[] coinbase = (byte[]) decodedResult[0];
        byte[] expected = "33333333333333333333".getBytes();
        assertArrayEquals(expected, coinbase);
    }

    @ParameterizedTest
    @MethodSource("blockDepthProvider")
    void getBlockHash(int blockDepth) throws VMException {
        int blockchainLength = 3000;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        byte[] encodedResult = blockHeaderContract.execute(getBlockHashFunction.encode(BigInteger.valueOf(blockDepth)));

        Object[] decodedResult = getBlockHashFunction.decodeResult(encodedResult);
        assertDecodedResultLength(decodedResult);

        int blockNumber = blockchainLength - blockDepth;
        Block block = world.getBlockChain().getBlockByNumber(blockNumber);
        byte[] expectedHash = block.getParentHash().getBytes();
        byte[] blockHash = (byte[]) decodedResult[0];
        assertEquals(ByteUtil.toHexString(expectedHash), ByteUtil.toHexString(blockHash));
    }

    @Test
    void getEmptyMergedMiningTags() throws VMException {
        int blockchainLength = 0;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        int blockDepth = 0;
        byte[] encodedResult = blockHeaderContract.execute(getMergedMiningTagsFunction.encode(BigInteger.valueOf(blockDepth)));

        Object[] decodedResult = getMergedMiningTagsFunction.decodeResult(encodedResult);
        assertDecodedResultLength(decodedResult);

        byte[] tags = (byte[]) decodedResult[0];
        assertArrayEquals(EMPTY_BYTE_ARRAY, tags);
    }

    @ParameterizedTest
    @MethodSource("blockDepthProvider")
    void getMergedMiningTags_additionalTag(int blockDepth) throws VMException {
        int blockchainLength = 2000;
        buildBlockchainOfLengthWithAdditionalTag(blockchainLength);
        initContract();

        byte[] encodedResult = blockHeaderContract.execute(getMergedMiningTagsFunction.encode(BigInteger.valueOf(blockDepth)));

        Object[] decodedResult = getMergedMiningTagsFunction.decodeResult(encodedResult);
        assertDecodedResultLength(decodedResult);

        byte[] tags = (byte[]) decodedResult[0];
        String tagsString = new String(tags, StandardCharsets.UTF_8);
        String altTagString = new String(ADDITIONAL_TAG, StandardCharsets.UTF_8);
        int expectedTagsIndex = 0;
        assertEquals(expectedTagsIndex, tagsString.indexOf(altTagString));
    }

    @ParameterizedTest
    @MethodSource("blockDepthProvider")
    void getMinimumGasPrice(int blockDepth) throws VMException {
        int blockchainLength = 3000;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        byte[] encodedResult = blockHeaderContract.execute(getMinGasPriceFunction.encode(BigInteger.valueOf(blockDepth)));

        Object[] decodedResult = getMinGasPriceFunction.decodeResult(encodedResult);
        assertExpectedResult(MIN_GAS_PRICE, decodedResult);
    }

    @ParameterizedTest
    @MethodSource("blockDepthProvider")
    void getGasLimit(int blockDepth) throws VMException {
        int blockchainLength = 3000;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        byte[] encodedResult = blockHeaderContract.execute(getGasLimitFunction.encode(BigInteger.valueOf(blockDepth)));

        Object[] decodedResult = getGasLimitFunction.decodeResult(encodedResult);
        assertExpectedResult(GAS_LIMIT, decodedResult);
    }

    @ParameterizedTest
    @MethodSource("blockDepthProvider")
    void getGasUsed(int blockDepth) throws VMException {
        int blockchainLength = 3000;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        byte[] encodedResult = blockHeaderContract.execute(getGasUsedFunction.encode(BigInteger.valueOf(blockDepth)));

        Object[] decodedResult = getGasUsedFunction.decodeResult(encodedResult);
        assertExpectedResult(GAS_USED, decodedResult);
    }

    @ParameterizedTest
    @MethodSource("blockDepthProvider")
    void getDifficulty_shouldReturnBlockDifficulty(int blockDepth) throws VMException {
        int blockchainLength = 4000;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        byte[] encodedResult = blockHeaderContract.execute(getDifficultyFunction.encode(BigInteger.valueOf(blockDepth)));

        Object[] decodedResult = getDifficultyFunction.decodeResult(encodedResult);
        assertExpectedResult(RSK_BLOCK_DIFFICULTY, decodedResult);
    }

    @ParameterizedTest
    @MethodSource("blockDepthProvider")
    void getBitcoinHeader(int blockDepth) throws VMException {
        int blockchainLength = 3000;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        byte[] encodedResult = blockHeaderContract.execute(getBitcoinHeaderFunction.encode(BigInteger.valueOf(blockDepth)));

        Object[] decodedResult = getBitcoinHeaderFunction.decodeResult(encodedResult);
        assertDecodedResultLength(decodedResult);

        byte[] bitcoinHeader = (byte[]) decodedResult[0];
        assertTrue(bitcoinHeader.length > 0);

        NetworkParameters bitcoinNetworkParameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        assertNotNull(bitcoinNetworkParameters);
        BtcBlock bitcoinMergedMiningBlock = bitcoinNetworkParameters.getDefaultSerializer().makeBlock(bitcoinHeader);
        assertEquals(DIFFICULTY_TARGET, bitcoinMergedMiningBlock.getDifficultyTarget());
        assertNull(bitcoinMergedMiningBlock.getTransactions());
    }

    @ParameterizedTest
    @MethodSource("blockDepthProvider")
    void getUncleCoinbaseAddress(int blockDepth) throws VMException {
        int blockchainLength = 3000;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        // just first and second uncles should be valid,
        // since each block should have just two uncles
        byte[] validUnclesExpectedResultBytes = "33333333333333333333".getBytes();
        BigInteger validUnclesExpectedResult = new BigInteger(validUnclesExpectedResultBytes);

        // first uncle
        int firstUncleIndex = 0;
        byte[] encodedResultForFirstUncle = blockHeaderContract.execute(getUncleCoinbaseAddressFunction.encode(BigInteger.valueOf(blockDepth), BigInteger.valueOf(firstUncleIndex)));
        Object[] decodedResultForFirstUncle = getUncleCoinbaseAddressFunction.decodeResult(encodedResultForFirstUncle);
        assertExpectedResult(validUnclesExpectedResult, decodedResultForFirstUncle);

        // second uncle
        int secondUncleIndex = 1;
        byte[] encodedResultForSecondUncle = blockHeaderContract.execute(getUncleCoinbaseAddressFunction.encode(BigInteger.valueOf(blockDepth), BigInteger.valueOf(secondUncleIndex)));
        Object[] decodedResultForSecondUncle = getUncleCoinbaseAddressFunction.decodeResult(encodedResultForSecondUncle);
        assertExpectedResult(validUnclesExpectedResult, decodedResultForSecondUncle);

        // invalid uncle
        int invalidUncleIndex = 2;
        byte[] encodedResultForInvalidUncle = blockHeaderContract.execute(getUncleCoinbaseAddressFunction.encode(BigInteger.valueOf(blockDepth), BigInteger.valueOf(invalidUncleIndex)));
        Object[] decodedResultForInvalidUncle = getUncleCoinbaseAddressFunction.decodeResult(encodedResultForInvalidUncle);
        assertEmptyResult(decodedResultForInvalidUncle);
    }

    @ParameterizedTest
    @MethodSource("blockDepthProvider")
    void getUncleCoinbaseAddress_negativeUncleIndex_shouldThrowVME(int blockDepth) {
        int blockchainLength = 3000;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        int negativeUncleIndex = -1;
        byte[] encodedResult = getUncleCoinbaseAddressFunction.encode(BigInteger.valueOf(blockDepth), BigInteger.valueOf(negativeUncleIndex));

        assertThrows(
            VMException.class,
            () -> blockHeaderContract.execute(encodedResult),
            "Trying to access an uncle coinbase using a negative uncle index should throw an exception"
        );
    }

    @ParameterizedTest
    @MethodSource("blockDepthProvider")
    void getCumulativeDifficulty_whenMethodDisabled_shouldThrowVME(int blockDepth) {
        ActivationConfig activationConfigForReed = ActivationConfigsForTest.reed800();
        blockHeaderContract = new BlockHeaderContract(activationConfigForReed, BLOCK_HEADER_CONTRACT_ADDRESS);

        int blockchainLength = 4000;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        assertThrows(
            VMException.class,
            () -> blockHeaderContract.execute(getCumulativeDifficultyFunction.encode(BigInteger.valueOf(blockDepth)))
        );
    }

    @ParameterizedTest
    @MethodSource("blockDepthProvider")
    void getCumulativeDifficulty(int blockDepth) throws VMException {
        int blockchainLength = 4000;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        byte[] encodedResult = blockHeaderContract.execute(getCumulativeDifficultyFunction.encode(BigInteger.valueOf(blockDepth)));

        Object[] decodedResult = getCumulativeDifficultyFunction.decodeResult(encodedResult);
        assertExpectedResult(RSK_BLOCK_WITH_UNCLES_DIFFICULTY, decodedResult);
    }

    @ParameterizedTest
    @MethodSource("blockDepthProvider")
    void getTotalDifficulty_whenMethodDisabled_shouldThrowVME(int blockDepth) {
        // arrange
        int blockchainLength = 4000;
        buildBlockchainOfLength(blockchainLength);

        ActivationConfig activationConfigForReed = ActivationConfigsForTest.reed800();
        blockHeaderContract = new BlockHeaderContract(activationConfigForReed, BLOCK_HEADER_CONTRACT_ADDRESS);
        initContract();

        assertThrows(
            VMException.class,
            () -> blockHeaderContract.execute(getTotalDifficultyFunction.encode(BigInteger.valueOf(blockDepth)))
        );
    }

    @ParameterizedTest
    @MethodSource("blockDepthProvider")
    void getTotalDifficulty(int blockDepth) throws VMException {
        int blockchainLength = 4000;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        byte[] encodedResult = blockHeaderContract.execute(getTotalDifficultyFunction.encode(BigInteger.valueOf(blockDepth)));

        int blockHeight = blockchainLength - blockDepth;
        BigInteger expectedTotalDifficulty = calculateBlockExpectedTotalDifficultyWithUncles(blockHeight);
        Object[] decodedResult = getTotalDifficultyFunction.decodeResult(encodedResult);
        assertExpectedResult(expectedTotalDifficulty, decodedResult);
    }

    private BigInteger calculateBlockExpectedTotalDifficultyWithUncles(int blockHeight) {
        // Expected calculation:
        // N = blockHeight
        // - Block 0 (genesis): difficulty 1, no uncles
        // - Block 1: difficulty 1, no uncles
        // - From block 2 to N-1 (total N-2 blocks): difficulty 1 + 2 uncles (of difficulty 1 each) = 3 per block
        // Total difficulty at block height: 1 + 1 + (3 × (N-2))

        BigInteger amountOfBlocksWithoutUncles = BigInteger.valueOf(2);
        BigInteger difficultyFromBlocksWithoutUncles = RSK_BLOCK_DIFFICULTY.multiply(amountOfBlocksWithoutUncles);

        BigInteger amountOfBlocksWithUncles = BigInteger.valueOf(blockHeight).subtract(amountOfBlocksWithoutUncles);
        BigInteger difficultyFromBlocksWithUncles = RSK_BLOCK_WITH_UNCLES_DIFFICULTY.multiply(amountOfBlocksWithUncles);

        return difficultyFromBlocksWithUncles.add(difficultyFromBlocksWithoutUncles);
    }

    @Test
    void getBlockHeaderFieldsFromBranch() throws VMException {
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
        initContract(bestBlock);

        // get coinbase of block at depth 1 from branch (current best chain), coinbase should be bestChainCoinbase
        executeAndAssertCoinbase(new BigInteger("1"), bestChainCoinbase);

        // get coinbase of block at depth 4 from branch (block belongs to initial chain), coinbase should be initialChainCoinbase
        executeAndAssertCoinbase(new BigInteger("4"), initialChainCoinbase);

        // initialize contract with best block from initial chain
        initContract(initialBestBlock);

        // get coinbase of block at depth 3 from branch (block belongs to initial chain), coinbase should be initialChainCoinbase
        executeAndAssertCoinbase(new BigInteger("3"), initialChainCoinbase);
    }

    private void executeAndAssertCoinbase(BigInteger blockDepth, String expectedCoinbase) throws VMException {
        byte[] encodedResult = blockHeaderContract.execute(getCoinbaseFunction.encode(blockDepth));
        Object[] decodedResult = getCoinbaseFunction.decodeResult(encodedResult);

        // coinbase should be expectedCoinbase
        assertDecodedResultLength(decodedResult);
        byte[] coinbase = (byte[]) decodedResult[0];
        byte[] expected = expectedCoinbase.getBytes();
        assertArrayEquals(expected, coinbase);
    }

    @ParameterizedTest
    @MethodSource("blockHeaderMethodsProvider")
    void negativeBlockDepth_shouldThrowVME(Class<? extends BlockHeaderContractMethod> methodClass) {
        int blockchainLength = 10;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        CallTransaction.Function function = getContractFunction(blockHeaderContract, methodClass);
        int negativeBlockDepth = -1;
        assertThrows(
            VMException.class,
            () -> blockHeaderContract.execute(function.encode(negativeBlockDepth)),
            "Trying to access a block using a negative block depth should throw an exception"
        );
    }

    // GetUncleCoinbaseAddress receives two parameters, so we need to remove it to parameterize
    private static Stream<Class<? extends BlockHeaderContractMethod>> blockHeaderMethodsThatOnlyReceiveBlockDepthProvider() {
        return Stream.of(
            GetCoinbaseAddress.class,
            GetBlockHash.class,
            GetMergedMiningTags.class,
            GetMinimumGasPrice.class,
            GetGasLimit.class,
            GetGasUsed.class,
            GetDifficulty.class,
            GetBitcoinHeader.class,
            GetCumulativeDifficulty.class,
            GetTotalDifficulty.class
        );
    }

    @ParameterizedTest
    @MethodSource("blockHeaderMethodsThatOnlyReceiveBlockDepthProvider")
    void blockDepthBeyondMaximum_returnsEmptyArray(Class<? extends BlockHeaderContractMethod> method) throws VMException {
        // arrange
        int blockchainLength = 4000;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        CallTransaction.Function function = getContractFunction(blockHeaderContract, method);

        // act
        int blockDepth = MAXIMUM_BLOCK_DEPTH + 1;
        byte[] encodedResult = blockHeaderContract.execute(function.encode(BigInteger.valueOf(blockDepth)));

        // assert
        assertExecutionReturnsEmptyArray(function, encodedResult);
    }

    @ParameterizedTest
    @MethodSource("blockHeaderMethodsThatOnlyReceiveBlockDepthProvider")
    void blockDepthInvalid_returnsEmptyArray(Class<? extends BlockHeaderContractMethod> method) throws VMException {
        // arrange
        int blockchainLength = 2000;
        buildBlockchainOfLength(blockchainLength);
        initContract();

        CallTransaction.Function function = getContractFunction(blockHeaderContract, method);

        // act
        int blockDepth = 3000;
        byte[] encodedResult = blockHeaderContract.execute(function.encode(BigInteger.valueOf(blockDepth)));

        // assert
        assertExecutionReturnsEmptyArray(function, encodedResult);
    }

    @Test
    void getUncleCoinbaseAddress_blockDepthInvalid_returnsEmptyArrayForBothUncles() throws VMException {
        // arrange
        int blockchainLength = 2000;
        buildBlockchainOfLength(blockchainLength);
        initContract();
        CallTransaction.Function function = getContractFunction(blockHeaderContract, GetUncleCoinbaseAddress.class);

        // act
        int blockDepth = 3000;
        // first uncle
        int firstUncleIndex = 0;
        byte[] encodedResultForFirstUncle = blockHeaderContract.execute(function.encode(BigInteger.valueOf(blockDepth), BigInteger.valueOf(firstUncleIndex)));
        // second uncle
        int secondUncleIndex = 1;
        byte[] encodedResultForSecondUncle = blockHeaderContract.execute(function.encode(BigInteger.valueOf(blockDepth), BigInteger.valueOf(secondUncleIndex)));

        // assert
        assertExecutionReturnsEmptyArray(function, encodedResultForFirstUncle);
        assertExecutionReturnsEmptyArray(function, encodedResultForSecondUncle);
    }

    @Test
    void getUncleCoinbaseAddress_blockDepthBeyondMaximum_returnsEmptyArrayForBothUncles() throws VMException {
        // arrange
        int blockchainLength = 4000;
        buildBlockchainOfLength(blockchainLength);
        initContract();
        CallTransaction.Function function = getContractFunction(blockHeaderContract, GetUncleCoinbaseAddress.class);

        // act
        int blockDepth = MAXIMUM_BLOCK_DEPTH + 1;
        // first uncle
        int firstUncleIndex = 0;
        byte[] encodedResultForFirstUncle = blockHeaderContract.execute(function.encode(BigInteger.valueOf(blockDepth), BigInteger.valueOf(firstUncleIndex)));

        // second uncle
        int secondUncleIndex = 1;
        byte[] encodedResultForSecondUncle = blockHeaderContract.execute(function.encode(BigInteger.valueOf(blockDepth), BigInteger.valueOf(secondUncleIndex)));

        // assert
        assertExecutionReturnsEmptyArray(function, encodedResultForFirstUncle);
        assertExecutionReturnsEmptyArray(function, encodedResultForSecondUncle);
    }

    private CallTransaction.Function getContractFunction(NativeContract contract, Class<? extends BlockHeaderContractMethod> methodClass) {
        Optional<NativeMethod> method = contract.getMethods()
            .stream()
            .filter(m -> m.getClass() == methodClass)
            .findFirst();
        assertTrue(method.isPresent());

        return method.get().getFunction();
    }

    private void initContract() {
        initContract(world.getBlockChain().getBestBlock());
    }

    private void initContract(Block block) {
        PrecompiledContractArgs precompiledContractArgs = PrecompiledContractArgsBuilder.builder()
            .transaction(rskTx)
            .executionBlock(block)
            .repository(world.getRepository())
            .blockStore(world.getBlockStore())
            .build();
        blockHeaderContract.init(precompiledContractArgs);
    }

    // creates a blockchain where every block has two uncles
    private void buildBlockchainOfLength(int length) {
        if (length < 0) {
            return;
        }

        BlockChainBuilder.extend(world.getBlockChain(), length, true, true);
    }

    private void buildBlockchainOfLengthUsingCoinbase(int length, RskAddress coinbase) {
        if (length < 0) {
            return;
        }

        BlockChainBuilder.extendUsingCoinbase(world.getBlockChain(), length, false, true, coinbase);
    }

    private void buildBlockchainOfLengthWithAdditionalTag(int length) {
        if (length < 0) {
            return;
        }

        for (int i = 0; i < length; i++) {
            Block parent = world.getBlockChain().getBestBlock();
            Block block = mineBlockWithAdditionalTag(parent, parent.getCoinbase());
            world.getBlockChain().tryToConnect(block);
        }
    }

    private Block mineBlockWithAdditionalTag(Block parent, RskAddress coinbase) {
        NetworkParameters networkParameters = RegTestParams.get();
        BlockGenerator blockGenerator = new BlockGenerator(Constants.regtest(), activationConfig);

        Block childBlock = blockGenerator.createChildBlockUsingCoinbase(
            parent,
            new ArrayList<>(),
            new ArrayList<>(),
            parent.getDifficulty().asBigInteger().longValue(),
            MIN_GAS_PRICE,
            parent.getGasLimit(),
            coinbase
        );
        Block newBlock = blockFactory.cloneBlockForModification(childBlock);

        byte[] mergedMiningHash = childBlock.getHashForMergedMining();
        BtcTransaction mergedMiningCoinbaseTransaction =
            MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(networkParameters, mergedMiningHash);
        BtcBlock mergedMiningBlock =
            MinerUtils.getBitcoinMergedMiningBlock(networkParameters, mergedMiningCoinbaseTransaction);

        BigInteger targetDifficulty = DifficultyUtils.difficultyToTarget(parent.getDifficulty());
        new BlockMiner(activationConfig).findNonce(mergedMiningBlock, targetDifficulty);

        newBlock.setBitcoinMergedMiningHeader(mergedMiningBlock.cloneAsHeader().bitcoinSerialize());
        byte[] merkleProof = MinerUtils.buildMerkleProof(
            activationConfig,
            pb -> pb.buildFromBlock(mergedMiningBlock),
            newBlock.getNumber()
        );
        newBlock.setBitcoinMergedMiningMerkleProof(merkleProof);

        byte[] additionalTag = Arrays.concatenate(ADDITIONAL_TAG, mergedMiningHash);
        byte[] prefix = new byte[1000];
        byte[] compressedTag = Arrays.concatenate(prefix, RskMiningConstants.RSK_TAG);
        byte[] mergedMiningTx = org.bouncycastle.util.Arrays.concatenate(compressedTag, mergedMiningHash, additionalTag);

        newBlock.setBitcoinMergedMiningCoinbaseTransaction(mergedMiningTx);
        newBlock.seal();

        return newBlock;
    }

    private void assertExecutionReturnsEmptyArray(CallTransaction.Function function, byte[] encodedResult) {
        Object[] decodedResult = function.decodeResult(encodedResult);
        assertEmptyResult(decodedResult);
    }

    private void assertEmptyResult(Object[] decodedResult) {
        assertDecodedResultLength(decodedResult);

        byte[] result = (byte[]) decodedResult[0];
        assertArrayEquals(EMPTY_BYTE_ARRAY, result);
    }

    private void assertExpectedResult(BigInteger expectedResult, Object[] decodedResult) {
        assertDecodedResultLength(decodedResult);

        byte[] resultArg = (byte[]) decodedResult[0];
        BigInteger result = new BigInteger(resultArg);
        assertEquals(expectedResult, result);
    }

    private void assertDecodedResultLength(Object[] decodedResult) {
        int expectedDecodedResultLength = 1;
        assertEquals(expectedDecodedResultLength, decodedResult.length);
    }
}
