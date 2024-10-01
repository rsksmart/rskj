package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.NetworkParameters;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

public class NonStandardErpRedeemScriptBuilderFactory {

    private NonStandardErpRedeemScriptBuilderFactory() {}

    public static ErpRedeemScriptBuilder getNonStandardErpRedeemScriptBuilder(
        ActivationConfig.ForBlock activations,
        NetworkParameters networkParameters
    ) {
        if (networkIsTestnet(networkParameters) && !activations.isActive(ConsensusRule.RSKIP284)) {
            return NonStandardErpRedeemScriptBuilderHardcoded.builder();
        }
        if (!activations.isActive(ConsensusRule.RSKIP293)) {
            return NonStandardErpRedeemScriptBuilderWithCsvUnsignedBE.builder();
        }

        return NonStandardErpRedeemScriptBuilder.builder();
    }

    private static boolean networkIsTestnet(NetworkParameters networkParameters) {
        return networkParameters.getId().equals(NetworkParameters.ID_TESTNET);
    }
}
