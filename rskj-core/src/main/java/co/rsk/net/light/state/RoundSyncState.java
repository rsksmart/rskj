package co.rsk.net.light.state;

import co.rsk.net.light.LightPeer;
import org.ethereum.core.BlockHeader;

import java.util.List;

public class RoundSyncState implements LightSyncState {
    public RoundSyncState() {
    }

    @Override
    public void sync() {
        //Nothing to do here
    }

    @Override
    public void newBlockHeaders(LightPeer lightPeer, List<BlockHeader> blockHeaders) {
        //Nothing to do here
    }
}
