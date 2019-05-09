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

package co.rsk.db;

import co.rsk.core.bc.BlockResult;
import co.rsk.crypto.Keccak256;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockHeader;
import org.ethereum.datasource.KeyValueDataSource;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class StateRootHandler {
    private final ActivationConfig activationConfig;
    private final StateRootTranslator stateRootTranslator;

    public StateRootHandler(
            ActivationConfig activationConfig,
            KeyValueDataSource stateRootDB,
            Map<Keccak256, Keccak256> stateRootCache) {
        this.activationConfig = activationConfig;
        this.stateRootTranslator = new StateRootTranslator(stateRootDB, stateRootCache);
    }

    public Keccak256 translate(BlockHeader block) {
        boolean isRskip85Enabled = activationConfig.isActive(ConsensusRule.RSKIP85, block.getNumber());
        Keccak256 blockStateRoot = new Keccak256(block.getStateRoot());
        if (isRskip85Enabled || block.isGenesis()) {
            return blockStateRoot;
        }

        return Objects.requireNonNull(
                stateRootTranslator.get(blockStateRoot),
                "Reset database or continue syncing with previous version"
        );
    }

    public void register(BlockHeader executedBlock, Keccak256 calculatedStateRoot) {
        boolean isRskip85Enabled = activationConfig.isActive(ConsensusRule.RSKIP85, executedBlock.getNumber());
        // we only save state root translations for blocks before 0.5.0 activation
        if (!isRskip85Enabled) {
            Keccak256 blockStateRoot = new Keccak256(executedBlock.getStateRoot());
            stateRootTranslator.put(blockStateRoot, calculatedStateRoot);
        }
    }

    public boolean validate(BlockHeader block, BlockResult result) {
        boolean isRskip85Enabled = activationConfig.isActive(ConsensusRule.RSKIP85, block.getNumber());
        if (!isRskip85Enabled) {
            return true;
        }

        // we only validate state roots of blocks newer than 0.5.0 activation
        return Arrays.equals(result.getStateRoot(), block.getStateRoot());
    }
}
