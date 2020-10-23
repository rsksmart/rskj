package co.rsk.peg;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.RedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

public class FastBridgeCompatibleBtcWalletTest {
    private final Federation federation = new Federation(
        FederationTestUtils.getFederationMembers(3),
        Instant.ofEpochMilli(1000),
        0L,
        NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
    );

    private final List<Federation> federationList = Collections.singletonList(federation);
    private final Sha256Hash derivationArgumentsHash = Sha256Hash.of(new byte[]{1});
    private final FastBridgeFederationInformation fastBridgeFederationInformation =
        new FastBridgeFederationInformation(derivationArgumentsHash,
            federation.getP2SHScript().getPubKeyHash());

    @Test
    public void findRedeemDataFromScriptHash_with_no_fastBridgeInformation_in_storage_call_super() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFastBridgeFederationInformation(
            federation.getP2SHScript().getPubKeyHash())).thenReturn(Optional.empty()
        );

        FastBridgeCompatibleBtcWallet fastBridgeCompatibleBtcWallet = new FastBridgeCompatibleBtcWallet(
            mock(Context.class), federationList, provider);

        RedeemData redeemData = fastBridgeCompatibleBtcWallet.findRedeemDataFromScriptHash(
            federation.getP2SHScript().getPubKeyHash());

        Assert.assertNotNull(redeemData);
        Assert.assertEquals(federation.getRedeemScript(), redeemData.redeemScript);
    }

    @Test
    public void findRedeemDataFromScriptHash_with_fastBridgeInformation_in_storage() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFastBridgeFederationInformation(
            federation.getP2SHScript().getPubKeyHash())).thenReturn(
                Optional.of(fastBridgeFederationInformation)
        );

        FastBridgeCompatibleBtcWallet fastBridgeCompatibleBtcWallet = new FastBridgeCompatibleBtcWallet(
            mock(Context.class), federationList, provider
        );

        RedeemData redeemData = fastBridgeCompatibleBtcWallet.findRedeemDataFromScriptHash(
            federation.getP2SHScript().getPubKeyHash());

        Script expectedRedeemScript = RedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            federation.getRedeemScript(), derivationArgumentsHash);

        Assert.assertNotNull(redeemData);
        Assert.assertEquals(expectedRedeemScript, redeemData.redeemScript);
    }

    @Test
    public void findRedeemDataFromScriptHash_null_destination_federation() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFastBridgeFederationInformation(any())).thenReturn(
            Optional.of(fastBridgeFederationInformation)
        );

        FastBridgeCompatibleBtcWallet fastBridgeCompatibleBtcWallet = new FastBridgeCompatibleBtcWallet(
            mock(Context.class), federationList, provider
        );

        Assert.assertNull(fastBridgeCompatibleBtcWallet.findRedeemDataFromScriptHash(new byte[]{1}));
    }
}
