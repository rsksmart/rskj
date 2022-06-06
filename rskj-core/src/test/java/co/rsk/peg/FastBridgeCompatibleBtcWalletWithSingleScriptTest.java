package co.rsk.peg;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.FastBridgeErpRedeemScriptParser;
import co.rsk.bitcoinj.script.FastBridgeRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import co.rsk.peg.utils.ScriptBuilderWrapper;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class FastBridgeCompatibleBtcWalletWithSingleScriptTest {
    private static final List<BtcECKey> erpFedKeys = Arrays.stream(new String[]{
        "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
        "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
        "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

    private Federation federation;
    private ErpFederation erpFederation;
    private List<Federation> federationList;
    private List<Federation> erpFederationList;

    @Before
    public void setup() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        ScriptBuilderWrapper scriptBuilderWrapper = ScriptBuilderWrapper.getInstance();

        federation = new Federation(
            FederationTestUtils.getFederationMembers(3),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
                scriptBuilderWrapper
        );

        erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembers(3),
            Instant.ofEpochMilli(1000),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            erpFedKeys,
            5063,
            activations,
                scriptBuilderWrapper
        );

        federationList = Collections.singletonList(federation);
        erpFederationList = Collections.singletonList(erpFederation);
    }

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

    @Test
    public void findRedeemDataFromScriptHash_with_fastBridgeInformation_and_erp_federation() {
        byte[] fastBridgeScriptHash = new byte[]{(byte)0x22};
        FastBridgeFederationInformation fastBridgeFederationInformation =
            new FastBridgeFederationInformation(
                PegTestUtils.createHash3(2),
                erpFederation.getP2SHScript().getPubKeyHash(),
                fastBridgeScriptHash);

        FastBridgeCompatibleBtcWalletWithSingleScript fastBridgeCompatibleBtcWalletWithSingleScript =
            new FastBridgeCompatibleBtcWalletWithSingleScript(
                mock(Context.class),
                erpFederationList,
                fastBridgeFederationInformation);

        RedeemData redeemData = fastBridgeCompatibleBtcWalletWithSingleScript.findRedeemDataFromScriptHash(
            erpFederation.getP2SHScript().getPubKeyHash());

        Script fastBridgeRedeemScript = FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
            erpFederation.getRedeemScript(),
            Sha256Hash.wrap(fastBridgeFederationInformation.getDerivationHash().getBytes())
        );

        Assert.assertNotNull(redeemData);
        Assert.assertEquals(fastBridgeRedeemScript, redeemData.redeemScript);
    }
}
