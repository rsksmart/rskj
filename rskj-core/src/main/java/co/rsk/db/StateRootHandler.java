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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.bc.BlockResult;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieConverter;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.core.BlockHeader;
import org.ethereum.datasource.KeyValueDataSource;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class StateRootHandler {
    private final BlockchainNetConfig blockchainConfig;
    private final TrieConverter trieConverter;
    private final StateRootTranslator stateRootTranslator;

    public StateRootHandler(
            RskSystemProperties config,
            TrieConverter trieConverter,
            KeyValueDataSource stateRootDB,
            Map<Keccak256, Keccak256> stateRootCache) {
        this.blockchainConfig = config.getBlockchainConfig();
        this.trieConverter = trieConverter;
        this.stateRootTranslator = new StateRootTranslator(stateRootDB, stateRootCache);
    }

    public Keccak256 translate(BlockHeader block) {
        BlockchainConfig configForBlock = blockchainConfig.getConfigForBlock(block.getNumber());
        Keccak256 blockStateRoot = new Keccak256(block.getStateRoot());
        if (configForBlock.isRskipUnitrie()) {
            return blockStateRoot;
        }

        return Objects.requireNonNull(
                stateRootTranslator.get(blockStateRoot),
                "Reset database or continue syncing with previous version"
        );
    }

    public Keccak256 convert(BlockHeader minedBlock, Trie executionResult) {
        BlockchainConfig configForBlock = blockchainConfig.getConfigForBlock(minedBlock.getNumber());
        if (configForBlock.isRskipUnitrie()) {
            return executionResult.getHash();
        }
        //we shouldn't be converting blocks before orchid in stable networks
        return new Keccak256(trieConverter.getOrchidAccountTrieRoot(executionResult));
    }

    public void register(BlockHeader executedBlock, Trie executionResult) {
        BlockchainConfig configForBlock = blockchainConfig.getConfigForBlock(executedBlock.getNumber());

        Keccak256 blockStateRoot = new Keccak256(executedBlock.getStateRoot());
        if (configForBlock.isRskipUnitrie()) {
            return;
        }

        if (executedBlock.isGenesis()) {
            Keccak256 genesisStateRoot = convert(executedBlock, executionResult);
            stateRootTranslator.put(genesisStateRoot, executionResult.getHash());
        } else if (configForBlock.isRskip85()) {
            Keccak256 orchidStateRoot = convert(executedBlock, executionResult);
            stateRootTranslator.put(orchidStateRoot, executionResult.getHash());
        } else {
            stateRootTranslator.put(blockStateRoot, executionResult.getHash());
        }
    }

    public boolean validate(BlockHeader block, BlockResult result) {
        BlockchainConfig configForBlock = blockchainConfig.getConfigForBlock(block.getNumber());
        if (!configForBlock.isRskip85()) {
            return true;
        }

        if (!configForBlock.isRskipUnitrie()) {
            byte[] orchidStateRoot = trieConverter.getOrchidAccountTrieRoot(result.getFinalState());
            return Arrays.equals(orchidStateRoot, block.getStateRoot());
        }

        // we only validate state roots of blocks newer than 0.5.0 activation
        return Arrays.equals(result.getStateRoot(), block.getStateRoot());
    }
}
