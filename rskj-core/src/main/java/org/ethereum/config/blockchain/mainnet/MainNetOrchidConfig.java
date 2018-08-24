package org.ethereum.config.blockchain.mainnet;

import co.rsk.core.BlockDifficulty;
import org.ethereum.core.BlockHeader;

public class MainNetOrchidConfig extends MainNetAfterBridgeSyncConfig {
    @Override
    public boolean isRskip90() {
        return true;
    }

    @Override
    public boolean isRskip89() {
        return true;
    }

    @Override
    public boolean isRskip88() {
        return true;
    }

    @Override
    public boolean isRskip85() {
        return true;
    }

    @Override
    public boolean isRskip87() { return true; }

    @Override
    public boolean isRskip92() {
        return true;
    }

    @Override
    public boolean isRskip93() { return true; }
    
    @Override    
    public boolean isRskip91() {
        return true;
    }

    @Override public boolean isRskip94() { return true; }

    @Override
    public boolean isRskip98() {
        return true;
    }

    //RSKIP97
    @Override
    public BlockDifficulty calcDifficulty(BlockHeader curBlock, BlockHeader parent) {
        return getBlockDifficulty(curBlock, parent, getConstants());
    }
}
