package co.rsk.remasc;

import co.rsk.config.RemascConfig;
import co.rsk.config.RemascConfigFactory;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.peg.PegTestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Repository;
import org.ethereum.db.BlockStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

class RemascTest {

    private final static ActivationConfig genesisActivations = ActivationConfigsForTest.genesis();
    private final static ActivationConfig orchidActivations = ActivationConfigsForTest.orchid();
    private final static Constants mainnet = Constants.mainnet();
    private final static Coin minimumGasPrice = Coin.valueOf(100L);

    Repository repository;
    BlockStore blockStore;
    Block nextBlockToReward;

    private final static RemascConfig remascConfig = new RemascConfigFactory(RemascContract.REMASC_CONFIG).createRemascConfig("main");

    RemascTransaction executionTx;

    RskAddress remascAddr;

    Block executionBlock;

    BlockHeader blockHeader;

    List<LogInfo> logs;

    List<Block> blocks;

    DataWord rewardBalanceKey = DataWord.fromString("rewardBalance");

    @BeforeEach
    void setUp() {
        repository = mock(Repository.class);
        blockStore = mock(BlockStore.class);
        nextBlockToReward = mock(Block.class);
        long blockNumber = 3992L;
        when(nextBlockToReward.getParentHash()).thenReturn(PegTestUtils.createHash3((int) (blockNumber-1)));
        when(nextBlockToReward.getHash()).thenReturn(PegTestUtils.createHash3((int) blockNumber));
        when(nextBlockToReward.getNumber()).thenReturn(blockNumber);
        when(blockStore.getBlockByHash(nextBlockToReward.getParentHash().getBytes())).thenReturn(nextBlockToReward);

        when(blockStore.getBlockAtDepthStartingAt(anyLong(), any(byte[].class))).thenReturn(nextBlockToReward);

        executionTx = mock(RemascTransaction.class);

        remascAddr = PrecompiledContracts.REMASC_ADDR;

        executionBlock = mock(Block.class);
        when(executionBlock.getMinimumGasPrice()).thenReturn(minimumGasPrice);
        when(executionBlock.getNumber()).thenReturn(4100L);
        when(executionBlock.getHash()).thenReturn(PegTestUtils.createHash3(4100));

        blockHeader = mock(BlockHeader.class);
        when(executionBlock.getHeader()).thenReturn(blockHeader);
        when(executionBlock.getParentHash()).thenReturn(PegTestUtils.createHash3(1));

        logs = new ArrayList<>();
    }

    private void mockBlockStore(Coin feesPerBlock) {
        blocks = new ArrayList<>();
        int uncleGenerationLimit = mainnet.getUncleGenerationLimit();

        for (int i = 0; i < uncleGenerationLimit + 1; i++) {
            Block currentBlock = mock(Block.class);
            int currentBlockNumber = (int) (nextBlockToReward.getNumber() - i);
            when(currentBlock.getParentHash()).thenReturn(PegTestUtils.createHash3(currentBlockNumber - 1));
            when(currentBlock.getHash()).thenReturn(PegTestUtils.createHash3(currentBlockNumber));
            when(currentBlock.getNumber()).thenReturn((long) currentBlockNumber);

            BlockHeader blockHeader = mock(BlockHeader.class);

            when(blockHeader.getPaidFees()).thenReturn(feesPerBlock);
            when(blockHeader.getHash()).thenReturn(PegTestUtils.createHash3(currentBlockNumber));
            when(blockHeader.getCoinbase()).thenReturn(PegTestUtils.createRandomRskAddress());

            when(currentBlock.getHeader()).thenReturn(blockHeader);

            when(blockStore.getBlockByHash(currentBlock.getHash().getBytes())).thenReturn(currentBlock);
            blocks.add(currentBlock);
        }
    }

    private static Stream<Arguments> processMinersFeesArgProvider() {
        Coin minimumPayableGas = Coin.valueOf(mainnet.getMinimumPayableGas().longValue());
        Coin minPayableFees = minimumGasPrice.multiply(minimumPayableGas.asBigInteger());

        Coin equalToMinPayableFees = minPayableFees.multiply(BigInteger.valueOf(remascConfig.getSyntheticSpan()));
        Coin belowMinPayableFees = equalToMinPayableFees.subtract(Coin.valueOf(1L));

        return Stream.of(
            Arguments.of(genesisActivations, belowMinPayableFees, true),
            Arguments.of(orchidActivations, belowMinPayableFees, false),
            Arguments.of(orchidActivations, equalToMinPayableFees, true)
        );
    }

    @ParameterizedTest
    @MethodSource("processMinersFeesArgProvider")
    void test_processMinersFees(ActivationConfig activationConfig, Coin feesPerBlock, boolean success) {
        mockBlockStore(feesPerBlock);

        // Arrange
        Remasc remasc = new Remasc(
            mainnet,
            activationConfig,
            repository,
            blockStore,
            remascConfig,
            executionTx,
            remascAddr,
            executionBlock,
            logs
        );

        // Act
        remasc.processMinersFees();
        remasc.save();

        // Assert
        Coin syntheticReward = feesPerBlock.divide(BigInteger.valueOf(remascConfig.getSyntheticSpan()));
        Coin expectedRemascPayment = feesPerBlock.subtract(syntheticReward);
        Coin expectedRskLabPayment = syntheticReward.divide(BigInteger.valueOf(remascConfig.getRskLabsDivisor()));
        RskAddress iovLabsAddress = remasc.getRskLabsAddress();

        if (success) {
            verify(this.repository, times(1)).addStorageRow(remascAddr, rewardBalanceKey, DataWord.valueOf(expectedRemascPayment.getBytes()));
            verify(this.repository, times(1)).addBalance(remascAddr, expectedRskLabPayment.negate());
            verify(this.repository, times(1)).addBalance(iovLabsAddress, expectedRskLabPayment);
        } else {
            verify(this.repository, times(1)).addStorageRow(remascAddr, rewardBalanceKey, DataWord.valueOf(feesPerBlock.getBytes()));
            verify(this.repository, never()).addBalance(any(), any());
            verify(this.repository, never()).addBalance(any(),any());
        }
    }
}
