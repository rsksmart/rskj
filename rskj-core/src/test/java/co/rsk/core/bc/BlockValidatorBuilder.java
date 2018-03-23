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

package co.rsk.core.bc;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.DifficultyCalculator;
import co.rsk.validators.*;
import org.ethereum.core.Repository;
import org.ethereum.db.BlockStore;
import org.mockito.Mockito;

/**
 * Created by mario on 19/01/17.
 */
public class BlockValidatorBuilder {

    private final TestSystemProperties config = new TestSystemProperties();
    private BlockTxsValidationRule blockTxsValidationRule;

    private BlockTxsFieldsValidationRule blockTxsFieldsValidationRule;

    private PrevMinGasPriceRule prevMinGasPriceRule;

    private TxsMinGasPriceRule txsMinGasPriceRule;

    private BlockUnclesValidationRule blockUnclesValidationRule;

    private BlockRootValidationRule blockRootValidationRule;

    private RemascValidationRule remascValidationRule;

    private BlockParentNumberRule parentNumberRule;

    private BlockDifficultyRule difficultyRule;

    private BlockTimeStampValidationRule blockTimeStampValidationRule;

    private BlockParentGasLimitRule parentGasLimitRule;

    private BlockCompositeRule blockCompositeRule;

    private BlockParentCompositeRule blockParentCompositeRule;

    private BlockStore blockStore;

    public BlockValidatorBuilder addBlockTxsFieldsValidationRule() {
        this.blockTxsFieldsValidationRule = new BlockTxsFieldsValidationRule();
        return this;
    }

    public BlockValidatorBuilder addBlockTxsValidationRule(Repository repository) {
        this.blockTxsValidationRule = new BlockTxsValidationRule(repository);
        return this;
    }

    public BlockValidatorBuilder addPrevMinGasPriceRule() {
        this.prevMinGasPriceRule = new PrevMinGasPriceRule();
        return this;
    }

    public BlockValidatorBuilder addTxsMinGasPriceRule() {
        this.txsMinGasPriceRule = new TxsMinGasPriceRule();
        return this;
    }

    public BlockValidatorBuilder addParentBlockHeaderValidator() {
        this.addParentNumberRule().addDifficultyRule().addParentGasLimitRule();;
        return this;
    }

    public BlockValidatorBuilder addBlockUnclesValidationRule(BlockStore blockStore) {
        BlockValidationRule validationRule = Mockito.mock(BlockValidationRule.class);
        Mockito.when(validationRule.isValid(Mockito.any())).thenReturn(true);

        BlockParentDependantValidationRule parentValidationRule = Mockito.mock(BlockParentDependantValidationRule.class);
        Mockito.when(parentValidationRule.isValid(Mockito.any(), Mockito.any())).thenReturn(true);

        this.addBlockUnclesValidationRule(blockStore, validationRule, parentValidationRule);
        return this;
    }

    public BlockValidatorBuilder addBlockUnclesValidationRule(BlockStore blockStore, BlockValidationRule validationRule, BlockParentDependantValidationRule parentValidationRule) {
        int uncleListLimit = config.getBlockchainConfig().getCommonConstants().getUncleListLimit();
        int uncleGenLimit = config.getBlockchainConfig().getCommonConstants().getUncleGenerationLimit();
        this.blockUnclesValidationRule = new BlockUnclesValidationRule(config, blockStore, uncleListLimit, uncleGenLimit, validationRule, parentValidationRule);
        return this;
    }

    public BlockValidatorBuilder addBlockRootValidationRule() {
        this.blockRootValidationRule = new BlockRootValidationRule();
        return this;
    }

    public BlockValidatorBuilder addRemascValidationRule() {
        this.remascValidationRule = new RemascValidationRule();
        return this;
    }

    public BlockValidatorBuilder addParentNumberRule() {
        this.parentNumberRule = new BlockParentNumberRule();
        return this;
    }

    public BlockValidatorBuilder addDifficultyRule() {
        this.difficultyRule = new BlockDifficultyRule(new DifficultyCalculator(config));
        return this;
    }

    public BlockValidatorBuilder addParentGasLimitRule() {
        parentGasLimitRule = new BlockParentGasLimitRule(config.getBlockchainConfig().
                        getCommonConstants().getGasLimitBoundDivisor());
        return this;
    }

    public BlockValidatorBuilder blockCompositeRule(BlockCompositeRule blockCompositeRule) {
        this.blockCompositeRule = blockCompositeRule;
        return this;
    }

    public BlockValidatorBuilder blockParentCompositeRule(BlockParentCompositeRule blockParentCompositeRule) {
        this.blockParentCompositeRule = blockParentCompositeRule;
        return this;
    }

    public BlockValidatorBuilder addBlockTimeStampValidationRule(int validPeriod) {
        this.blockTimeStampValidationRule = new BlockTimeStampValidationRule(validPeriod);
        return this;
    }

    public BlockValidatorBuilder blockStore(BlockStore blockStore) {
        this.blockStore = blockStore;
        return this;
    }

    public BlockValidatorImpl build() {
        if (this.blockCompositeRule == null) {
            this.blockCompositeRule = new BlockCompositeRule(this.txsMinGasPriceRule, this.blockUnclesValidationRule, this.blockRootValidationRule, this.remascValidationRule, this.blockTimeStampValidationRule);
        }

        if (this.blockParentCompositeRule == null) {
            this.blockParentCompositeRule = new BlockParentCompositeRule(this.blockTxsFieldsValidationRule, this.blockTxsValidationRule, this.prevMinGasPriceRule, this.parentNumberRule, this.difficultyRule, this.parentGasLimitRule);
        }

        return new BlockValidatorImpl(this.blockStore, this.blockParentCompositeRule, this.blockCompositeRule);
    }
}
