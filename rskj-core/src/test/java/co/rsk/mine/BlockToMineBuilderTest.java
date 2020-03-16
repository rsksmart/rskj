/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.mine;

import co.rsk.config.GasLimitConfig;
import co.rsk.config.MiningConfig;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.core.bc.BlockResult;
import co.rsk.core.bc.FamilyUtils;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.StateRootHandler;
import co.rsk.validators.BlockValidationRule;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({FamilyUtils.class,BlockHashesHelper.class})
public class BlockToMineBuilderTest {

    private BlockToMineBuilder blockBuilder;
    private BlockValidationRule validationRules;
    private BlockExecutor blockExecutor;
    private ActivationConfig activationConfig;

    @Before
    public void setUp() {
        initializeBuilderWithStubValues();
    }

    @Test
    public void BuildBlockHasEmptyUnclesWhenCreateAnInvalidBlock() {
        BlockHeader parent = buildBlockHeaderWithSibling();

        BlockResult expectedResult = mock(BlockResult.class);
        ArgumentCaptor<Block> blockCaptor = ArgumentCaptor.forClass(Block.class);

        when(validationRules.isValid(any())).thenReturn(false);
        when(blockExecutor.executeAndFill(blockCaptor.capture(), any())).thenReturn(expectedResult);

        blockBuilder.build(new ArrayList<>(Collections.singletonList(parent)), new byte[0]);

        assertThat(blockCaptor.getValue().getUncleList(), empty());
    }

    @Test
    public void BuildBlockHasUnclesWhenCreateAnInvalidBlock() {
        BlockHeader parent = buildBlockHeaderWithSibling();

        BlockResult expectedResult = mock(BlockResult.class);
        ArgumentCaptor<Block> blockCaptor = ArgumentCaptor.forClass(Block.class);
        when(validationRules.isValid(any())).thenReturn(true);
        when(blockExecutor.executeAndFill(blockCaptor.capture(), any())).thenReturn(expectedResult);

        blockBuilder.build(new ArrayList<>(Collections.singletonList(parent)), new byte[0]);

        assertThat(blockCaptor.getValue().getUncleList(), hasSize(1));
    }

    @Test
    public void buildBlockBeforeUMMActivation() {
        Keccak256 parentHash = TestUtils.randomHash();

        BlockHeader parent = mock(BlockHeader.class);
        when(parent.getNumber()).thenReturn(500L);
        when(parent.getHash()).thenReturn(parentHash);
        when(parent.getGasLimit()).thenReturn(new byte[0]);
        when(parent.getMinimumGasPrice()).thenReturn(mock(Coin.class));

        when(validationRules.isValid(any())).thenReturn(true);
        when(activationConfig.isActive(ConsensusRule.RSKIPUMM, 501L)).thenReturn(false);

        BlockResult expectedResult = mock(BlockResult.class);
        ArgumentCaptor<Block> blockCaptor = ArgumentCaptor.forClass(Block.class);
        when(blockExecutor.executeAndFill(blockCaptor.capture(), any())).thenReturn(expectedResult);

        blockBuilder.build(new ArrayList<>(Collections.singletonList(parent)), new byte[0]);

        Block actualBlock = blockCaptor.getValue();
        assertNull(actualBlock.getHeader().getUmmRoot());
    }

    @Test
    public void buildBlockAfterUMMActivation() {
        Keccak256 parentHash = TestUtils.randomHash();

        BlockHeader parent = mock(BlockHeader.class);
        when(parent.getNumber()).thenReturn(500L);
        when(parent.getHash()).thenReturn(parentHash);
        when(parent.getGasLimit()).thenReturn(new byte[0]);
        when(parent.getMinimumGasPrice()).thenReturn(mock(Coin.class));

        when(validationRules.isValid(any())).thenReturn(true);
        when(activationConfig.isActive(ConsensusRule.RSKIPUMM, 501L)).thenReturn(true);

        BlockResult expectedResult = mock(BlockResult.class);
        ArgumentCaptor<Block> blockCaptor = ArgumentCaptor.forClass(Block.class);
        when(blockExecutor.executeAndFill(blockCaptor.capture(), any())).thenReturn(expectedResult);

        blockBuilder.build(new ArrayList<>(Collections.singletonList(parent)), new byte[0]);

        Block actualBlock = blockCaptor.getValue();
        assertThat(actualBlock.getHeader().getUmmRoot(), is(new byte[0]));
    }

    @Test
    /**
     * Since a Builder pattern is used to create the header, it may lead to mistakes due to oversight when
     * configuring the fields. The intent of this test is to make those mistakes easier to spot.
     */
    public void blockHeaderCreation() {
        Keccak256 parentHash = TestUtils.randomHash();

        BlockHeader parent = mock(BlockHeader.class);
        when(parent.getNumber()).thenReturn(1000L);
        when(parent.getHash()).thenReturn(parentHash);
        when(parent.getGasLimit()).thenReturn(new byte[0]);
        when(parent.getMinimumGasPrice()).thenReturn(mock(Coin.class));

        BlockHeader uncle = createBlockHeader();
        mockBlockFamily(1000L, parentHash, uncle);
        byte[] unclesHash = HashUtil.keccak256(BlockHeader.getUnclesEncoded(Collections.singletonList(uncle)));

        BlockDifficulty blockDifficulty = new BlockDifficulty(BigInteger.valueOf(100L));
        GasLimitConfig gasLimitConfig = new GasLimitConfig(6800000,6800000,false);
        Keccak256 stateRoot = TestUtils.randomHash();
        RskAddress coinbase = TestUtils.randomAddress();
        Coin minGasPrice = Coin.valueOf(59000000);
        BigInteger gasLimit = BigInteger.valueOf(6800000);
        byte[] forkDetectionData = TestUtils.randomBytes(12);
        byte[] txRoot = TestUtils.randomHash().getBytes();
        long timestamp = 1584057600000L;
        byte[] extraData = "blocktominebuilder".getBytes();

        initializeBuilderWithStubValues(
                blockDifficulty,
                gasLimitConfig,
                stateRoot,
                coinbase,
                minGasPrice,
                gasLimit,
                forkDetectionData,
                txRoot,
                timestamp,
                ActivationConfigsForTest.all()
        );

        BlockResult expectedResult = mock(BlockResult.class);
        ArgumentCaptor<Block> blockCaptor = ArgumentCaptor.forClass(Block.class);
        when(blockExecutor.executeAndFill(blockCaptor.capture(), any())).thenReturn(expectedResult);

        blockBuilder.build(new ArrayList<>(Collections.singletonList(parent)), extraData);

        Block actualBlock = blockCaptor.getValue();
        BlockHeader actualHeader = actualBlock.getHeader();

        assertEquals(parentHash, actualHeader.getParentHash());
        assertArrayEquals(unclesHash, actualHeader.getUnclesHash());
        assertEquals(coinbase, actualHeader.getCoinbase());
        assertArrayEquals(EMPTY_TRIE_HASH, actualHeader.getStateRoot());
        assertArrayEquals(txRoot, actualHeader.getTxTrieRoot());
        assertArrayEquals(EMPTY_TRIE_HASH, actualHeader.getReceiptsRoot());
        assertArrayEquals(new Bloom().getData(), actualHeader.getLogsBloom());
        assertEquals(blockDifficulty, actualHeader.getDifficulty());
        assertEquals(1001, actualHeader.getNumber());
        assertArrayEquals(BigInteger.valueOf(6800000).toByteArray(), actualHeader.getGasLimit());
        assertEquals(0, actualHeader.getGasUsed());
        assertEquals(timestamp, actualHeader.getTimestamp());
        assertArrayEquals(extraData, actualHeader.getExtraData());
        assertArrayEquals(new byte[0], actualHeader.getBitcoinMergedMiningHeader());
        assertArrayEquals(new byte[0], actualHeader.getBitcoinMergedMiningMerkleProof());
        assertArrayEquals(new byte[0], actualHeader.getBitcoinMergedMiningCoinbaseTransaction());
        assertArrayEquals(forkDetectionData, actualHeader.getMiningForkDetectionData());
        assertEquals(minGasPrice, actualHeader.getMinimumGasPrice());
        assertEquals(1, actualHeader.getUncleCount());
        assertArrayEquals(new byte[0], actualHeader.getUmmRoot());
    }

    private void initializeBuilderWithStubValues() {
        initializeBuilderWithStubValues(
                mock(BlockDifficulty.class),
                new GasLimitConfig(0, 0, false),
                new Keccak256(EMPTY_TRIE_HASH),
                TestUtils.randomAddress(),
                mock(Coin.class),
                BigInteger.valueOf(0),
                new byte[0],
                EMPTY_TRIE_HASH,
                0,
                mock(ActivationConfig.class)
        );
    }

    private void initializeBuilderWithStubValues(BlockDifficulty blockDifficulty, GasLimitConfig gasLimitConfig, Keccak256 stateRoot,
                                                 RskAddress coinbase, Coin minGasPrice, BigInteger gasLimit,
                                                 byte[] forkDetectionData, byte[] txRoot, long timestamp, ActivationConfig stubbedActivationConfig) {
        validationRules = mock(BlockValidationRule.class);
        StateRootHandler stateRootHandler = mock(StateRootHandler.class);
        MiningConfig miningConfig = mock(MiningConfig.class);
        DifficultyCalculator difficultyCalculator = mock(DifficultyCalculator.class);
        MinimumGasPriceCalculator minimumGasPriceCalculator = mock(MinimumGasPriceCalculator.class);
        MinerUtils minerUtils = mock(MinerUtils.class);
        ForkDetectionDataCalculator forkDetectionDataCalculator = mock(ForkDetectionDataCalculator.class);
        GasLimitCalculator gasLimitCalculator = mock(GasLimitCalculator.class);
        MinerClock minerClock = mock(MinerClock.class);
        blockExecutor = mock(BlockExecutor.class);
        activationConfig = stubbedActivationConfig;

        blockBuilder = new BlockToMineBuilder(
                activationConfig,
                miningConfig,
                mock(RepositoryLocator.class),
                mock(BlockStore.class),
                mock(TransactionPool.class),
                difficultyCalculator,
                gasLimitCalculator,
                forkDetectionDataCalculator,
                validationRules,
                minerClock,
                new BlockFactory(activationConfig),
                blockExecutor,
                minimumGasPriceCalculator,
                minerUtils
        );

        when(minerUtils.getAllTransactions(any())).thenReturn(new ArrayList<>());
        when(minerUtils.filterTransactions(any(), any(), any(), any(), any())).thenReturn(new ArrayList<>());
        when(minimumGasPriceCalculator.calculate(any())).thenReturn(minGasPrice);
        when(stateRootHandler.translate(any())).thenReturn(stateRoot);
        when(miningConfig.getGasLimit()).thenReturn(gasLimitConfig);
        when(miningConfig.getUncleListLimit()).thenReturn(10);
        when(miningConfig.getCoinbaseAddress()).thenReturn(coinbase);
        when(difficultyCalculator.calcDifficulty(any(), any())).thenReturn(blockDifficulty);
        when(forkDetectionDataCalculator.calculateWithBlockHeaders(any())).thenReturn(forkDetectionData);
        when(gasLimitCalculator.calculateBlockGasLimit(any(), any(), any(), any(), anyBoolean())).thenReturn(gasLimit);
        when(minerClock.calculateTimestampForChild(any())).thenReturn(timestamp);

        PowerMockito.mockStatic(BlockHashesHelper.class);
        PowerMockito.when(BlockHashesHelper.getTxTrieRoot(any(), anyBoolean()))
                .thenReturn(txRoot);

        when(validationRules.isValid(any())).thenReturn(true);
    }

    private BlockHeader buildBlockHeaderWithSibling() {
        BlockHeader blockHeader = mock(BlockHeader.class);
        long blockNumber = 42L;
        when(blockHeader.getNumber()).thenReturn(blockNumber);
        Keccak256 blockHash = TestUtils.randomHash();
        when(blockHeader.getHash()).thenReturn(blockHash);
        when(blockHeader.getMinimumGasPrice()).thenReturn(mock(Coin.class));
        when(blockHeader.getGasLimit()).thenReturn(new byte[0]);

        mockBlockFamily(blockNumber, blockHash, createBlockHeader());

        return blockHeader;
    }

    private void mockBlockFamily(long blockNumber, Keccak256 blockHash, BlockHeader relative) {
        PowerMockito.mockStatic(FamilyUtils.class);
        PowerMockito.when(FamilyUtils.getUnclesHeaders(any(), eq(blockNumber + 1L), eq(blockHash), anyInt()))
                .thenReturn(Collections.singletonList(relative));
    }

    private BlockHeader createBlockHeader() {
        return new BlockHeader(
                EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, TestUtils.randomAddress(),
                EMPTY_TRIE_HASH, null, EMPTY_TRIE_HASH,
                new Bloom().getData(), BlockDifficulty.ZERO, 1L,
                EMPTY_BYTE_ARRAY, 0L, 0L, EMPTY_BYTE_ARRAY, Coin.ZERO,
                EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY,
                Coin.ZERO, 0, false, true, false,
                new byte[0]
        );
    }
}