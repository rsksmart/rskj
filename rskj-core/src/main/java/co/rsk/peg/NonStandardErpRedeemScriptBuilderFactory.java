package co.rsk.peg;

import co.rsk.bitcoinj.core.NetworkParameters;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

public class NonStandardErpRedeemScriptBuilderFactory {

    private NonStandardErpRedeemScriptBuilderFactory() {
    }

    public static ErpRedeemScriptBuilder defineNonStandardErpRedeemScriptBuilder(
        ActivationConfig.ForBlock activations,
        NetworkParameters networkParameters) {

        ErpRedeemScriptBuilder erpRedeemScriptBuilder;

        boolean networkIsTestnet = checkIfNetworkIsTestnet(networkParameters);

        if(!activations.isActive(ConsensusRule.RSKIP284) && networkIsTestnet) {
            erpRedeemScriptBuilder = new NonStandardErpRedeemScriptBuilderHardcoded();
        } else if (!activations.isActive(ConsensusRule.RSKIP293)) {
            erpRedeemScriptBuilder = new NonStandardErpRedeemScriptBuilderWithCsvUnsignedBE();
        } else {
            erpRedeemScriptBuilder = new NonStandardErpRedeemScriptBuilder();
        }

        return erpRedeemScriptBuilder;
    }

    private static boolean checkIfNetworkIsTestnet(NetworkParameters networkParameters) {
        return networkParameters.getId().equals(NetworkParameters.ID_TESTNET);
    }
}
