package co.rsk.peg;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ErpFederationTest {
    private ErpFederation federation;

    // ERP federation keys
    private final BtcECKey ecKey4 = BtcECKey.fromPrivate(BigInteger.valueOf(400));
    private final BtcECKey ecKey5 = BtcECKey.fromPrivate(BigInteger.valueOf(500));
    private final BtcECKey ecKey6 = BtcECKey.fromPrivate(BigInteger.valueOf(600));
    private final List<BtcECKey> erpFedKeys = Arrays.asList(ecKey4, ecKey5, ecKey6);

    @Before
    public void createErpFederation() {
        federation = new ErpFederation(
            FederationTestUtils.getFederationMembersFromPks(100, 200, 300),
            ZonedDateTime.parse("2017-06-10T02:30:00Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            erpFedKeys,
            5063
        );
    }

    @Test
    public void getErpPubKeys() {
        Assert.assertEquals(erpFedKeys, federation.getErpPubKeys());
    }

    @Test
    public void getActivationDelay() {
        Assert.assertEquals(5063, federation.getActivationDelay());
    }

    @Test
    public void getRedeemScript() {
        Script redeemScript = federation.getRedeemScript();
        Assert.assertEquals(17, redeemScript.getChunks().size());

        String expectedProgram = "64522102ed3bace23c5e17652e174c835fb72bf53ee306b3406a26890221b4c"
            + "ef7500f88210385a7b790fc9d962493788317e4874a4ab07f1e9c78c773c47f2f6c96df756f052103c"
            + "d5a3be41717d65683fe7a9de8ae5b4b8feced69f26a8b55eeefbcc2e74b75fb53670213c7b27552210"
            + "2cfd70505faacd3caf4419000bf4b6ab9e7dc2e4bcf43bbcaa550839cf4713b42210314a4b6e04384d"
            + "abd15f1a3c8b0beeb6c1328213abb7232407340c277ad792a3a2103d902ff7196ddc842ef5b4ea5d0a"
            + "a17608e9b7f5f9a964ba1281cd432a7abe2e95368ae";

        Assert.assertEquals(expectedProgram, Hex.toHexString(redeemScript.getProgram()));
    }


    @Test
    public void getAddress() {
        String fedAddress = federation.getAddress().toBase58();
        String expectedAddress = "2N21gL6qfe4DnNktDkeAXtmLRmq8uKfwZMq";

        Assert.assertEquals(expectedAddress, fedAddress);
    }
}
