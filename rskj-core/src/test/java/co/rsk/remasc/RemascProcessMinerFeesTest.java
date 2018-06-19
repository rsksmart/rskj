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

package co.rsk.remasc;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RemascConfig;
import co.rsk.config.RemascConfigFactory;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.PegTestUtils;
import co.rsk.test.builders.BlockChainBuilder;
import com.google.common.collect.Lists;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.regtest.RegTestConfig;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.BeforeClass;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RemascProcessMinerFeesTest {

    private static RemascConfig remascConfig;
    private static TestSystemProperties config;

    private Coin cowInitialBalance = new Coin(new BigInteger("1000000000000000000"));
    private long initialGasLimit = 10000000L;
    private long minerFee = 21000;
    private long txValue = 10000;
    private ECKey cowKey = ECKey.fromPrivate(Keccak256Helper.keccak256("cow".getBytes()));
    private byte[] cowAddress = cowKey.getAddress();
    private static RskAddress coinbaseA = TestUtils.randomAddress();
    private static RskAddress coinbaseB = TestUtils.randomAddress();
    private static RskAddress coinbaseC = TestUtils.randomAddress();
    private static RskAddress coinbaseD = TestUtils.randomAddress();
    private static RskAddress coinbaseE = TestUtils.randomAddress();
    private static List<byte[]> accountsAddressesUpToD;

    private Map<byte[], BigInteger> preMineMap = Collections.singletonMap(cowAddress, cowInitialBalance.asBigInteger());

    private Genesis genesisBlock = (Genesis) (new BlockGenerator()).getNewGenesisBlock(initialGasLimit, preMineMap);

    @BeforeClass
    public static void setUpBeforeClass() {
        config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestConfig());
        remascConfig = new RemascConfigFactory(RemascContract.REMASC_CONFIG).createRemascConfig("regtest");

        accountsAddressesUpToD = new LinkedList<>();
        accountsAddressesUpToD.add(coinbaseA.getBytes());
        accountsAddressesUpToD.add(coinbaseB.getBytes());
        accountsAddressesUpToD.add(coinbaseC.getBytes());
        accountsAddressesUpToD.add(coinbaseD.getBytes());
    }

    @Test
    public void processMinersFeesWithoutRequiredMaturity() {
        List<Block> blocks = createSimpleBlocks(genesisBlock, 1);
        Block blockWithOneTx = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(), coinbaseA, null, minerFee, 0, txValue, cowKey);
        blocks.add(blockWithOneTx);
        blocks.addAll(createSimpleBlocks(blockWithOneTx, 1));

        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.setGenesis(genesisBlock).setTesting(true).setBlocks(blocks).build();

        assertNull(blockchain.getRepository().getAccountState(coinbaseA));
    }

    @Test
    public void processMinersFeesWithoutMinimumSyntheticSpan() {
        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.setTesting(true).setGenesis(genesisBlock).build();

        List<Block> blocks = createSimpleBlocks(genesisBlock, 2);
        Block blockWithOneTx = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(), coinbaseA, null, minerFee, 0, txValue, cowKey);
        blocks.add(blockWithOneTx);
        blocks.addAll(createSimpleBlocks(blockWithOneTx, 9));

        BlockExecutor blockExecutor = new BlockExecutor(config, blockchain.getRepository(), null, blockchain.getBlockStore(), null);

        for (Block b : blocks) {
            blockExecutor.executeAndFillAll(b, blockchain.getBestBlock());
            blockchain.tryToConnect(b);
        }

        assertEquals(cowInitialBalance.subtract(Coin.valueOf(minerFee+txValue)), blockchain.getRepository().getAccountState(new RskAddress(cowAddress)).getBalance());

        Repository repository = blockchain.getRepository();

        assertEquals(Coin.valueOf(minerFee), repository.getAccountState(PrecompiledContracts.REMASC_ADDR).getBalance());
        assertNull(repository.getAccountState(coinbaseA));
        assertNull(repository.getAccountState(remascConfig.getRskLabsAddress()));

        Block newblock = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(), TestUtils.randomAddress(), null, null);

        blockExecutor.executeAndFillAll(newblock, blockchain.getBestBlock());
        blockchain.tryToConnect(newblock);

        repository = blockchain.getRepository();

        assertEquals(cowInitialBalance.subtract(Coin.valueOf(minerFee+txValue)), repository.getAccountState(new RskAddress(cowAddress)).getBalance());
        assertEquals(Coin.valueOf(minerFee), repository.getAccountState(PrecompiledContracts.REMASC_ADDR).getBalance());
        assertNull(repository.getAccountState(coinbaseA));
        assertNull(repository.getAccountState(remascConfig.getRskLabsAddress()));

        RemascStorageProvider remascStorageProvider = getRemascStorageProvider(blockchain);
        assertEquals(Coin.valueOf(minerFee), remascStorageProvider.getRewardBalance());
        assertEquals(Coin.ZERO, remascStorageProvider.getBurnedBalance());
        assertEquals(0, remascStorageProvider.getSiblings().size());
    }

    @Test
    public void processMinersFeesWithNoSiblings() {
        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.setTesting(true).setGenesis(genesisBlock).build();

        List<Block> blocks = createSimpleBlocks(genesisBlock, 4);
        Block blockWithOneTx = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(), coinbaseA, null, minerFee, 0, txValue, cowKey);
        blocks.add(blockWithOneTx);
        blocks.addAll(createSimpleBlocks(blockWithOneTx, 9));

        BlockExecutor blockExecutor = new BlockExecutor(config, blockchain.getRepository(), null, blockchain.getBlockStore(), null);

        for (Block b : blocks) {
            blockExecutor.executeAndFillAll(b, blockchain.getBestBlock());
            blockchain.tryToConnect(b);
        }

        Repository repository = blockchain.getRepository();

        assertEquals(cowInitialBalance.subtract(Coin.valueOf(minerFee+txValue)), repository.getAccountState(new RskAddress(cowAddress)).getBalance());
        assertEquals(Coin.valueOf(minerFee), repository.getAccountState(PrecompiledContracts.REMASC_ADDR).getBalance());
        assertNull(repository.getAccountState(coinbaseA));
        assertNull(repository.getAccountState(remascConfig.getRskLabsAddress()));

        RemascStorageProvider remascStorageProvider = getRemascStorageProvider(blockchain);

        assertEquals(Coin.ZERO, remascStorageProvider.getRewardBalance());
        assertEquals(Coin.ZERO, remascStorageProvider.getBurnedBalance());
        assertEquals(0, remascStorageProvider.getSiblings().size());

        Block newblock = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(), TestUtils.randomAddress(), null, null);

        blockExecutor.executeAndFillAll(newblock, blockchain.getBestBlock());
        blockchain.tryToConnect(newblock);

        repository = blockchain.getRepository();

        assertEquals(cowInitialBalance.subtract(Coin.valueOf(minerFee+txValue)), repository.getAccountState(new RskAddress(cowAddress)).getBalance());
        long blockReward = minerFee/remascConfig.getSyntheticSpan();
        assertEquals(Coin.valueOf(minerFee - blockReward), repository.getAccountState(PrecompiledContracts.REMASC_ADDR).getBalance());
        long rskReward = blockReward/remascConfig.getRskLabsDivisor();
        assertEquals(Coin.valueOf(rskReward), repository.getAccountState(remascConfig.getRskLabsAddress()).getBalance());
        long federationReward = (blockReward - rskReward)/remascConfig.getFederationDivisor();
        assertEquals(33, federationReward);
        assertEquals(Coin.valueOf(blockReward - rskReward - federationReward), repository.getAccountState(coinbaseA).getBalance());

        remascStorageProvider = getRemascStorageProvider(blockchain);

        assertEquals(Coin.valueOf(minerFee - blockReward), remascStorageProvider.getRewardBalance());
        assertEquals(Coin.ZERO, remascStorageProvider.getBurnedBalance());
        assertEquals(0, remascStorageProvider.getSiblings().size());

        this.validateFederatorsBalanceIsCorrect(blockchain.getRepository(), federationReward);
    }

    @Test
    public void processMinersFeesWithOneSibling() {
        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.setTesting(true).setGenesis(genesisBlock).build();

        List<Block> blocks = createSimpleBlocks(genesisBlock, 4);
        Block blockWithOneTxA = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(), coinbaseA, null, minerFee, 0, txValue, cowKey);
        Block blockWithOneTxB = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(), coinbaseB, null, (long) (minerFee*1.5), 0, txValue, cowKey);
        blocks.add(blockWithOneTxA);
        Block blockThatIncludesUncle = RemascTestRunner.createBlock(this.genesisBlock, blockWithOneTxA, PegTestUtils.createHash3(), coinbaseC, Lists.newArrayList(blockWithOneTxB.getHeader()), null);
        blocks.add(blockThatIncludesUncle);
        blocks.addAll(createSimpleBlocks(blockThatIncludesUncle, 8));

        BlockExecutor blockExecutor = new BlockExecutor(config, blockchain.getRepository(), null, blockchain.getBlockStore(), null);

        for (Block b : blocks) {
            blockExecutor.executeAndFillAll(b, blockchain.getBestBlock());
            blockchain.tryToConnect(b);
        }

        Repository repository = blockchain.getRepository();

        assertEquals(cowInitialBalance.subtract(Coin.valueOf(minerFee+txValue)), repository.getAccountState(new RskAddress(cowAddress)).getBalance());
        assertEquals(Coin.valueOf(minerFee), repository.getAccountState(PrecompiledContracts.REMASC_ADDR).getBalance());
        assertNull(repository.getAccountState(coinbaseA));
        assertNull(repository.getAccountState(coinbaseB));
        assertNull(repository.getAccountState(coinbaseC));
        assertNull(repository.getAccountState(remascConfig.getRskLabsAddress()));

        RemascStorageProvider remascStorageProvider = getRemascStorageProvider(blockchain);

        assertEquals(Coin.ZERO, remascStorageProvider.getRewardBalance());
        assertEquals(Coin.ZERO, remascStorageProvider.getBurnedBalance());
        assertEquals(1, remascStorageProvider.getSiblings().size());

        Block newblock = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1),
                PegTestUtils.createHash3(), TestUtils.randomAddress(), null, null);

        blockExecutor.executeAndFillAll(newblock, blockchain.getBestBlock());

        blockchain.tryToConnect(newblock);

        repository = blockchain.getRepository();

        assertEquals(cowInitialBalance.subtract(Coin.valueOf(minerFee+txValue)), repository.getAccountState(new RskAddress(cowAddress)).getBalance());

        long blockReward = minerFee/remascConfig.getSyntheticSpan();
        // There is one unit burned
        assertEquals(Coin.valueOf(minerFee - blockReward + 1), repository.getAccountState(PrecompiledContracts.REMASC_ADDR).getBalance());
        long rskReward = blockReward/remascConfig.getRskLabsDivisor();
        assertEquals(Coin.valueOf(rskReward), repository.getAccountState(remascConfig.getRskLabsAddress()).getBalance());
        long federationReward = (blockReward - rskReward)/remascConfig.getFederationDivisor();
        assertEquals(33, federationReward);

        blockReward = blockReward - rskReward - federationReward;
        assertEquals(Coin.valueOf(blockReward/remascConfig.getPublishersDivisor()), repository.getAccountState(coinbaseC).getBalance());
        blockReward = blockReward - blockReward/remascConfig.getPublishersDivisor();
        assertEquals(Coin.valueOf(blockReward/2), repository.getAccountState(coinbaseA).getBalance());
        assertEquals(Coin.valueOf(blockReward/2), repository.getAccountState(coinbaseB).getBalance());

        blockReward = minerFee/remascConfig.getSyntheticSpan();

        remascStorageProvider = getRemascStorageProvider(blockchain);

        assertEquals(Coin.valueOf(minerFee - blockReward), remascStorageProvider.getRewardBalance());
        assertEquals(Coin.valueOf(1), remascStorageProvider.getBurnedBalance());
        assertEquals(0, remascStorageProvider.getSiblings().size());

        this.validateFederatorsBalanceIsCorrect(blockchain.getRepository(), federationReward);
    }

    @Test
    public void processMinersFeesWithOneSiblingBrokenSelectionRuleBlockWithHigherFees() {
        processMinersFeesWithOneSiblingBrokenSelectionRule("higherFees");
    }

    /**
     * From RSKIP15, one of the three selection rules hash
     */
    @Test
    public void processMinersFeesWithOneSiblingBrokenSelectionRuleBlockWithLowerHash() {
        processMinersFeesWithOneSiblingBrokenSelectionRule("lowerHash");
    }

    
    @Test
    public void siblingThatBreaksSelectionRuleGetsPunished() {
        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.setTesting(true).setGenesis(genesisBlock).build();

        final long NUMBER_OF_TXS_WITH_FEES = 3;
        List<Block> blocks = createSimpleBlocks(genesisBlock, 4);

        Block blockAtHeightThree = blocks.get(blocks.size() - 1);
        Block blockWithOneTxA = RemascTestRunner.createBlock(this.genesisBlock, blockAtHeightThree,
                PegTestUtils.createHash3(), coinbaseA, null, minerFee, 0, txValue, cowKey, 2l);
        blocks.add(blockWithOneTxA);

        Block blockWithOneTxC = RemascTestRunner.createBlock(this.genesisBlock, blockWithOneTxA,
                PegTestUtils.createHash3(), coinbaseC, null, minerFee, 1, txValue, cowKey, 2l);
        blocks.add(blockWithOneTxC);

        Block blockWithOneTxD = RemascTestRunner.createBlock(this.genesisBlock, blockWithOneTxA,
                PegTestUtils.createHash3(), coinbaseD, null, minerFee, 1, txValue, cowKey, 2l);
        Block blockWithOneTxB = RemascTestRunner.createBlock(this.genesisBlock, blockAtHeightThree,
                PegTestUtils.createHash3(), coinbaseB, null, 3 * minerFee, 0, txValue, cowKey, 2l);

        Block blockThatIncludesUnclesE = RemascTestRunner.createBlock(this.genesisBlock, blockWithOneTxC,
                PegTestUtils.createHash3(), coinbaseE, Lists.newArrayList(blockWithOneTxB.getHeader(),
                        blockWithOneTxD.getHeader()), minerFee, 2, txValue, cowKey);
        blocks.add(blockThatIncludesUnclesE);
        blocks.addAll(createSimpleBlocks(blockThatIncludesUnclesE, 7));

        BlockExecutor blockExecutor = new BlockExecutor(config, blockchain.getRepository(), null,
                blockchain.getBlockStore(), null);

        for (Block b : blocks) {
            blockExecutor.executeAndFillAll(b, blockchain.getBestBlock());
            blockchain.tryToConnect(b);
        }

        // validate that the blockchain's and REMASC's initial states are correct
        Coin cowRemainingBalance = cowInitialBalance.subtract(Coin.valueOf(
                minerFee * NUMBER_OF_TXS_WITH_FEES + txValue * NUMBER_OF_TXS_WITH_FEES));
        List<Long> otherAccountsBalance = new ArrayList<>(Arrays.asList(null, null, null, null));
        this.validateAccountsCurrentBalanceIsCorrect(blockchain.getRepository(), cowRemainingBalance, minerFee * NUMBER_OF_TXS_WITH_FEES, null, this.getAccountsWithExpectedBalance(otherAccountsBalance));
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(blockchain), Coin.ZERO, Coin.ZERO, 2L);

        // add block to pay fees of blocks on blockchain's height 4
        Block blockToPayFeesOnHeightFour = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size() - 1), PegTestUtils.createHash3(), TestUtils.randomAddress(), null, minerFee, 3, txValue, cowKey);

        blockExecutor.executeAndFillAll(blockToPayFeesOnHeightFour, blockchain.getBestBlock());

        blockchain.tryToConnect(blockToPayFeesOnHeightFour);

        Repository repository = blockchain.getRepository();

        // -- After executing REMASC's contract for paying height 4 block
        // validate that account's balances are correct
        cowRemainingBalance = cowRemainingBalance.subtract(Coin.valueOf(minerFee + txValue));
        long minerRewardOnHeightFour = minerFee / remascConfig.getSyntheticSpan();
        long burnBalanceLevelFour = minerRewardOnHeightFour;
        long remascCurrentBalance = minerFee * 4 - burnBalanceLevelFour;
        long rskCurrentBalance = minerRewardOnHeightFour / remascConfig.getRskLabsDivisor();
        minerRewardOnHeightFour -= rskCurrentBalance;
        long federationReward = minerRewardOnHeightFour / remascConfig.getFederationDivisor();
        minerRewardOnHeightFour -= federationReward;
        long publishersFee = minerRewardOnHeightFour / remascConfig.getPublishersDivisor();
        minerRewardOnHeightFour -= minerRewardOnHeightFour / remascConfig.getPublishersDivisor();
        minerRewardOnHeightFour /= 2;
        long siblingPunishmentLvlFour = (long)(minerRewardOnHeightFour *  0.05);
        long siblingReward = minerRewardOnHeightFour - siblingPunishmentLvlFour;

        HashMap<byte[], Coin> otherAccountsBalanceOnHeightFour = this.getAccountsWithExpectedBalance(new ArrayList<>(Arrays.asList(minerRewardOnHeightFour, siblingReward, null, null)));
        otherAccountsBalanceOnHeightFour.put(coinbaseE.getBytes(), Coin.valueOf(publishersFee));
        remascCurrentBalance += siblingPunishmentLvlFour;
        // TODO review one unit burned?
        this.validateAccountsCurrentBalanceIsCorrect(repository, cowRemainingBalance, remascCurrentBalance + 1, rskCurrentBalance, otherAccountsBalanceOnHeightFour);

        this.validateFederatorsBalanceIsCorrect(repository, federationReward);

        // validate that REMASC's state is correct
        long blockRewardOnHeightFour = minerFee / remascConfig.getSyntheticSpan();
        Coin expectedRewardBalance = Coin.valueOf(minerFee - blockRewardOnHeightFour);
        Coin expectedBurnedBalance = Coin.valueOf(siblingPunishmentLvlFour);
        // TODO review one more burned unit
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(blockchain), expectedRewardBalance, expectedBurnedBalance.add(Coin.valueOf(1)), 1L);

        // add block to pay fees of blocks on blockchain's height 5
        Block blockToPayFeesOnHeightFive = RemascTestRunner.createBlock(this.genesisBlock, blockToPayFeesOnHeightFour, PegTestUtils.createHash3(), TestUtils.randomAddress(), null, minerFee, 4, txValue, cowKey);
        blockExecutor.executeAndFillAll(blockToPayFeesOnHeightFive, blockchain.getBestBlock());
        blockchain.tryToConnect(blockToPayFeesOnHeightFive);

        repository = blockchain.getRepository();

        // -- After executing REMASC's contract for paying height 5 block
        // validate that account's balances are correct
        cowRemainingBalance = cowRemainingBalance.subtract(Coin.valueOf(minerFee + txValue));
        long rewardBalance = minerFee - blockRewardOnHeightFour;
        rewardBalance += minerFee;
        long blockRewardOnHeightFive = rewardBalance / remascConfig.getSyntheticSpan();
        remascCurrentBalance += minerFee - blockRewardOnHeightFive;
        long rskReward = blockRewardOnHeightFive / remascConfig.getRskLabsDivisor();
        rskCurrentBalance += rskReward;
        blockRewardOnHeightFive -= rskReward;
        long federationReward2 = blockRewardOnHeightFive / remascConfig.getFederationDivisor();
        blockRewardOnHeightFive -= federationReward2;

        long publishersFeeOnHeightFive = blockRewardOnHeightFive / remascConfig.getPublishersDivisor();
        blockRewardOnHeightFive -= publishersFeeOnHeightFive;

        long numberOfSiblingsOnHeightFive = 2;
        blockRewardOnHeightFive /= numberOfSiblingsOnHeightFive;

        long punishmentFee = blockRewardOnHeightFive / remascConfig.getPunishmentDivisor();
        blockRewardOnHeightFive -= punishmentFee;
        remascCurrentBalance += (numberOfSiblingsOnHeightFive * punishmentFee);

        HashMap<byte[], Coin> otherAccountsBalanceOnHeightFive = this.getAccountsWithExpectedBalance(new ArrayList<>(Arrays.asList(minerRewardOnHeightFour, siblingReward, blockRewardOnHeightFive, blockRewardOnHeightFive)));
        otherAccountsBalanceOnHeightFive.put(coinbaseE.getBytes(), Coin.valueOf(publishersFee + publishersFeeOnHeightFive));
        // TODO review value 1
        this.validateAccountsCurrentBalanceIsCorrect(repository, cowRemainingBalance, remascCurrentBalance + 1, rskCurrentBalance, otherAccountsBalanceOnHeightFive);
        // validate that REMASC's state is correct
        blockRewardOnHeightFive = (2 * minerFee - blockRewardOnHeightFour ) / remascConfig.getSyntheticSpan();
        expectedRewardBalance = Coin.valueOf(minerFee * 2 - blockRewardOnHeightFour - blockRewardOnHeightFive);
        expectedBurnedBalance = Coin.valueOf((2 * punishmentFee) + siblingPunishmentLvlFour);
        // TODO review value + 1
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(blockchain), expectedRewardBalance, expectedBurnedBalance.add(Coin.valueOf(1)), 0L);
        this.validateFederatorsBalanceIsCorrect(blockchain.getRepository(), federationReward + federationReward2);
    }

    @Test
    public void noPublisherFeeIsPaidWhenThePublisherHasNoSiblings() {
        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.setTesting(true).setGenesis(genesisBlock).build();

        final long NUMBER_OF_TXS_WITH_FEES = 3;
        List<Block> blocks = createSimpleBlocks(genesisBlock, 4);
        Block blockWithOneTxD = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(), coinbaseD, null, minerFee, 0, txValue, cowKey);
        blocks.add(blockWithOneTxD);

        Block blockWithOneTxA = RemascTestRunner.createBlock(this.genesisBlock, blockWithOneTxD, PegTestUtils.createHash3(), coinbaseA, null, minerFee, 1, txValue, cowKey);
        Block blockWithOneTxB = RemascTestRunner.createBlock(this.genesisBlock, blockWithOneTxD, PegTestUtils.createHash3(), coinbaseB, null, minerFee * 3, 1, txValue, cowKey);
        blocks.add(blockWithOneTxA);

        Block blockThatIncludesUncleC = RemascTestRunner.createBlock(this.genesisBlock, blockWithOneTxA, PegTestUtils.createHash3(), coinbaseC, Lists.newArrayList(blockWithOneTxB.getHeader()), minerFee, 2, txValue, cowKey);
        blocks.add(blockThatIncludesUncleC);
        blocks.addAll(createSimpleBlocks(blockThatIncludesUncleC, 7));

        BlockExecutor blockExecutor = new BlockExecutor(config, blockchain.getRepository(), null, blockchain.getBlockStore(), null);

        for (Block b : blocks) {
            blockExecutor.executeAndFillAll(b, blockchain.getBestBlock());
            blockchain.tryToConnect(b);
        }

        // validate that the blockchain's and REMASC's initial states are correct
        Coin cowRemainingBalance = cowInitialBalance.subtract(Coin.valueOf(minerFee * NUMBER_OF_TXS_WITH_FEES + txValue * NUMBER_OF_TXS_WITH_FEES));
        List<Long> otherAccountsBalance = new ArrayList<>(Arrays.asList(null, null, null, null));
        this.validateAccountsCurrentBalanceIsCorrect(blockchain.getRepository(), cowRemainingBalance, minerFee * NUMBER_OF_TXS_WITH_FEES, null, this.getAccountsWithExpectedBalance(otherAccountsBalance));
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(blockchain), Coin.ZERO, Coin.ZERO, 1L);

        // add block to pay fees of blocks on blockchain's height 4
        Block blockToPayFeesOnHeightFour = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size() - 1), PegTestUtils.createHash3(), TestUtils.randomAddress(), null, minerFee, 0, txValue, cowKey);
        blockExecutor.executeAndFillAll(blockToPayFeesOnHeightFour, blockchain.getBestBlock());
        blockchain.tryToConnect(blockToPayFeesOnHeightFour);

        Repository repository = blockchain.getRepository();

        // -- After executing REMASC's contract for paying height 4 block
        // validate that account's balances are correct
        long blockRewardOnHeightFour = minerFee / remascConfig.getSyntheticSpan();
        long remascCurrentBalance = minerFee * 3 - blockRewardOnHeightFour;
        long rskCurrentBalance = blockRewardOnHeightFour / remascConfig.getRskLabsDivisor();
        blockRewardOnHeightFour -= rskCurrentBalance;
        long federationReward = blockRewardOnHeightFour / remascConfig.getFederationDivisor();
        assertEquals(33, federationReward);
        blockRewardOnHeightFour -= federationReward;
        List<Long> otherAccountsBalanceOnHeightFour = new ArrayList<>(Arrays.asList(null, null, null, blockRewardOnHeightFour));
        this.validateAccountsCurrentBalanceIsCorrect(repository, cowRemainingBalance, remascCurrentBalance, rskCurrentBalance, this.getAccountsWithExpectedBalance(otherAccountsBalanceOnHeightFour));
        // validate that REMASC's state is correct
        blockRewardOnHeightFour = minerFee / remascConfig.getSyntheticSpan();
        Coin expectedRewardBalance = Coin.valueOf(minerFee - blockRewardOnHeightFour);
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(blockchain), expectedRewardBalance, Coin.ZERO, 1L);
        this.validateFederatorsBalanceIsCorrect(blockchain.getRepository(), federationReward);
    }


    private void processMinersFeesWithOneSiblingBrokenSelectionRule(String reasonForBrokenSelectionRule) {
        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.setTesting(true).setGenesis(genesisBlock).build();

        final long NUMBER_OF_TXS_WITH_FEES = 3;
        List<Block> blocks = createSimpleBlocks(this.genesisBlock, 4);
        Block blockWithOneTxA;
        Block blockWithOneTxB;

        if ("higherFees".equals(reasonForBrokenSelectionRule)) {
            blockWithOneTxA = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1),
                    PegTestUtils.createHash3(), coinbaseA, null, minerFee, 0, txValue, cowKey);
            blockWithOneTxB = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1),
                    PegTestUtils.createHash3(), coinbaseB, null, minerFee * 3, 0, txValue, cowKey);
        } else {
            Keccak256 blockWithOneTxBHash = PegTestUtils.createHash3();
            Keccak256 blockWithOneTxAHash = PegTestUtils.createHash3();
            blockWithOneTxA = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1),
                    blockWithOneTxAHash, coinbaseA, null, minerFee, 0, txValue, cowKey);
            blockWithOneTxB = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1),
                    blockWithOneTxBHash, coinbaseB, null, (long) (minerFee * 1.5), 0, txValue, cowKey);
        }

        blocks.add(blockWithOneTxA);
        Block blockThatIncludesUncleC = RemascTestRunner.createBlock(this.genesisBlock,
                blockWithOneTxA, PegTestUtils.createHash3(), coinbaseC,
                Lists.newArrayList(blockWithOneTxB.getHeader()), minerFee, 1, txValue, cowKey);
        blocks.add(blockThatIncludesUncleC);


        Block blockWithOneTxD = RemascTestRunner.createBlock(this.genesisBlock,
                blockThatIncludesUncleC, PegTestUtils.createHash3(), coinbaseD,
                null, minerFee, 2, txValue, cowKey);
        blocks.add(blockWithOneTxD);
        blocks.addAll(createSimpleBlocks(blockWithOneTxD, 7));

        BlockExecutor blockExecutor = new BlockExecutor(config, blockchain.getRepository(),
                null, blockchain.getBlockStore(), null);

        for (Block b : blocks) {
            blockExecutor.executeAndFillAll(b, blockchain.getBestBlock());
            blockchain.tryToConnect(b);
        }

        // validate that the blockchain's and REMASC's initial states are correct
        Coin cowRemainingBalance = cowInitialBalance.subtract(Coin.valueOf(minerFee * NUMBER_OF_TXS_WITH_FEES + txValue * NUMBER_OF_TXS_WITH_FEES));
        List<Long> otherAccountsBalance = new ArrayList<>(Arrays.asList(null, null, null, null));
        this.validateAccountsCurrentBalanceIsCorrect(blockchain.getRepository(), cowRemainingBalance, minerFee * NUMBER_OF_TXS_WITH_FEES, null, this.getAccountsWithExpectedBalance(otherAccountsBalance));
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(blockchain), Coin.ZERO, Coin.ZERO, 1L);

        // add block to pay fees of blocks on blockchain's height 5
        Block blockToPayFeesOnHeightFive = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size() - 1), PegTestUtils.createHash3(), TestUtils.randomAddress(), null, null);
        blockExecutor.executeAndFillAll(blockToPayFeesOnHeightFive, blockchain.getBestBlock());
        blockchain.tryToConnect(blockToPayFeesOnHeightFive);

        // -- After executing REMASC's contract for paying height 5 blocks
        // validate that account's balances are correct
        long blockRewardOnHeightFive = minerFee / remascConfig.getSyntheticSpan();
        long remascCurrentBalance = minerFee * 3 - blockRewardOnHeightFive;
        long rskReward = blockRewardOnHeightFive / remascConfig.getRskLabsDivisor();
        long rskCurrentBalance = rskReward;
        blockRewardOnHeightFive -= rskReward;
        long federationReward = blockRewardOnHeightFive / remascConfig.getFederationDivisor();
        blockRewardOnHeightFive -= federationReward;
        long publisherReward = blockRewardOnHeightFive / remascConfig.getPublishersDivisor();
        blockRewardOnHeightFive -= publisherReward;
        long minerRewardOnHeightFive = blockRewardOnHeightFive / 2;
        List<Long> otherAccountsBalanceOnHeightFive = new ArrayList<>(Arrays.asList(minerRewardOnHeightFive, minerRewardOnHeightFive, publisherReward, null));

        this.validateFederatorsBalanceIsCorrect(blockchain.getRepository(), federationReward);

        // TODO review value + 1
        this.validateAccountsCurrentBalanceIsCorrect(blockchain.getRepository(), cowRemainingBalance, remascCurrentBalance + 1, rskCurrentBalance, this.getAccountsWithExpectedBalance(otherAccountsBalanceOnHeightFive));
        // validate that REMASC's state is correct
        blockRewardOnHeightFive = minerFee / remascConfig.getSyntheticSpan();
        Coin expectedRewardBalance = Coin.valueOf(minerFee - blockRewardOnHeightFive);
        // TODO review burned value 1
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(blockchain), expectedRewardBalance, Coin.valueOf(1), 0L);

        // add block to pay fees of blocks on blockchain's height 6
        Block blockToPayFeesOnHeightSix = RemascTestRunner.createBlock(this.genesisBlock, blockToPayFeesOnHeightFive, PegTestUtils.createHash3(), TestUtils.randomAddress(), null, null);
        blockExecutor.executeAndFillAll(blockToPayFeesOnHeightSix, blockchain.getBestBlock());
        blockchain.tryToConnect(blockToPayFeesOnHeightSix);

        // -- After executing REMASC's contract for paying height 6 blocks
        // validate that account's balances are correct
        long rewardBalance = minerFee - blockRewardOnHeightFive + minerFee;
        long blockRewardOnHeightSix = rewardBalance / remascConfig.getSyntheticSpan();
        rewardBalance -= blockRewardOnHeightSix;

        rskReward = blockRewardOnHeightSix / remascConfig.getRskLabsDivisor();
        long blockRewardWithoutRskFee = blockRewardOnHeightSix - rskReward;
        long federationReward2 = blockRewardWithoutRskFee / remascConfig.getFederationDivisor();
        long blockRewardWithoutRskAndFederationFee = blockRewardWithoutRskFee - federationReward2;
        long burnedBalance = blockRewardWithoutRskAndFederationFee / remascConfig.getPunishmentDivisor();

        remascCurrentBalance = minerFee * NUMBER_OF_TXS_WITH_FEES - blockRewardOnHeightFive - blockRewardOnHeightSix + burnedBalance;
        rskCurrentBalance += blockRewardOnHeightSix / remascConfig.getRskLabsDivisor();
        blockRewardOnHeightSix -= blockRewardOnHeightSix / remascConfig.getRskLabsDivisor();
        blockRewardOnHeightSix -= blockRewardOnHeightSix / remascConfig.getFederationDivisor();
        blockRewardOnHeightSix -= blockRewardOnHeightSix / remascConfig.getPunishmentDivisor();
        List<Long> otherAccountsBalanceOnHeightSix = new ArrayList<>(Arrays.asList(minerRewardOnHeightFive, minerRewardOnHeightFive, publisherReward + blockRewardOnHeightSix, null));

        // TODO review + 1
        this.validateAccountsCurrentBalanceIsCorrect(blockchain.getRepository(), cowRemainingBalance, remascCurrentBalance + 1, rskCurrentBalance, this.getAccountsWithExpectedBalance(otherAccountsBalanceOnHeightSix));
        this.validateFederatorsBalanceIsCorrect(blockchain.getRepository(), federationReward + federationReward2);
        // TODO review + 1
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(blockchain), Coin.valueOf(rewardBalance), Coin.valueOf(burnedBalance + 1), 0L);
    }

    @Test
    public void processMinersFeesFromTxThatIsNotTheLatestTx() {
        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.setTesting(true).setGenesis(genesisBlock).build();

        List<Block> blocks = createSimpleBlocks(genesisBlock, 4);
        Block blockWithOneTx = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(), coinbaseA, null, minerFee, 0, txValue, cowKey);
        blocks.add(blockWithOneTx);
        blocks.addAll(createSimpleBlocks(blockWithOneTx, 9));

        BlockExecutor blockExecutor = new BlockExecutor(config, blockchain.getRepository(), null, blockchain.getBlockStore(), null);

        for (Block b : blocks) {
            blockExecutor.executeAndFillAll(b, blockchain.getBestBlock());
            blockchain.tryToConnect(b);
        }

        Repository repository = blockchain.getRepository();

        assertEquals(cowInitialBalance.subtract(Coin.valueOf(minerFee+txValue)), repository.getAccountState(new RskAddress(cowAddress)).getBalance());
        assertEquals(Coin.valueOf(minerFee), repository.getAccountState(PrecompiledContracts.REMASC_ADDR).getBalance());
        assertNull(repository.getAccountState(coinbaseA));
        assertNull(repository.getAccountState(remascConfig.getRskLabsAddress()));

        RemascStorageProvider remasceStorageProvider = getRemascStorageProvider(blockchain);
        assertEquals(Coin.ZERO, remasceStorageProvider.getRewardBalance());
        assertEquals(Coin.ZERO, remasceStorageProvider.getBurnedBalance());
        assertEquals(0, remasceStorageProvider.getSiblings().size());

        // A hacker trying to screw the system creates a tx to remasc and a fool/accomplice miner includes that tx in a block
        Transaction tx = new Transaction(
                Coin.valueOf(1).getBytes(),
                Coin.valueOf(1).getBytes(),
                Coin.valueOf(minerFee).getBytes(),
                PrecompiledContracts.REMASC_ADDR.getBytes() ,
                Coin.valueOf(txValue*2).getBytes(),
                null,
                config.getBlockchainConfig().getCommonConstants().getChainId());
        tx.sign(cowKey.getPrivKeyBytes());
        Block newblock = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1),
                PegTestUtils.createHash3(), TestUtils.randomAddress(), null, null, tx);
        blockExecutor.executeAndFillAll(newblock, blockchain.getBestBlock());
        blockchain.tryToConnect(newblock);

        repository = blockchain.getRepository();

        // Check "hack" tx makes no changes to the remasc state, sender pays fees, and value is added to remasc account balance
        assertEquals(cowInitialBalance.subtract(Coin.valueOf(minerFee+txValue+minerFee)), repository.getAccountState(new RskAddress(cowAddress)).getBalance());
        long blockReward = minerFee/remascConfig.getSyntheticSpan();
        long originalBlockReward = blockReward;
        assertEquals(Coin.valueOf(minerFee+minerFee-blockReward), repository.getAccountState(PrecompiledContracts.REMASC_ADDR).getBalance());
        long rskReward = blockReward/remascConfig.getRskLabsDivisor();
        assertEquals(Coin.valueOf(rskReward), repository.getAccountState(remascConfig.getRskLabsAddress()).getBalance());
        blockReward -= rskReward;
        long federationReward = blockReward / remascConfig.getFederationDivisor();
        assertEquals(33, federationReward);
        blockReward -= federationReward;
        assertEquals(Coin.valueOf(blockReward), repository.getAccountState(coinbaseA).getBalance());

        Coin expectedRewardBalance = Coin.valueOf(minerFee - originalBlockReward);
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(blockchain), expectedRewardBalance, Coin.ZERO, 0L);
        this.validateFederatorsBalanceIsCorrect(blockchain.getRepository(), federationReward);
    }

    @Test
    public void processMinersFeesFromTxInvokedByAnotherContract() {
        BlockChainBuilder builder = new BlockChainBuilder();
        Blockchain blockchain = builder.setTesting(true).setGenesis(genesisBlock).build();

        List<Block> blocks = createSimpleBlocks(genesisBlock, 4);
        Block blockWithOneTx = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1), PegTestUtils.createHash3(), coinbaseA, null, minerFee, 0, txValue, cowKey);
        blocks.add(blockWithOneTx);
        blocks.addAll(createSimpleBlocks(blockWithOneTx, 9));

        BlockExecutor blockExecutor = new BlockExecutor(config, blockchain.getRepository(), null, blockchain.getBlockStore(), null);

        for (Block b : blocks) {
            blockExecutor.executeAndFillAll(b, blockchain.getBestBlock());
            b.seal();
            blockchain.tryToConnect(b);
        }

        Repository repository = blockchain.getRepository();

        assertEquals(cowInitialBalance.subtract(Coin.valueOf(minerFee+txValue)), repository.getAccountState(new RskAddress(cowAddress)).getBalance());
        assertEquals(Coin.valueOf(minerFee), repository.getAccountState(PrecompiledContracts.REMASC_ADDR).getBalance());
        assertNull(repository.getAccountState(coinbaseA));
        assertNull(repository.getAccountState(remascConfig.getRskLabsAddress()));

        RemascStorageProvider remasceStorageProvider = getRemascStorageProvider(blockchain);
        assertEquals(Coin.ZERO, remasceStorageProvider.getRewardBalance());
        assertEquals(Coin.ZERO, remasceStorageProvider.getBurnedBalance());
        assertEquals(0, remasceStorageProvider.getSiblings().size());

        // A hacker trying to screw the system creates a contracts that calls remasc and a fool/accomplice miner includes that tx in a block
//        Contract code
//        pragma solidity ^0.4.3;
//        contract RemascHacker {
//
//            function()
//            {
//                address remasc = 0x0000000000000000000000000000000001000008;
//                remasc.call();
//            }
//        }
        long txCreateContractGasLimit = 53755 + 32000;
        Transaction txCreateContract = new Transaction(
                Coin.valueOf(1).getBytes(),
                Coin.valueOf(1).getBytes(),
                Coin.valueOf(txCreateContractGasLimit).getBytes(),
                null ,
                Coin.ZERO.getBytes(),
                Hex.decode("6060604052346000575b6077806100176000396000f30060606040525b3460005760495b6000600890508073ffffffffffffffffffffffffffffffffffffffff166040518090506000604051808303816000866161da5a03f1915050505b50565b0000a165627a7a7230582036692fbb1395da1688af0189be5b0ac18df3d93a2402f4fc8f927b31c1baa2460029"),
                config.getBlockchainConfig().getCommonConstants().getChainId());
        txCreateContract.sign(cowKey.getPrivKeyBytes());
        long txCallRemascGasLimit = 21828;
        Transaction txCallRemasc = new Transaction(
                Coin.valueOf(2).getBytes(),
                Coin.valueOf(1).getBytes(),
                Coin.valueOf(txCallRemascGasLimit).getBytes(),
                Hex.decode("da7ce79725418f4f6e13bf5f520c89cec5f6a974") ,
                Coin.ZERO.getBytes(),
                null,
                config.getBlockchainConfig().getCommonConstants().getChainId());
        txCallRemasc.sign(cowKey.getPrivKeyBytes());

        Block newblock = RemascTestRunner.createBlock(this.genesisBlock, blocks.get(blocks.size()-1),
                PegTestUtils.createHash3(), TestUtils.randomAddress(), null, null,
                txCreateContract, txCallRemasc);
        blockExecutor.executeAndFillAll(newblock, blockchain.getBestBlock());
        newblock.seal();
        blockchain.tryToConnect(newblock);

        repository = blockchain.getRepository();

        // Check "hack" tx makes no changes to the remasc state, sender pays fees, and value is added to remasc account balance
        assertEquals(cowInitialBalance.subtract(Coin.valueOf(txCreateContractGasLimit+txCallRemascGasLimit+txValue+minerFee)), repository.getAccountState(new RskAddress(cowAddress)).getBalance());
        long blockReward = minerFee/remascConfig.getSyntheticSpan();
        long originalBlockReward = blockReward;
        long rskReward = blockReward/remascConfig.getRskLabsDivisor();
        assertEquals(Coin.valueOf(rskReward), repository.getAccountState(remascConfig.getRskLabsAddress()).getBalance());
        blockReward -= rskReward;
        long federationReward = blockReward / remascConfig.getFederationDivisor();
        assertEquals(33, federationReward);
        blockReward -= federationReward;
        assertEquals(Coin.valueOf(blockReward), repository.getAccountState(coinbaseA).getBalance());

        Coin expectedRewardBalance = Coin.valueOf(minerFee - originalBlockReward);
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(blockchain), expectedRewardBalance, Coin.ZERO, 0L);
        this.validateFederatorsBalanceIsCorrect(blockchain.getRepository(), federationReward);
    }

    @Test
    public void siblingIncludedOneBlockLater() {
        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock);

        List<SiblingElement> siblings = Lists.newArrayList(new SiblingElement(5, 7, this.minerFee));

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(this.minerFee)
                .initialHeight(15).siblingElements(siblings).txSigningKey(this.cowKey);

        testRunner.start();

        Blockchain blockchain = testRunner.getBlockChain();

        Block blockAtHeightSeven = blockchain.getBlockByNumber(8);
        assertEquals(Coin.valueOf(1680L - 17L), testRunner.getAccountBalance(blockAtHeightSeven.getCoinbase()));

        Block blockAtHeightFiveMainchain = blockchain.getBlockByNumber(6);
        assertEquals(Coin.valueOf(7560L - 76L), testRunner.getAccountBalance(blockAtHeightFiveMainchain.getCoinbase()));

        Block blockAtHeightFiveSibling = testRunner.getAddedSiblings().get(0);
        assertEquals(Coin.valueOf(7182L - 72L), testRunner.getAccountBalance(blockAtHeightFiveSibling.getCoinbase()));

        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(blockchain), Coin.valueOf(84000L), Coin.valueOf(378L - 3L), 0L);
    }

    @Test
    public void oneSiblingIncludedOneBlockLaterAndAnotherIncludedRightAfter() {
        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock);

        List<SiblingElement> siblings = Lists.newArrayList(new SiblingElement(5, 6, this.minerFee), new SiblingElement(5, 7, this.minerFee));

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(this.minerFee)
                .initialHeight(15).siblingElements(siblings).txSigningKey(this.cowKey);

        testRunner.start();

        Blockchain blockchain = testRunner.getBlockChain();

        Block blockAtHeightSix = blockchain.getBlockByNumber(7);
        assertEquals(Coin.valueOf(840L - 9L), testRunner.getAccountBalance(blockAtHeightSix.getCoinbase()));

        Block blockAtHeightSeven = blockchain.getBlockByNumber(8);
        assertEquals(Coin.valueOf(840L - 9L), testRunner.getAccountBalance(blockAtHeightSeven.getCoinbase()));

        Block blockAtHeightFiveMainchain = blockchain.getBlockByNumber(6);
        assertEquals(Coin.valueOf(5040L - 51L), testRunner.getAccountBalance(blockAtHeightFiveMainchain.getCoinbase()));

        Block blockAtHeightFiveFirstSibling = testRunner.getAddedSiblings().get(0);
        assertEquals(Coin.valueOf(5040L - 51L), testRunner.getAccountBalance(blockAtHeightFiveFirstSibling.getCoinbase()));

        Block blockAtHeightFiveSecondSibling = testRunner.getAddedSiblings().get(1);
        assertEquals(Coin.valueOf(4788L - 48L), testRunner.getAccountBalance(blockAtHeightFiveSecondSibling.getCoinbase()));

        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(blockchain), Coin.valueOf(84000L), Coin.valueOf(252L), 0L);
    }

    @Test
    public void siblingIncludedSevenBlocksLater() {
        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock);

        List<SiblingElement> siblings = Lists.newArrayList(new SiblingElement(5, 12, this.minerFee));

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(this.minerFee)
                .initialHeight(15).siblingElements(siblings).txSigningKey(this.cowKey);

        testRunner.start();

        Blockchain blockchain = testRunner.getBlockChain();

        Block blockAtHeightTwelve = blockchain.getBlockByNumber(13);
        assertEquals(Coin.valueOf(1680L - 17), testRunner.getAccountBalance(blockAtHeightTwelve.getCoinbase()));

        Block blockAtHeightFiveMainchain = blockchain.getBlockByNumber(6);
        assertEquals(Coin.valueOf(7560L - 76), testRunner.getAccountBalance(blockAtHeightFiveMainchain.getCoinbase()));

        Block blockAtHeightFiveSibling = testRunner.getAddedSiblings().get(0);
        assertEquals(Coin.valueOf(5292L - 53), testRunner.getAccountBalance(blockAtHeightFiveSibling.getCoinbase()));

        Coin remascCurrentBalance = testRunner.getAccountBalance(PrecompiledContracts.REMASC_ADDR);
        // TODO review value -22
        assertEquals(Coin.valueOf(296268L - 22), remascCurrentBalance);

        // TODO review value -22
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(blockchain), Coin.valueOf(84000L), Coin.valueOf(2268L - 22), 0L);
    }

    @Test
    public void siblingsFeeForMiningBlockMustBeRoundedAndTheRoundedSurplusBurned() {
        BlockChainBuilder builder = new BlockChainBuilder()
                .setTesting(true)
                .setGenesis(genesisBlock);
        long minerFee = 21000;

        List<SiblingElement> siblings = Lists.newArrayList();
        for (int i = 0; i < 9; i++) {
            siblings.add(new SiblingElement(5, 6, minerFee));
        }

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(siblings).txSigningKey(this.cowKey);

        testRunner.start();

        Blockchain blockchain = testRunner.getBlockChain();

        Block blockAtHeightSix = blockchain.getBlockByNumber(7);
        // TODO Review value 18
        assertEquals(Coin.valueOf(1674 - 18), testRunner.getAccountBalance(blockAtHeightSix.getCoinbase()));

        Block blockAtHeightSeven = blockchain.getBlockByNumber(8);
        assertNull(testRunner.getAccountBalance(blockAtHeightSeven.getCoinbase()));

        Block blockAtHeightFiveMainchain = blockchain.getBlockByNumber(6);
        assertEquals(Coin.valueOf(1512 - 16), testRunner.getAccountBalance(blockAtHeightFiveMainchain.getCoinbase()));

        Block blockAtHeightFiveFirstSibling = testRunner.getAddedSiblings().get(0);
        assertEquals(Coin.valueOf(1512 - 16), testRunner.getAccountBalance(blockAtHeightFiveFirstSibling.getCoinbase()));

        Block blockAtHeightFiveSecondSibling = testRunner.getAddedSiblings().get(1);

        assertEquals(Coin.valueOf(1512 - 16), testRunner.getAccountBalance(blockAtHeightFiveSecondSibling.getCoinbase()));

        // TODO Review value 16 (original value 6)
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(blockchain), Coin.valueOf(84000), Coin.valueOf(16), 0L);

    }

    @Test
    public void unclesPublishingFeeMustBeRoundedAndTheRoundedSurplusBurned() {
        BlockChainBuilder builder = new BlockChainBuilder().setTesting(true).setGenesis(genesisBlock);
        long minerFee = 21000;

        List<SiblingElement> siblings = Lists.newArrayList();
        for (int i = 0; i < 10; i++) {
            siblings.add(new SiblingElement(5, 6, minerFee));
        }

        RemascTestRunner testRunner = new RemascTestRunner(builder, this.genesisBlock).txValue(txValue).minerFee(minerFee)
                .initialHeight(15).siblingElements(siblings).txSigningKey(this.cowKey);

        testRunner.start();

        Blockchain blockchain = testRunner.getBlockChain();

        Block blockAtHeightSix = blockchain.getBlockByNumber(7);
        // TODO review value -20
        assertEquals(Coin.valueOf(1680 - 20), testRunner.getAccountBalance(blockAtHeightSix.getCoinbase()));

        Block blockAtHeightFiveMainchain = blockchain.getBlockByNumber(6);
        assertEquals(Coin.valueOf(1374 - 14), testRunner.getAccountBalance(blockAtHeightFiveMainchain.getCoinbase()));

        Block blockAtHeightFiveFirstSibling = testRunner.getAddedSiblings().get(0);
        assertEquals(Coin.valueOf(1374 - 14), testRunner.getAccountBalance(blockAtHeightFiveFirstSibling.getCoinbase()));

        Block blockAtHeightFiveSecondSibling = testRunner.getAddedSiblings().get(1);
        assertEquals(Coin.valueOf(1374 - 14), testRunner.getAccountBalance(blockAtHeightFiveSecondSibling.getCoinbase()));

        Block blockAtHeightFiveThirdSibling = testRunner.getAddedSiblings().get(2);
        assertEquals(Coin.valueOf(1374 - 14), testRunner.getAccountBalance(blockAtHeightFiveThirdSibling.getCoinbase()));

        // Review value +6
        this.validateRemascsStorageIsCorrect(this.getRemascStorageProvider(blockchain), Coin.valueOf(84000), Coin.valueOf(6 + 6), 0);
    }

    private List<Block> createSimpleBlocks(Block parent, int size) {
        List<Block> chain = new ArrayList<>();

        while (chain.size() < size) {
            Block newblock = RemascTestRunner.createBlock(this.genesisBlock, parent, PegTestUtils.createHash3(), TestUtils.randomAddress(), null, null);
            chain.add(newblock);
            parent = newblock;
        }

        return chain;
    }

    private HashMap<byte[], Coin> getAccountsWithExpectedBalance(List<Long> otherAccountsBalance) {
        HashMap<byte[], Coin> accountsWithExpectedBalance = new HashMap<>();

        for(int currentAccount = 0; currentAccount < accountsAddressesUpToD.size(); currentAccount++) {
            accountsWithExpectedBalance.put(accountsAddressesUpToD.get(currentAccount), otherAccountsBalance.get(currentAccount) == null ? null : Coin.valueOf(otherAccountsBalance.get(currentAccount)));
        }

        return accountsWithExpectedBalance;
    }

    private void validateFederatorsBalanceIsCorrect(Repository repository, long federationReward) {
        RemascFederationProvider provider = new RemascFederationProvider(config, repository, null);

        int nfederators = provider.getFederationSize();

        Coin federatorBalance = Coin.valueOf(federationReward / nfederators);

        for (int k = 0; k < nfederators; k++) {
            assertEquals(federatorBalance, repository.getBalance(provider.getFederatorAddress(k)));
        }
    }

    private void validateAccountsCurrentBalanceIsCorrect(Repository repository, Coin cowBalance,
                                                         Long remascBalance, Long rskBalance,
                                                         HashMap<byte[], Coin> otherAccountsBalance) {

        assertEquals(cowBalance, RemascTestRunner.getAccountBalance(repository, cowAddress));

        Coin remascExpectedBalance = Coin.valueOf(remascBalance);
        Coin remascActualBalance = RemascTestRunner.getAccountBalance(repository, PrecompiledContracts.REMASC_ADDR);
        assertEquals(remascExpectedBalance, remascActualBalance);

        Coin rskExpectedBalance = rskBalance == null ? null : Coin.valueOf(rskBalance);
        assertEquals(rskExpectedBalance, RemascTestRunner.getAccountBalance(repository, remascConfig.getRskLabsAddress()));

        for(Map.Entry<byte[], Coin> entry : otherAccountsBalance.entrySet()) {
            Coin actualBalance = RemascTestRunner.getAccountBalance(repository, entry.getKey());
            assertEquals("Failed for: " + Hex.toHexString(entry.getKey()), entry.getValue(), actualBalance);
        }
    }

    private void validateRemascsStorageIsCorrect(RemascStorageProvider provider, Coin expectedRewardBalance, Coin expectedBurnedBalance, long expectedSiblingsSize) {
        assertEquals(expectedRewardBalance, provider.getRewardBalance());
        assertEquals(expectedBurnedBalance, provider.getBurnedBalance());
        assertEquals(expectedSiblingsSize, provider.getSiblings().size());
    }

    private RemascStorageProvider getRemascStorageProvider(Blockchain blockchain) {
        return new RemascStorageProvider(blockchain.getRepository(), PrecompiledContracts.REMASC_ADDR);
    }
}
