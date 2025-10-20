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

package co.rsk.core;

import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DifficultyCalculatorTest {

    @Mock
    private ActivationConfig activationConfig;

    @Mock
    private Constants constants;

    @Mock
    private BlockHeader currentHeader;

    @Mock
    private BlockHeader parentHeader;

    private DifficultyCalculator calculator;

    private static final long PARENT_TIMESTAMP = 1000L;
    private static final long CURRENT_BLOCK_NUMBER = 100L;
    private static final BlockDifficulty PARENT_DIFFICULTY = new BlockDifficulty(BigInteger.valueOf(1000000));
    private static final BlockDifficulty MIN_DIFFICULTY = new BlockDifficulty(BigInteger.valueOf(100));
    private static final int DURATION_LIMIT = 14;
    private static final BigInteger DIFF_BOUND_DIVISOR = BigInteger.valueOf(2048);

    @BeforeEach
    void setUp() {
        // Setup default mock behaviors
        when(currentHeader.getNumber()).thenReturn(CURRENT_BLOCK_NUMBER);
        when(currentHeader.getUncleCount()).thenReturn(0);

        when(parentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP);
        when(parentHeader.getDifficulty()).thenReturn(PARENT_DIFFICULTY);

        when(constants.getDurationLimit()).thenReturn(DURATION_LIMIT);
        when(constants.getDifficultyBoundDivisor(any())).thenReturn(DIFF_BOUND_DIVISOR);
        when(constants.getMinimumDifficulty(anyLong())).thenReturn(MIN_DIFFICULTY);
    }

    @Test
    void testConstructorWithoutMetrics() {
        calculator = new DifficultyCalculator(activationConfig, constants);
        assertNotNull(calculator);
    }

    @Test
    void testConstructorWithMetricsDisabled() {
        calculator = new DifficultyCalculator(activationConfig, constants, false);
        assertNotNull(calculator);
    }

    @Test
    void testConstructorWithMetricsEnabled() {
        calculator = new DifficultyCalculator(activationConfig, constants, true);
        assertNotNull(calculator);
    }

    @Test
    void testPreRskip97WithLargeTimeDifferenceReturnsMinDifficulty() {
        // Setup: RSKIP97 not active, timestamp difference >= 600 seconds
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(false);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 600);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        assertThat(result, is(MIN_DIFFICULTY));
        verify(constants).getMinimumDifficulty(CURRENT_BLOCK_NUMBER);
    }

    @Test
    void testPreRskip97WithSmallTimeDifferenceCalculatesDifficulty() {
        // Setup: RSKIP97 not active, timestamp difference < 600 seconds
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(false);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 599);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // Should calculate difficulty, not return minimum
        assertNotNull(result);
        // With timestamp difference of 599 and calcDur of 14, difficulty should decrease (delta > calcDur)
        assertThat(result.compareTo(PARENT_DIFFICULTY), is(-1));
    }

    @Test
    void testPostRskip97WithLargeTimeDifferenceCalculatesDifficulty() {
        // Setup: RSKIP97 active, timestamp difference >= 600 seconds
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 600);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // Should calculate difficulty, not return minimum
        assertNotNull(result);
        // With timestamp difference of 600 and calcDur of 14, difficulty should decrease
        assertThat(result.compareTo(PARENT_DIFFICULTY), is(-1));
    }

    @Test
    void testPostRskip97WithVeryLargeTimeDifferenceCalculatesDifficulty() {
        // Setup: RSKIP97 active, timestamp difference much larger than 600
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 10000);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // Should calculate difficulty and decrease significantly, but not below minimum
        assertNotNull(result);
        assertTrue(result.compareTo(MIN_DIFFICULTY) >= 0); // >= MIN_DIFFICULTY
    }

    @Test
    void testDifficultyIncreasesWhenBlocksMinedFaster() {
        // Setup: calcDur (14) > delta (10) => difficulty should increase
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 10);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // Expected: parent difficulty + (parent difficulty / 2048)
        BigInteger expected = PARENT_DIFFICULTY.asBigInteger()
                .add(PARENT_DIFFICULTY.asBigInteger().divide(DIFF_BOUND_DIVISOR));
        assertThat(result, is(new BlockDifficulty(expected)));
    }

    @Test
    void testDifficultyDecreasesWhenBlocksMinedSlower() {
        // Setup: calcDur (14) < delta (20) => difficulty should decrease
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 20);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // Expected: parent difficulty - (parent difficulty / 2048)
        BigInteger expected = PARENT_DIFFICULTY.asBigInteger()
                .subtract(PARENT_DIFFICULTY.asBigInteger().divide(DIFF_BOUND_DIVISOR));
        assertThat(result, is(new BlockDifficulty(expected)));
    }

    @Test
    void testDifficultyUnchangedWhenCalcDurEqualsTimeDelta() {
        // Setup: calcDur (14) == delta (14) => difficulty unchanged
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + DURATION_LIMIT);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        assertThat(result, is(PARENT_DIFFICULTY));
    }

    @Test
    void testDifficultyUnchangedWithNegativeTimeDelta() {
        // Setup: timestamp goes backwards (negative delta)
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP - 10);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // Should return parent difficulty unchanged
        assertThat(result, is(PARENT_DIFFICULTY));
    }

    @Test
    void testDifficultyCalculationWithNoUncles() {
        // Setup: 0 uncles, calcDur = 1 * 14 = 14
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 10);
        when(currentHeader.getUncleCount()).thenReturn(0);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // calcDur (14) > delta (10) => difficulty increases
        assertThat(result.compareTo(PARENT_DIFFICULTY), is(1));
    }

    @Test
    void testDifficultyCalculationWithOneUncle() {
        // Setup: 1 uncle, calcDur = (1 + 1) * 14 = 28
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 20);
        when(currentHeader.getUncleCount()).thenReturn(1);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // calcDur (28) > delta (20) => difficulty increases
        assertThat(result.compareTo(PARENT_DIFFICULTY), is(1));
    }

    @Test
    void testDifficultyCalculationWithMultipleUncles() {
        // Setup: 3 uncles, calcDur = (1 + 3) * 14 = 56
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 50);
        when(currentHeader.getUncleCount()).thenReturn(3);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // calcDur (56) > delta (50) => difficulty increases
        assertThat(result.compareTo(PARENT_DIFFICULTY), is(1));
    }

    @Test
    void testDifficultyCalculationWithUnclesAffectsDirection() {
        // Setup: Without uncles, difficulty would decrease; with uncles it increases
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 20);

        // With 0 uncles: calcDur = 14, delta = 20 => decrease
        when(currentHeader.getUncleCount()).thenReturn(0);
        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty resultNoUncles = calculator.calcDifficulty(currentHeader, parentHeader);
        assertThat(resultNoUncles.compareTo(PARENT_DIFFICULTY), is(-1));

        // With 2 uncles: calcDur = 42, delta = 20 => increase
        when(currentHeader.getUncleCount()).thenReturn(2);
        BlockDifficulty resultWithUncles = calculator.calcDifficulty(currentHeader, parentHeader);
        assertThat(resultWithUncles.compareTo(PARENT_DIFFICULTY), is(1));
    }

    @Test
    void testMinimumDifficultyIsEnforced() {
        // Setup: Very low parent difficulty that would go below minimum
        BlockDifficulty lowParentDifficulty = new BlockDifficulty(BigInteger.valueOf(150));
        when(parentHeader.getDifficulty()).thenReturn(lowParentDifficulty);
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 1000); // Large delta to decrease difficulty

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // Result should not be less than minimum difficulty
        assertTrue(result.compareTo(MIN_DIFFICULTY) >= 0);
    }

    @Test
    void testZeroParentDifficultyEnforcesMinimum() {
        // Setup: Zero parent difficulty (genesis block scenario)
        BlockDifficulty zeroParentDifficulty = BlockDifficulty.ZERO;
        when(parentHeader.getDifficulty()).thenReturn(zeroParentDifficulty);
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 10);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // Result must be at least minimum difficulty
        assertTrue(result.compareTo(MIN_DIFFICULTY) >= 0); // >= MIN_DIFFICULTY
    }

    @Test
    void testMinimumDifficultyNotEnforcedWhenNotNeeded() {
        // Setup: Normal calculation that stays well above minimum
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 10);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // Result should be well above minimum
        assertThat(result.compareTo(MIN_DIFFICULTY), is(1));
    }

    @Test
    void testWithLargeDifficultyValues() {
        // Setup: Very large difficulty value
        BlockDifficulty largeDifficulty = new BlockDifficulty(new BigInteger("1000000000000000000000"));
        when(parentHeader.getDifficulty()).thenReturn(largeDifficulty);
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 10);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        assertNotNull(result);
        // With fast mining (delta < calcDur), difficulty should increase
        assertThat(result.compareTo(largeDifficulty), is(1));
    }

    @Test
    void testBoundaryTimestampDifference() {
        // Setup: Exactly at DURATION_LIMIT boundary
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + DURATION_LIMIT);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // calcDur == delta, should return parent difficulty unchanged
        assertThat(result, is(PARENT_DIFFICULTY));
    }

    @Test
    void testBoundaryPreRskip97ExactlyAt600Seconds() {
        // Setup: Exactly at 600 second boundary for pre-RSKIP97
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(false);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 600);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // Should return minimum difficulty
        assertThat(result, is(MIN_DIFFICULTY));
    }

    @Test
    void testBoundaryPreRskip97JustBefore600Seconds() {
        // Setup: Just before 600 second boundary for pre-RSKIP97
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(false);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 599);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // Should calculate difficulty, not return minimum
        assertNotNull(result);
        assertThat(result.compareTo(MIN_DIFFICULTY), is(1)); // Should be > min difficulty
    }

    @Test
    void testWithDifferentDiffBoundDivisors() {
        // Setup: Test with different difficulty bound divisor
        BigInteger smallerDivisor = BigInteger.valueOf(1024);
        when(constants.getDifficultyBoundDivisor(any())).thenReturn(smallerDivisor);
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP + 10);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // With smaller divisor, the adjustment should be larger
        BigInteger expected = PARENT_DIFFICULTY.asBigInteger()
                .add(PARENT_DIFFICULTY.asBigInteger().divide(smallerDivisor));
        assertThat(result, is(new BlockDifficulty(expected)));
    }

    @Test
    void testZeroTimestampDifference() {
        // Setup: Same timestamp for both blocks
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP97), eq(CURRENT_BLOCK_NUMBER))).thenReturn(true);
        when(currentHeader.getTimestamp()).thenReturn(PARENT_TIMESTAMP);

        calculator = new DifficultyCalculator(activationConfig, constants);
        BlockDifficulty result = calculator.calcDifficulty(currentHeader, parentHeader);

        // calcDur (14) > delta (0) => difficulty should increase
        assertThat(result.compareTo(PARENT_DIFFICULTY), is(1));
    }
}
