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

import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.config.GasLimitConfig;
import co.rsk.config.MiningConfig;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.Coin;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.core.bc.BlockResult;
import co.rsk.core.bc.FamilyUtils;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryLocator;
import co.rsk.validators.BlockValidationRule;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.mockito.Mockito.*;

/**
 * Tests that invalid transactions (those that caused exceptions during mining)
 * are properly evicted from the transaction pool after block building.
 */
@ExtendWith(MockitoExtension.class)
class BlockToMineBuilderInvalidTxEvictionTest {

    private BlockToMineBuilder blockBuilder;
    private BlockExecutor blockExecutor;
    private TransactionPool transactionPool;
    private BlockValidationRule validationRules;

    @BeforeEach
    void setUp() {
        validationRules = mock(BlockValidationRule.class);

        RepositoryLocator repositoryLocator = mock(RepositoryLocator.class);
        MiningConfig miningConfig = mock(MiningConfig.class);
        DifficultyCalculator difficultyCalculator = mock(DifficultyCalculator.class);
        MinimumGasPriceCalculator minimumGasPriceCalculator = mock(MinimumGasPriceCalculator.class);
        MinerUtils minerUtils = mock(MinerUtils.class);
        SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        ActivationConfig activationConfig = mock(ActivationConfig.class);

        blockExecutor = mock(BlockExecutor.class);
        transactionPool = mock(TransactionPool.class);
        blockBuilder = new BlockToMineBuilder(
                activationConfig,
                miningConfig,
                repositoryLocator,
                mock(BlockStore.class),
                transactionPool,
                difficultyCalculator,
                new GasLimitCalculator(Constants.mainnet()),
                new ForkDetectionDataCalculator(RegTestParams.get()),
                validationRules,
                mock(MinerClock.class),
                new BlockFactory(activationConfig),
                blockExecutor,
                minimumGasPriceCalculator,
                minerUtils,
                signatureCache
        );

        BlockDifficulty blockDifficulty = mock(BlockDifficulty.class);
        Repository snapshot = mock(Repository.class);
        GasLimitConfig gasLimitConfig = new GasLimitConfig(0, 0, false);

        when(minerUtils.getAllTransactions(any(), any())).thenReturn(new ArrayList<>());
        when(minerUtils.filterTransactions(any(), any(), any(), any(), any(), anyBoolean(), any())).thenReturn(new ArrayList<>());
        when(repositoryLocator.snapshotAt(any())).thenReturn(snapshot);
        when(minimumGasPriceCalculator.calculate(any())).thenReturn(mock(Coin.class));
        when(miningConfig.getGasLimit()).thenReturn(gasLimitConfig);
        when(miningConfig.getUncleListLimit()).thenReturn(10);
        when(miningConfig.getCoinbaseAddress()).thenReturn(TestUtils.generateAddress("coinbaseAddress"));
        when(difficultyCalculator.calcDifficulty(any(), any())).thenReturn(blockDifficulty);
    }

    @Test
    void build_whenBlockResultHasInvalidTxs_shouldEvictThemFromPool() {
        runMocked((parent) -> {
            Transaction invalidTx = mock(Transaction.class);
            List<Transaction> invalidTxs = Collections.singletonList(invalidTx);

            BlockResult blockResult = mock(BlockResult.class);
            when(blockResult.getInvalidTransactions()).thenReturn(invalidTxs);

            when(validationRules.isValid(any())).thenReturn(true);
            when(blockExecutor.executeAndFill(any(), any())).thenReturn(blockResult);

            blockBuilder.build(new ArrayList<>(Collections.singletonList(parent)), new byte[0]);

            verify(transactionPool).removeTransactions(invalidTxs);
        });
    }

    @Test
    void build_whenBlockResultHasNoInvalidTxs_shouldNotEvictFromPool() {
        runMocked((parent) -> {
            BlockResult blockResult = mock(BlockResult.class);
            when(blockResult.getInvalidTransactions()).thenReturn(Collections.emptyList());

            when(validationRules.isValid(any())).thenReturn(true);
            when(blockExecutor.executeAndFill(any(), any())).thenReturn(blockResult);

            blockBuilder.build(new ArrayList<>(Collections.singletonList(parent)), new byte[0]);

            // removeTransactions should only be called once (for filter-based removal),
            // not a second time for invalid txs since the list is empty.
            verify(transactionPool, times(1)).removeTransactions(any());
        });
    }

    private void runMocked(java.util.function.Consumer<BlockHeader> task) {
        BlockHeader blockHeader = mock(BlockHeader.class);
        long blockNumber = 42L;
        when(blockHeader.getNumber()).thenReturn(blockNumber);
        Keccak256 blockHash = TestUtils.generateHash("mockedHash");
        when(blockHeader.getHash()).thenReturn(blockHash);
        when(blockHeader.getMinimumGasPrice()).thenReturn(mock(Coin.class));
        when(blockHeader.getGasLimit()).thenReturn(new byte[0]);
        BlockHeader relative = createBlockHeader();

        try (MockedStatic<FamilyUtils> familyUtilsMocked = mockStatic(FamilyUtils.class)) {
            familyUtilsMocked.when(() -> FamilyUtils.getUnclesHeaders(any(), eq(blockNumber + 1L), eq(blockHash), anyInt()))
                    .thenReturn(Collections.singletonList(relative));

            task.accept(blockHeader);
        }
    }

    private BlockHeader createBlockHeader() {
        return new BlockHeaderV0(
                EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, TestUtils.generateAddress("blockHeader"),
                EMPTY_TRIE_HASH, null, EMPTY_TRIE_HASH,
                new Bloom().getData(), BlockDifficulty.ZERO, 1L,
                EMPTY_BYTE_ARRAY, 0L, 0L, EMPTY_BYTE_ARRAY, Coin.ZERO,
                EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY, EMPTY_BYTE_ARRAY,
                Coin.ZERO, 0, false, true, false,
                new byte[0], null
        );
    }
}
