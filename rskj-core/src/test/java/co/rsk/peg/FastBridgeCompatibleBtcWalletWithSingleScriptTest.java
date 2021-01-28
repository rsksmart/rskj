package co.rsk.peg;

import static org.mockito.Mockito.mock;

import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.FastBridgeRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class FastBridgeCompatibleBtcWalletWithSingleScriptTest {
    private final Federation federation = new Federation(
        FederationTestUtils.getFederationMembers(3),
        Instant.ofEpochMilli(1000),
        0L,
        NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
    );

    private final List<Federation> federationList = Collections.singletonList(federation);

    @Test
    public void findRedeemDataFromScriptHash_null_destination_federation() {
        FastBridgeFederationInformation fastBridgeFederationInformation =
            new FastBridgeFederationInformation(null, new byte[0], new byte[0]);
        FastBridgeCompatibleBtcWalletWithSingleScript fastBridgeCompatibleBtcWalletWithSingleScript =
            new FastBridgeCompatibleBtcWalletWithSingleScript(
                mock(Context.class),
                federationList,
                fastBridgeFederationInformation);

        Assert.assertNull(fastBridgeCompatibleBtcWalletWithSingleScript.findRedeemDataFromScriptHash(
            federation.getP2SHScript().getPubKeyHash()));
    }

    @Test
    public void findRedeemDataFromScriptHash_with_fastBridgeInformation() {
        byte[] fastBridgeScriptHash = new byte[]{(byte)0x22};
        FastBridgeFederationInformation fastBridgeFederationInformation =
            new FastBridgeFederationInformation(
                PegTestUtils.createHash3(2),
                federation.getP2SHScript().getPubKeyHash(),
                fastBridgeScriptHash);

        FastBridgeCompatibleBtcWalletWithSingleScript fastBridgeCompatibleBtcWalletWithSingleScript =
            new FastBridgeCompatibleBtcWalletWithSingleScript(
                mock(Context.class),
                federationList,
                fastBridgeFederationInformation);

        RedeemData redeemData = fastBridgeCompatibleBtcWalletWithSingleScript.findRedeemDataFromScriptHash(
            federation.getP2SHScript().getPubKeyHash());

        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            federation.getRedeemScript(), Sha256Hash.wrap(fastBridgeFederationInformation.getDerivationHash().getBytes())
        );

        Assert.assertNotNull(redeemData);
        Assert.assertEquals(fastBridgeRedeemScript, redeemData.redeemScript);
    }
}
