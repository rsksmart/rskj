package co.rsk.peg;

import co.rsk.bitcoinj.core.NetworkParameters;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

public class ErpRedeemScriptBuilderUtils {

    private ErpRedeemScriptBuilderUtils() {
    }

    public static ErpRedeemScriptBuilder defineNonStandardErpRedeemScriptBuilder(
        ActivationConfig.ForBlock activations,
        NetworkParameters networkParameters) {

        ErpRedeemScriptBuilder erpRedeemScriptBuilder;

        boolean networkParametersIsTestnetOrRegtest = checkIfNetworkParametersAreTestnetOrRegtest(networkParameters);

        if(!activations.isActive(ConsensusRule.RSKIP284) && networkParametersIsTestnetOrRegtest) {
            erpRedeemScriptBuilder = new NonStandardErpRedeemScriptBuilderHardcoaded();
        } else if (!activations.isActive(ConsensusRule.RSKIP293)) {
            erpRedeemScriptBuilder = new NonStandardErpRedeemScriptBuilderWithCsvUnsignedBE();
        } else erpRedeemScriptBuilder = new NonStandardErpRedeemScriptBuilder();

        return erpRedeemScriptBuilder;
    }

    public static boolean checkIfNetworkParametersAreTestnetOrRegtest(NetworkParameters networkParameters) {
        return networkParameters.getId().equals(NetworkParameters.ID_TESTNET)
            || networkParameters.getId().equals(NetworkParameters.ID_REGTEST);
    }
}
