package org.ethereum.config.blockchain.mainnet;

import co.rsk.core.BlockDifficulty;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.GenesisConfig;
import org.ethereum.core.BlockHeader;

public class MainNetAfterBridgeSyncConfig extends GenesisConfig {

    public MainNetAfterBridgeSyncConfig() {
        super(Constants.mainnet());
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
