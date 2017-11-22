package co.rsk.remasc;

import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeSupport;
import org.ethereum.core.Repository;
import org.ethereum.db.BlockStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;

/**
 * Created by ajlopez on 14/11/2017.
 */
public class RemascFederationProvider {
    private BridgeSupport bridgeSupport;

    public RemascFederationProvider(Repository repository) throws IOException, BlockStoreException {
        this.bridgeSupport = new BridgeSupport(repository, PrecompiledContracts.BRIDGE_ADDR, null, null, null, null);
    }

    public int getFederationSize() throws IOException {
        return this.bridgeSupport.getFederationSize().intValue();
    }
}
