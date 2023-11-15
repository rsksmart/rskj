package co.rsk.peg;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeConstants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

public class ErpRedeemScriptBuilderUtils {

    private ErpRedeemScriptBuilderUtils() {
    }

    public static ErpRedeemScriptBuilder defineErpRedeemScriptBuilder(
        ActivationConfig.ForBlock activations,
        BridgeConstants bridgeConstants) {

        ErpRedeemScriptBuilder erpRedeemScriptBuilder;

        NetworkParameters networkParameters = bridgeConstants.getBtcParams();
        boolean networkParametersIsTestnetOrRegtest = checkIfNetworkParameterIsTestnetOrRegtest(networkParameters);

        if(!activations.isActive(ConsensusRule.RSKIP284) && networkParametersIsTestnetOrRegtest) {
            erpRedeemScriptBuilder = new NonStandardErpRedeemScriptBuilderHardcoaded();
        } else if (!activations.isActive(ConsensusRule.RSKIP293)) {
            erpRedeemScriptBuilder = new NonStandardErpRedeemScriptBuilderWithCsvUnsignedBE();
        } else erpRedeemScriptBuilder = new NonStandardErpRedeemScriptBuilder();

        return erpRedeemScriptBuilder;
    }

    public static ErpRedeemScriptBuilder defineErpRedeemScriptBuilder(
        ActivationConfig.ForBlock activations,
        NetworkParameters networkParameters) {

        ErpRedeemScriptBuilder erpRedeemScriptBuilder;

        boolean networkParametersIsTestnetOrRegtest = checkIfNetworkParameterIsTestnetOrRegtest(networkParameters);

        if(!activations.isActive(ConsensusRule.RSKIP284) && networkParametersIsTestnetOrRegtest) {
            erpRedeemScriptBuilder = new NonStandardErpRedeemScriptBuilderHardcoaded();
        } else if (!activations.isActive(ConsensusRule.RSKIP293)) {
            erpRedeemScriptBuilder = new NonStandardErpRedeemScriptBuilderWithCsvUnsignedBE();
        } else erpRedeemScriptBuilder = new NonStandardErpRedeemScriptBuilder();

        return erpRedeemScriptBuilder;
    }

    private static boolean checkIfNetworkParameterIsTestnetOrRegtest(NetworkParameters networkParametersId) {
        return networkParametersId.getId().equals(NetworkParameters.ID_TESTNET)
            || networkParametersId.getId().equals(NetworkParameters.ID_REGTEST);
    }
}
