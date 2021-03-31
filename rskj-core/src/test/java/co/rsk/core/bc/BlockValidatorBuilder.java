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
import co.rsk.db.RepositoryLocator;
import co.rsk.db.StateRootHandler;
import co.rsk.trie.TrieConverter;
import co.rsk.trie.TrieStore;
import co.rsk.validators.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.BlockStore;
import org.mockito.Mockito;

import java.util.HashMap;

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

    private BlockTimeStampValidationRule blockTimeStampValidationRule;

    private BlockCompositeRule blockCompositeRule;

    private BlockParentCompositeRule blockParentCompositeRule;

    private BlockStore blockStore;

    public BlockValidatorBuilder addBlockTxsFieldsValidationRule() {
        this.blockTxsFieldsValidationRule = new BlockTxsFieldsValidationRule();
        return this;
    }

    public BlockValidatorBuilder addBlockTxsValidationRule(TrieStore trieStore) {
        this.blockTxsValidationRule = new BlockTxsValidationRule(new RepositoryLocator(
                trieStore,
                new StateRootHandler(config.getActivationConfig(), new TrieConverter(), new HashMapDB(), new HashMap<>())
        ));
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

    public BlockValidatorBuilder addBlockUnclesValidationRule(BlockStore blockStore) {
        BlockHeaderValidationRule validationRule = Mockito.mock(BlockHeaderValidationRule.class);
        Mockito.when(validationRule.isValid(Mockito.any())).thenReturn(true);

        BlockHeaderParentDependantValidationRule parentValidationRule = Mockito.mock(BlockHeaderParentDependantValidationRule.class);
        Mockito.when(parentValidationRule.isValid(Mockito.any(), Mockito.any())).thenReturn(true);

        this.addBlockUnclesValidationRule(blockStore, validationRule, parentValidationRule);
        return this;
    }

    public BlockValidatorBuilder addBlockUnclesValidationRule(BlockStore blockStore, BlockHeaderValidationRule validationRule, BlockHeaderParentDependantValidationRule parentValidationRule) {
        int uncleListLimit = config.getNetworkConstants().getUncleListLimit();
        int uncleGenLimit = config.getNetworkConstants().getUncleGenerationLimit();
        this.blockUnclesValidationRule = new BlockUnclesValidationRule(blockStore, uncleListLimit, uncleGenLimit, validationRule, parentValidationRule);
        return this;
    }

    public BlockValidatorBuilder addBlockRootValidationRule() {
        this.blockRootValidationRule = new BlockRootValidationRule(config.getActivationConfig());
        return this;
    }

    public BlockValidatorBuilder addRemascValidationRule() {
        this.remascValidationRule = new RemascValidationRule();
        return this;
    }

    public BlockValidatorBuilder addBlockTimeStampValidationRule(int validPeriod) {
        this.blockTimeStampValidationRule = new BlockTimeStampValidationRule(validPeriod, config.getActivationConfig());
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
            this.blockParentCompositeRule = new BlockParentCompositeRule(this.blockTxsFieldsValidationRule, this.blockTxsValidationRule, this.prevMinGasPriceRule);
        }

        return new BlockValidatorImpl(this.blockStore, this.blockParentCompositeRule, this.blockCompositeRule);
    }
}
