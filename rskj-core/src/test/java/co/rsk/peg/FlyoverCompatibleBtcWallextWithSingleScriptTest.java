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
import co.rsk.peg.federation.*;
import co.rsk.peg.flyover.FlyoverFederationInformation;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlyoverCompatibleBtcWallextWithSingleScriptTest {
    private static final List<BtcECKey> erpFedKeys = Arrays.stream(new String[]{
        "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
        "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
        "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

    private Federation federation;
    private ErpFederation nonStandardErpFederation;
    private List<Federation> federationList;
    private List<Federation> nonStandardErpFederationList;
    private ActivationConfig.ForBlock activations;

    @BeforeEach
    void setup() {
        // set up standard multisig federation
        List<FederationMember> fedMembers = FederationTestUtils.getFederationMembers(3);
        Instant creationTime = Instant.ofEpochMilli(1000);
        NetworkParameters btcParams = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        FederationArgs federationArgs = new FederationArgs(fedMembers, creationTime, 0L, btcParams);
        federation = FederationFactory.buildStandardMultiSigFederation(federationArgs);
        federationList = Collections.singletonList(federation);

        // set up non-standard erp federation
        activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP123)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        nonStandardErpFederation = FederationFactory.buildNonStandardErpFederation(federationArgs, erpFedKeys, 5063, activations);
        nonStandardErpFederationList = Collections.singletonList(nonStandardErpFederation);
    }

    @Test
    void findRedeemDataFromScriptHash_null_destination_federation() {
        FlyoverFederationInformation flyoverFederationInformation =
            new FlyoverFederationInformation(null, new byte[0], new byte[0]);
        FlyoverCompatibleBtcWalletWithSingleScript flyoverCompatibleBtcWalletWithSingleScript =
            new FlyoverCompatibleBtcWalletWithSingleScript(
                mock(Context.class),
                federationList,
                flyoverFederationInformation);

        Assertions.assertNull(flyoverCompatibleBtcWalletWithSingleScript.findRedeemDataFromScriptHash(
            federation.getP2SHScript().getPubKeyHash()));
    }

    @Test
    void findRedeemDataFromScriptHash_with_flyoverInformation() {
        byte[] flyoverScriptHash = new byte[]{(byte)0x22};
        FlyoverFederationInformation flyoverFederationInformation =
            new FlyoverFederationInformation(
                PegTestUtils.createHash3(2),
                federation.getP2SHScript().getPubKeyHash(),
                flyoverScriptHash);

        FlyoverCompatibleBtcWalletWithSingleScript flyoverCompatibleBtcWalletWithSingleScript =
            new FlyoverCompatibleBtcWalletWithSingleScript(
                mock(Context.class),
                federationList,
                flyoverFederationInformation);

        RedeemData redeemData = flyoverCompatibleBtcWalletWithSingleScript.findRedeemDataFromScriptHash(
            federation.getP2SHScript().getPubKeyHash());

        Script flyoverRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            federation.getRedeemScript(), Sha256Hash.wrap(flyoverFederationInformation.getDerivationHash().getBytes())
        );

        Assertions.assertNotNull(redeemData);
        Assertions.assertEquals(flyoverRedeemScript, redeemData.redeemScript);
    }

    @Test
    void findRedeemDataFromScriptHash_with_flyoverInformation_and_non_standard_erp_federation() {
        byte[] flyoverScriptHash = new byte[]{(byte)0x22};
        FlyoverFederationInformation flyoverFederationInformation =
            new FlyoverFederationInformation(
                PegTestUtils.createHash3(2),
                nonStandardErpFederation.getP2SHScript().getPubKeyHash(),
                flyoverScriptHash);

        FlyoverCompatibleBtcWalletWithSingleScript flyoverCompatibleBtcWalletWithSingleScript =
            new FlyoverCompatibleBtcWalletWithSingleScript(
                mock(Context.class),
                nonStandardErpFederationList,
                flyoverFederationInformation);

        RedeemData redeemData = flyoverCompatibleBtcWalletWithSingleScript.findRedeemDataFromScriptHash(
            nonStandardErpFederation.getP2SHScript().getPubKeyHash());

        Script flyoverRedeemScript = FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
            nonStandardErpFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverFederationInformation.getDerivationHash().getBytes())
        );

        Assertions.assertNotNull(redeemData);
        Assertions.assertEquals(flyoverRedeemScript, redeemData.redeemScript);
    }
}
