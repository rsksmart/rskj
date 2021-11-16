package co.rsk.peg;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BridgeUtilsLegacyTest {

    private ActivationConfig.ForBlock activations;
    private BridgeConstants bridgeConstants;
    private NetworkParameters networkParameters;

    @Before
    public void setup() {
        activations = mock(ActivationConfig.ForBlock.class);
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @Test
    public void calculatePegoutTxSize_before_rskip_271() {
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        List<BtcECKey> keys = createBtcECKeys(13);
        Federation federation = new Federation(
                FederationMember.getFederationMembersFromKeys(keys),
                Instant.now(),
                0,
                networkParameters
        );

        int pegoutTxSize = BridgeUtilsLegacy.calculatePegoutTxSize(activations, federation, 2, 2);

        // The difference between the calculated size and a real tx size should be smaller than 1% in any direction
        int origTxSize = 2076; // Data for 2 inputs, 2 outputs Based on Pegouts From Blockchain.info Explorer
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .01;

        Assert.assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test(expected = DeprecatedMethodCallException.class)
    public void calculatePegoutTxSize_after_rskip_271() {
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        List<BtcECKey> keys = createBtcECKeys(13);
        Federation federation = new Federation(
                FederationMember.getFederationMembersFromKeys(keys),
                Instant.now(),
                0,
                networkParameters
        );

        BridgeUtilsLegacy.calculatePegoutTxSize(activations, federation, 2, 2);
    }

    private List<BtcECKey> createBtcECKeys(int keysCount) {
        List<BtcECKey> keys = new ArrayList<>();
        for (int i = 0; i < keysCount; i++) {
            keys.add(new BtcECKey());
        }
        return keys;
    }
}
