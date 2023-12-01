package co.rsk.peg;

import co.rsk.bitcoinj.core.NetworkParameters;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

public class NonStandardErpFederationContextFactory {

    private NonStandardErpFederationContextFactory() {}
    
    public static ErpFederationContext getNonStandardErpFederationContext(
        ActivationConfig.ForBlock activations,
        NetworkParameters networkParameters) {

        if (networkIsTestnet(networkParameters) && !activations.isActive(ConsensusRule.RSKIP284)) {
            return new NonStandardErpFederationContextHardcoded();
        }
        if (!activations.isActive(ConsensusRule.RSKIP293)) {
            return new NonStandardErpFederationContextWithCsvUnsignedBE();
        }
        return new NonStandardErpFederationContext();
    }

    private static boolean networkIsTestnet(NetworkParameters networkParameters) {
        return networkParameters.getId().equals(NetworkParameters.ID_TESTNET);
    }
}
