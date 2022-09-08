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

package co.rsk.validators;

import co.rsk.core.bc.ConsensusValidationMainchainView;
import co.rsk.crypto.Keccak256;
import co.rsk.mine.ForkDetectionDataCalculator;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.geq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ForkDetectionDataRuleTest {

    private ActivationConfig activationConfig;

    @BeforeEach
    public void setUp() {
        activationConfig = mock(ActivationConfig.class);
    }

    @Test
    public void validForBlocksBeforeRskip110UsingMethodThatReceivesBlockAsParameter() {
        long blockNumber = 4242;
        ForkDetectionDataRule rule = new ForkDetectionDataRule(
                activationConfig,
                mock(ConsensusValidationMainchainView.class),
                mock(ForkDetectionDataCalculator.class),
                449
        );

        BlockHeader header = mock(BlockHeader.class);
        when(header.getNumber()).thenReturn(blockNumber);

        Block block = mock(Block.class);
        when(block.getHeader()).thenReturn(header);

        assertTrue(rule.isValid(header));
    }

    @Test
    public void validForBlocksBeforeRskip110() {
        long blockNumber = 4242;
        ForkDetectionDataRule rule = new ForkDetectionDataRule(
                activationConfig,
                mock(ConsensusValidationMainchainView.class),
                mock(ForkDetectionDataCalculator.class),
                449
        );

        BlockHeader header = mock(BlockHeader.class);
        when(header.getNumber()).thenReturn(blockNumber);

        assertTrue(rule.isValid(header));
    }

    @Test
    public void invalidForRskip110ActiveButForkDetectionData() {
        long blockNumber = 42;
        enableRulesAt(blockNumber, ConsensusRule.RSKIP110);

        ForkDetectionDataRule rule = new ForkDetectionDataRule(
                activationConfig,
                mock(ConsensusValidationMainchainView.class),
                mock(ForkDetectionDataCalculator.class),
                449
        );

        BlockHeader header = mock(BlockHeader.class);
        when(header.getNumber()).thenReturn(blockNumber);
        when(header.getMiningForkDetectionData()).thenReturn(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 });

        assertFalse(rule.isValid(header));
    }

    @Test
    public void validForRskip110ActiveButNoForkDetectionDataBecauseNoEnoughBlocksToCalculateIt() {
        long blockNumber = 42;
        enableRulesAt(blockNumber, ConsensusRule.RSKIP110);

        ForkDetectionDataRule rule = new ForkDetectionDataRule(
                activationConfig,
                mock(ConsensusValidationMainchainView.class),
                mock(ForkDetectionDataCalculator.class),
                449
        );

        BlockHeader header = mock(BlockHeader.class);
        when(header.getNumber()).thenReturn(blockNumber);
        when(header.getMiningForkDetectionData()).thenReturn(new byte[0]);

        assertTrue(rule.isValid(header));
    }

    @Test
    public void invalidForRskip110ActiveAndForkDetectionDataButMissingBlocksForCalculation() {
        long blockNumber = 4242;
        enableRulesAt(blockNumber, ConsensusRule.RSKIP110);

        Keccak256 parentBlockHash = new Keccak256(getRandomHash());
        int requiredBlocksForForkDataCalculation = 449;
        ConsensusValidationMainchainView mainchainView = mock(ConsensusValidationMainchainView.class);
        when(mainchainView.get(parentBlockHash, requiredBlocksForForkDataCalculation)).thenReturn(new ArrayList<>());

        ForkDetectionDataRule rule = new ForkDetectionDataRule(
                activationConfig,
                mainchainView,
                mock(ForkDetectionDataCalculator.class),
                requiredBlocksForForkDataCalculation
        );

        BlockHeader header = mock(BlockHeader.class);
        when(header.getNumber()).thenReturn(blockNumber);
        when(header.getParentHash()).thenReturn(parentBlockHash);
        Keccak256 blockHash = new Keccak256(getRandomHash());
        when(header.getHash()).thenReturn(blockHash);
        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        when(header.getMiningForkDetectionData()).thenReturn(forkDetectionData);

        assertFalse(rule.isValid(header));
    }

    @Test
    public void validForRskip110ActiveAndForkDetectionData() {
        long blockNumber = 4242;
        enableRulesAt(blockNumber, ConsensusRule.RSKIP110);

        Keccak256 parentBlockHash = new Keccak256(getRandomHash());
        int requiredBlocksForForkDataCalculation = 449;
        List<BlockHeader> previousBlocks = IntStream
                .range(0, requiredBlocksForForkDataCalculation)
                .mapToObj(i -> mock(BlockHeader.class))
                .collect(Collectors.toList());
        ConsensusValidationMainchainView mainchainView = mock(ConsensusValidationMainchainView.class);
        when(mainchainView.get(parentBlockHash, requiredBlocksForForkDataCalculation)).thenReturn(previousBlocks);

        ForkDetectionDataCalculator calculator = mock(ForkDetectionDataCalculator.class);
        Keccak256 blockHash = new Keccak256(getRandomHash());
        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        when(calculator.calculateWithBlockHeaders(previousBlocks)).thenReturn(forkDetectionData);

        ForkDetectionDataRule rule = new ForkDetectionDataRule(
                activationConfig,
                mainchainView,
                calculator,
                requiredBlocksForForkDataCalculation
        );

        BlockHeader header = mock(BlockHeader.class);
        when(header.getNumber()).thenReturn(blockNumber);
        when(header.getHash()).thenReturn(blockHash);
        when(header.getParentHash()).thenReturn(parentBlockHash);
        when(header.getMiningForkDetectionData()).thenReturn(forkDetectionData);

        assertTrue(rule.isValid(header));
    }

    @Test
    public void invalidForRskip110ActiveAndForkDetectionDataBecauseDataDoesNotMatch() {
        long blockNumber = 4242;
        enableRulesAt(blockNumber, ConsensusRule.RSKIP110);

        Keccak256 parentBlockHash = new Keccak256(getRandomHash());
        int requiredBlocksForForkDataCalculation = 449;
        List<BlockHeader> previousBlocks = IntStream
                .range(0, requiredBlocksForForkDataCalculation)
                .mapToObj(i -> mock(BlockHeader.class))
                .collect(Collectors.toList());
        ConsensusValidationMainchainView mainchainView = mock(ConsensusValidationMainchainView.class);
        when(mainchainView.get(parentBlockHash, requiredBlocksForForkDataCalculation)).thenReturn(previousBlocks);

        ForkDetectionDataCalculator calculator = mock(ForkDetectionDataCalculator.class);
        Keccak256 blockHash = new Keccak256(getRandomHash());
        byte[] forkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
        when(calculator.calculateWithBlockHeaders(previousBlocks)).thenReturn(forkDetectionData);

        ForkDetectionDataRule rule = new ForkDetectionDataRule(
                activationConfig,
                mainchainView,
                calculator,
                requiredBlocksForForkDataCalculation
        );

        BlockHeader header = mock(BlockHeader.class);
        when(header.getNumber()).thenReturn(blockNumber);
        when(header.getHash()).thenReturn(blockHash);
        when(header.getParentHash()).thenReturn(parentBlockHash);
        byte[] headerForkDetectionData = new byte[] { 1, 2, 3, 4, 5, 6, 42, 8, 9, 10, 11, 12 };
        when(header.getMiningForkDetectionData()).thenReturn(headerForkDetectionData);

        assertFalse(rule.isValid(header));
    }

    private void enableRulesAt(long number, ConsensusRule... consensusRules) {
        for (ConsensusRule consensusRule : consensusRules) {
            when(activationConfig.isActive(eq(consensusRule), geq(number))).thenReturn(true);
        }
    }

    private byte[] getRandomHash() {
        byte[] byteArray = new byte[32];
        new SecureRandom().nextBytes(byteArray);
        return byteArray;
    }
}
