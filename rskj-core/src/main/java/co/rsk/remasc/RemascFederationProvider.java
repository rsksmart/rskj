package co.rsk.remasc;

import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.peg.BridgeSupport;
import org.ethereum.crypto.ECKey;

import java.io.IOException;

/**
 * Created by ajlopez on 14/11/2017.
 */
public class RemascFederationProvider {
    private BridgeSupport bridgeSupport;

    public RemascFederationProvider(BridgeSupport bridgeSupport) throws IOException, BlockStoreException {
        this.bridgeSupport = bridgeSupport;
    }

    public int getFederationSize() throws IOException {
        return this.bridgeSupport.getFederationSize().intValue();
    }

    public byte[] getFederatorAddress(int n) throws IOException {
        byte[] publicKey = this.bridgeSupport.getFederatorPublicKey(n);
        return ECKey.fromPublicOnly(publicKey).getAddress();
    }
}
