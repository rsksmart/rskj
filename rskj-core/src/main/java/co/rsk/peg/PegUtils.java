package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.peg.bitcoin.BitcoinUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

import java.util.List;
import java.util.Optional;

public class PegUtils {
    private PegUtils() { }

    public static PegTxType getTransactionTypeUsingPegoutIndex(
        ActivationConfig.ForBlock activations,
        BridgeStorageProvider provider,
        Wallet liveFederationsWallet,
        BtcTransaction btcTransaction
    ) {
        if (!activations.isActive(ConsensusRule.RSKIP379)){
            throw new IllegalStateException("Can't call this method before RSKIP379 activation.");
        }

        List<TransactionOutput> liveFederationOutputs = btcTransaction.getWalletOutputs(liveFederationsWallet);

        Optional<Sha256Hash> inputSigHash = BitcoinUtils.getFirstInputSigHash(btcTransaction);
        if (inputSigHash.isPresent() && provider.hasPegoutTxSigHash(inputSigHash.get())){
            return PegTxType.PEGOUT_OR_MIGRATION;
        } else if (!liveFederationOutputs.isEmpty()){
            return PegTxType.PEGIN;
        } else {
            return PegTxType.UNKNOWN;
        }
    }

    public static boolean isAnyUTXOAmountBelowMinimum(
        Coin minimumPegInTxValue, BtcTransaction btcTx, Wallet wallet) {
        return PegUtilsLegacy.isAnyUTXOAmountBelowMinimum(minimumPegInTxValue, btcTx, wallet);
    }

    public static PegTxType getTransactionType(
        ActivationConfig.ForBlock activations,
        BridgeStorageProvider provider,
        Federation activeFederation,
        Federation retiringFederation,
        Address oldFederationAddress,
        Wallet liveFederationsWallet,
        Coin minimumPeginTxValue,
        BtcTransaction btcTransaction,
        boolean shouldUsePegoutTxIndexMechanism
    ) {
        if (activations.isActive(ConsensusRule.RSKIP379) && shouldUsePegoutTxIndexMechanism){
            return getTransactionTypeUsingPegoutIndex(
                activations,
                provider,
                liveFederationsWallet,
                btcTransaction
            );
        } else {
            Script retiredFederationP2SHScript = provider.getLastRetiredFederationP2SHScript().orElse(null);
            return PegUtilsLegacy.getTransactionType(
                btcTransaction,
                activeFederation,
                retiringFederation,
                retiredFederationP2SHScript,
                oldFederationAddress,
                activations,
                minimumPeginTxValue,
                liveFederationsWallet
            );
        }
    }
}
