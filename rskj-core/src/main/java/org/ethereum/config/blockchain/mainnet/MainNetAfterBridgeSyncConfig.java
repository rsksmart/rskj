package org.ethereum.config.blockchain.mainnet;

import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.core.BlockDifficulty;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.GenesisConfig;
import org.ethereum.core.BlockHeader;

import java.math.BigInteger;

public class MainNetAfterBridgeSyncConfig extends GenesisConfig {

    public static class MainNetConstants extends GenesisConstants {
        private static final BigInteger DIFFICULTY_BOUND_DIVISOR = BigInteger.valueOf(50);
        private static final byte CHAIN_ID = 30;

        @Override
        public BridgeConstants getBridgeConstants() {
            return BridgeMainNetConstants.getInstance();
        }

        @Override
        public int getDurationLimit() {
            return 14;
        }

        @Override
        public BigInteger getDifficultyBoundDivisor() {
            return DIFFICULTY_BOUND_DIVISOR;
        }

        @Override
        public int getNewBlockMaxSecondsInTheFuture() {
            return 60;
        }

        @Override
        public byte getChainId() {
            return MainNetAfterBridgeSyncConfig.MainNetConstants.CHAIN_ID;
        }

    }

    public MainNetAfterBridgeSyncConfig() {
        super(new MainNetAfterBridgeSyncConfig.MainNetConstants());
    }

    protected MainNetAfterBridgeSyncConfig(Constants constants) {
        super(constants);
    }


    @Override
    public BlockDifficulty calcDifficulty(BlockHeader curBlock, BlockHeader parent) {
        // If more than 10 minutes, reset to original difficulty 0x00100000
        if (curBlock.getTimestamp() >= parent.getTimestamp() + 600) {
            return getConstants().getMinimumDifficulty();
        }

        return super.calcDifficulty(curBlock, parent);
    }
}
