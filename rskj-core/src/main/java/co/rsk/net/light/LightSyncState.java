package co.rsk.net.light;public class LightSyncState{private final co.rsk.net.light.CheckingBestHeaderLightSyncState checkingBestHeaderLightSyncState;	public LightSyncState(co.rsk.net.light.CheckingBestHeaderLightSyncState checkingBestHeaderLightSyncState)	{		this.checkingBestHeaderLightSyncState = checkingBestHeaderLightSyncState;	}public void newBlockHeaderMessage(java.util.List<org.ethereum.core.BlockHeader> blockHeaders) {

        if (blockHeaders.isEmpty()) {
            return;
        }

        //TODO: Mechanism of disconnecting when peer gives bad information
        for (org.ethereum.core.BlockHeader h : blockHeaders) {
            if (!checkingBestHeaderLightSyncState.getBlockHeaderValidationRule().isValid(h)) {
                return;
            }
        }

        checkingBestHeaderLightSyncState.getLightPeer().receivedBlock(blockHeaders);
    }}