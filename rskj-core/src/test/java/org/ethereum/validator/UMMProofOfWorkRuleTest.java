package org.ethereum.validator;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.blockchain.utils.BlockMiner;
import co.rsk.config.TestSystemProperties;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.crypto.HashUtil;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class UMMProofOfWorkRuleTest {
    private final ActivationConfig activationConfig;
    private final Constants networkConstants;
    private ProofOfWorkRule rule;
    private BlockFactory blockFactory;
    protected  TestSystemProperties config;

    public UMMProofOfWorkRuleTest() {
        //TestSystemProperties config

        TestSystemProperties config = new TestSystemProperties() {
            @Override
            public ActivationConfig getActivationConfig() {
                return ActivationConfigsForTest.all();
            }

            @Override
            public String toString() {
                return "future";
            }
        };
        this.rule = new ProofOfWorkRule(config).setFallbackMiningEnabled(false);
        this.activationConfig = config.getActivationConfig();
        this.networkConstants = config.getNetworkConstants();
        this.blockFactory = new BlockFactory(activationConfig);
    }
    public byte[] getHashRootForMergedMining(byte[] leftHashForMergedMining,byte[] mergeMiningRightHash) {
        byte[] left = leftHashForMergedMining;

        byte[] leftRight = org.bouncycastle.util.Arrays.concatenate(left,mergeMiningRightHash);
        byte[] root256 = HashUtil.keccak256(leftRight);
        return root256;
    }
    @Test
    public void test_1() {
        // mined block
        byte[] mergeMiningRightHash =new byte[]{1,2,3,4,5,6,7,8,9,0,1,2,3,4,5,6,7,8,9,0};
        BlockGenerator bg = new BlockGenerator(networkConstants, activationConfig);
        bg.setMergeMiningRightHash(mergeMiningRightHash);
        Block b = new BlockMiner(activationConfig).mineBlock(bg.getBlock(1));
        assertTrue(rule.isValid(b));
        byte[] encodedBlock = b.getHeader().getEncoded(false, false);
        byte[] headerHashForMergedMining = HashUtil.keccak256(encodedBlock);
        byte[] newRoot= getHashRootForMergedMining(headerHashForMergedMining,mergeMiningRightHash);
        byte[] rootFromHeader =b.getHashForMergedMining();
        assertArrayEquals(rootFromHeader,newRoot);
    }
}
