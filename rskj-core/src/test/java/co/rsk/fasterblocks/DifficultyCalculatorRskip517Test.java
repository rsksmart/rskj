package co.rsk.fasterblocks;

import co.rsk.core.BlockDifficulty;
import co.rsk.core.DifficultyCalculator;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

public class DifficultyCalculatorRskip517Test {

    private static final long BLOCK_NUMBER = 30; // Multiple of BLOCK_COUNT_WINDOW
    private static final long PARENT_BLOCK_NUMBER = 29;
    private static final long BLOCK_TIMESTAMP = 1000;
    private static final long PARENT_BLOCK_TIMESTAMP = 980;
    private static final BigInteger INITIAL_DIFFICULTY = BigInteger.valueOf(1000000);

    @Mock
    private ActivationConfig activationConfig;

    @Mock
    private Constants constants;

    @Mock
    private BlockHeader blockHeader;

    @Mock
    private BlockHeader parentHeader;

    private DifficultyCalculator calculator;
    private List<BlockHeader> blockWindow;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        calculator = new DifficultyCalculator(activationConfig, constants);
        
        // Enable RSKIP517 for testing
        when(activationConfig.isActive(ConsensusRule.RSKIP517, BLOCK_NUMBER)).thenReturn(true);
        DifficultyCalculator.enableTesting();
        
        // Setup block header mocks
        when(blockHeader.getNumber()).thenReturn(BLOCK_NUMBER);
        when(blockHeader.getTimestamp()).thenReturn(BLOCK_TIMESTAMP);
        when(parentHeader.getNumber()).thenReturn(PARENT_BLOCK_NUMBER);
        when(parentHeader.getTimestamp()).thenReturn(PARENT_BLOCK_TIMESTAMP);
        when(parentHeader.getDifficulty()).thenReturn(new BlockDifficulty(INITIAL_DIFFICULTY));

        // Setup minimum difficulty
        BlockDifficulty minDifficulty = new BlockDifficulty(BigInteger.valueOf(1000));
        when(constants.getMinimumDifficulty(any())).thenReturn(minDifficulty);

        // Initialize block window with unique headers
        blockWindow = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            BlockHeader header = mock(BlockHeader.class);
            // Block numbers should be 1-30 to match the window
            when(header.getNumber()).thenReturn((long) (i + 1));
            // Each block is 17 seconds after the previous one to ensure decrease
            when(header.getTimestamp()).thenReturn(BLOCK_TIMESTAMP - (29 - i) * 17L);
            when(header.getUncleCount()).thenReturn(0);
            blockWindow.add(header);
        }
    }

    @AfterAll
    public static void after() {
        DifficultyCalculator.disableTesting();
    }

    @Test
    public void testDifficultyCalculationOnWindowBlock() {
        // Test case: Normal difficulty calculation on a block window
        // Using 17s intervals to ensure decrease (avoiding 18-22s range)
        BlockDifficulty result = calculator.calcDifficulty(blockHeader, parentHeader, blockWindow);
        
        // According to RSKIP:
        // - Uncle rate is 0 (< 0.7 threshold)
        // - Block time average is 17s (< 18s = BLOCK_TARGET * 0.9)
        // Therefore F = -ALPHA (-0.005) and difficulty should decrease
        BigInteger expectedDifficulty = INITIAL_DIFFICULTY.multiply(BigInteger.valueOf(995)).divide(BigInteger.valueOf(1000));
        assertEquals(new BlockDifficulty(expectedDifficulty), result, 
            "Difficulty should decrease by 0.5% when block time average < 18s and uncle rate < 0.7");
    }

    @Test
    public void testHighUncleRate() {
        // Test case: High uncle rate should increase difficulty
        for (BlockHeader header : blockWindow) {
            when(header.getUncleCount()).thenReturn(1); // All blocks have uncles
        }
        
        BlockDifficulty result = calculator.calcDifficulty(blockHeader, parentHeader, blockWindow);
        
        // Expected: Initial difficulty * (1 + ALPHA) because uncle rate >= UNCLE_TRESHOLD
        BigInteger expectedDifficulty = INITIAL_DIFFICULTY.multiply(BigInteger.valueOf(1005)).divide(BigInteger.valueOf(1000));
        assertEquals(new BlockDifficulty(expectedDifficulty), result);
    }

    @Test
    public void testFastBlockTime() {
        // Test case: Fast block time should decrease difficulty
        // Using 17s intervals (below 18s = BLOCK_TARGET * 0.9) to trigger decrease
        for (int i = 0; i < blockWindow.size(); i++) {
            BlockHeader header = blockWindow.get(i);
            when(header.getTimestamp()).thenReturn(BLOCK_TIMESTAMP - (29 - i) * 17L);
        }
        
        BlockDifficulty result = calculator.calcDifficulty(blockHeader, parentHeader, blockWindow);
        
        // According to RSKIP:
        // - Uncle rate is 0 (< 0.7 threshold)
        // - Block time average is 17s (< 18s = BLOCK_TARGET * 0.9)
        // Therefore F = -ALPHA (-0.005) and difficulty should decrease
        BigInteger expectedDifficulty = INITIAL_DIFFICULTY.multiply(BigInteger.valueOf(995)).divide(BigInteger.valueOf(1000));
        assertEquals(new BlockDifficulty(expectedDifficulty), result, 
            "Difficulty should decrease by 0.5% when block time average < 18s and uncle rate < 0.7");
    }

    @Test
    public void testEmptyBlockWindow() {
        // Test case: Empty block window should throw exception
        assertThrows(IllegalArgumentException.class, () -> 
            calculator.calcDifficulty(blockHeader, parentHeader, new ArrayList<>())
        );
    }

    @Test
    public void testInvalidBlockWindowSize() {
        // Test case: Block window with wrong size should throw exception
        List<BlockHeader> invalidWindow = new ArrayList<>(blockWindow.subList(0, 29));
        assertThrows(IllegalStateException.class, () ->
            calculator.calcDifficulty(blockHeader, parentHeader, invalidWindow)
        );
    }

    @Test
    public void testMinimumDifficulty() {
        // Test case: Ensure difficulty doesn't go below minimum
        BlockDifficulty minDifficulty = new BlockDifficulty(BigInteger.valueOf(1000));
        when(constants.getMinimumDifficulty(BLOCK_NUMBER)).thenReturn(minDifficulty);
        
        // Using 17s intervals to trigger difficulty decrease
        for (int i = 0; i < blockWindow.size(); i++) {
            BlockHeader header = blockWindow.get(i);
            when(header.getTimestamp()).thenReturn(BLOCK_TIMESTAMP - (29 - i) * 17L);
        }
        
        // Set initial difficulty to 1005, which when decreased by 0.5% would be 1000.5
        // This is just above minimum, so next decrease would go below minimum
        when(parentHeader.getDifficulty()).thenReturn(new BlockDifficulty(BigInteger.valueOf(1005)));
        
        BlockDifficulty result = calculator.calcDifficulty(blockHeader, parentHeader, blockWindow);
        
        // First decrease: 1005 * 0.995 = 1000.5 (above minimum)
        // Second decrease: 1000.5 * 0.995 = 995.5 (below minimum, so should use 1000)
        assertEquals(minDifficulty, result, 
            "When calculated difficulty would go below minimum (995.5), should use minimum value (1000)");
    }

    @Test
    public void testSlowBlockTime() {
        // Test case: Slow block time should increase difficulty
        // Using 25s intervals (well above 22s = BLOCK_TARGET * 1.1) to ensure increase
        for (int i = 0; i < blockWindow.size(); i++) {
            BlockHeader header = blockWindow.get(i);
            when(header.getTimestamp()).thenReturn(BLOCK_TIMESTAMP - (29 - i) * 25L);
        }
        
        BlockDifficulty result = calculator.calcDifficulty(blockHeader, parentHeader, blockWindow);
        
        // According to RSKIP:
        // - Uncle rate is 0 (< 0.7 threshold)
        // - Block time average is 25s (> 22s = BLOCK_TARGET * 1.1)
        // Therefore F = ALPHA (0.005) and difficulty should increase
        BigInteger expectedDifficulty = INITIAL_DIFFICULTY.multiply(BigInteger.valueOf(1005)).divide(BigInteger.valueOf(1000));
        assertEquals(new BlockDifficulty(expectedDifficulty), result, 
            "Difficulty should increase by 0.5% when block time average > 22s");
    }

    @Test
    public void testMixedUncleRates() {
        // Test case: Mixed uncle rates in the window
        for (int i = 0; i < blockWindow.size(); i++) {
            BlockHeader header = blockWindow.get(i);
            // Set uncle count to 1 for first 21 blocks (70% of window)
            when(header.getUncleCount()).thenReturn(i < 21 ? 1 : 0);
        }
        
        BlockDifficulty result = calculator.calcDifficulty(blockHeader, parentHeader, blockWindow);
        
        // Expected: Initial difficulty * (1 + ALPHA) because uncle rate >= UNCLE_TRESHOLD (0.7)
        BigInteger expectedDifficulty = INITIAL_DIFFICULTY.multiply(BigInteger.valueOf(1005)).divide(BigInteger.valueOf(1000));
        assertEquals(new BlockDifficulty(expectedDifficulty), result);
    }

    @Test
    public void testEdgeCaseUncleThreshold() {
        // Test case: Uncle rate exactly at threshold
        for (int i = 0; i < blockWindow.size(); i++) {
            BlockHeader header = blockWindow.get(i);
            // Set uncle count to 1 for exactly 21 blocks (70% of window)
            when(header.getUncleCount()).thenReturn(i < 21 ? 1 : 0);
            // Set block times to target (20 seconds) to avoid time-based adjustments
            when(header.getTimestamp()).thenReturn(BLOCK_TIMESTAMP - (30 - i) * 20L);
        }
        
        BlockDifficulty result = calculator.calcDifficulty(blockHeader, parentHeader, blockWindow);
        
        // Expected: Initial difficulty * (1 + ALPHA) because uncle rate = UNCLE_TRESHOLD (0.7)
        BigInteger expectedDifficulty = INITIAL_DIFFICULTY.multiply(BigInteger.valueOf(1005)).divide(BigInteger.valueOf(1000));
        assertEquals(new BlockDifficulty(expectedDifficulty), result);
    }

    @Test
    public void testSlowBlockTimeWithUncleRate() {
        // Test case: Slow block time should increase difficulty regardless of uncle rate
        // Using 23s intervals (above 22s = BLOCK_TARGET * 1.1) to trigger increase
        for (int i = 0; i < blockWindow.size(); i++) {
            BlockHeader header = blockWindow.get(i);
            when(header.getTimestamp()).thenReturn(BLOCK_TIMESTAMP - (29 - i) * 23L);
            // Add uncles to ensure uncle rate is above threshold (0.7)
            when(header.getUncleCount()).thenReturn(i < 22 ? 1 : 0); // ~73% uncle rate
        }
        
        BlockDifficulty result = calculator.calcDifficulty(blockHeader, parentHeader, blockWindow);
        
        // According to RSKIP:
        // - Uncle rate is ~0.73 (> 0.7 threshold)
        // - Block time average is 23s (> 22s = BLOCK_TARGET * 1.1)
        // Therefore F = ALPHA (0.005) and difficulty should increase
        BigInteger expectedDifficulty = INITIAL_DIFFICULTY.multiply(BigInteger.valueOf(1005)).divide(BigInteger.valueOf(1000));
        assertEquals(new BlockDifficulty(expectedDifficulty), result, 
            "Difficulty should increase by 0.5% when uncle rate > 0.7 or block time average > 22s");
    }
} 