package co.rsk.peg.federation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.script.Script;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FederationContextTest {

    private Federation activeFederation;
    private Federation retiringFederation;
    private FederationContext federationContext;

    @BeforeEach
    void setup() {
        activeFederation = buildActiveFederation();
        retiringFederation = buildRetiringFederation();
        federationContext = new FederationContext(activeFederation);
    }

    @Test
    void getActiveFederation() {
        assertEquals(activeFederation, federationContext.getActiveFederation());
    }

    @Test
    void getRetiringFederation() {
        assertTrue(federationContext.getRetiringFederation().isEmpty());
    }

    @Test
    void setRetiringFederation() {
        federationContext.setRetiringFederation(retiringFederation);

        assertTrue(federationContext.getRetiringFederation().isPresent());
        assertEquals(retiringFederation, federationContext.getRetiringFederation().get());
    }

    @Test
    void getLastRetiredFederationP2SHScript() {
        assertTrue(federationContext.getLastRetiredFederationP2SHScript().isEmpty());
    }

    @Test
    void setLastRetiredFederationP2SHScript() {
        Script lastRetiredFederationP2SHScript = retiringFederation.getP2SHScript();
        federationContext.setLastRetiredFederationP2SHScript(lastRetiredFederationP2SHScript);

        assertTrue(federationContext.getLastRetiredFederationP2SHScript().isPresent());
        assertEquals(lastRetiredFederationP2SHScript, federationContext.getLastRetiredFederationP2SHScript().get());
    }

    @Test
    void getLiveFederations_withOnlyActiveFederation() {
        List<Federation> liveFederations = federationContext.getLiveFederations();

        assertEquals(1, liveFederations.size());
        assertEquals(activeFederation, liveFederations.get(0));
    }

    @Test
    void getLiveFederations_withActiveAndRetiringFederations() {
        federationContext.setRetiringFederation(retiringFederation);
        List<Federation> liveFederations = federationContext.getLiveFederations();

        assertEquals(2, liveFederations.size());
        assertEquals(activeFederation, liveFederations.get(0));
        assertEquals(retiringFederation, liveFederations.get(1));
    }

    private Federation buildActiveFederation() {
        String[] seeds = new String[] {
            "activeFederationMember1",
            "activeFederationMember2",
            "activeFederationMember3",
            "activeFederationMember4",
            "activeFederationMember5",
            "activeFederationMember6",
            "activeFederationMember7",
            "activeFederationMember8",
            "activeFederationMember9"
        };
        List<BtcECKey> activeFederationKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(seeds, true);

        return P2shErpFederationBuilder.builder().withMembersBtcPublicKeys(activeFederationKeys).build();
    }

    private Federation buildRetiringFederation() {
        String[] seeds = new String[] {
            "retiringFederationMember1",
            "retiringFederationMember2",
            "retiringFederationMember3",
            "retiringFederationMember4",
            "retiringFederationMember5",
            "retiringFederationMember6",
            "retiringFederationMember7",
            "retiringFederationMember8",
            "retiringFederationMember9"
        };
        List<BtcECKey> retiringFederationKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(seeds, true);

        return P2shErpFederationBuilder.builder().withMembersBtcPublicKeys(retiringFederationKeys).build();
    }
}