package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.peg.bitcoin.BitcoinUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PegUtils {
    private PegUtils() { }

    static PegTxType getTransactionTypeUsingPegoutIndex(
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

    /**
     * Checks if all utxos sent to the <b>fedWallet</b> are equal or above the minimum pegin value.
     * If no utxo is sent to the <b>fedWallet </b>or there is at least 1 utxo sent to the <b>fedWallet</b> that is below minimum,
     * this method will return false. It will return true when all utxos sent to the <b>fedWallet</b> are above the minimum pegin value.
     *
     * @param minimumPegInTxValue
     * @param btcTx
     * @param fedWallet
     * @param activations
     * @return
     */
    public static boolean allUTXOsToFedAreAboveMinimumPeginValue(Coin minimumPegInTxValue, BtcTransaction btcTx, Wallet fedWallet, ActivationConfig.ForBlock activations) {

        if(!activations.isActive(ConsensusRule.RSKIP379)) {
            return !PegUtilsLegacy.isAnyUTXOAmountBelowMinimum(minimumPegInTxValue, btcTx, fedWallet);
        }

        List<TransactionOutput> fedUtxos = btcTx.getWalletOutputs(fedWallet);

        if(fedUtxos.isEmpty()) {
            return false;
        }

        return fedUtxos.stream()
                .allMatch(transactionOutput -> transactionOutput.getValue().compareTo(minimumPegInTxValue) >= 0);

    }

    public static PegTxType getTransactionType(
        ActivationConfig.ForBlock activations,
        BridgeStorageProvider provider,
        BridgeConstants bridgeConstants,
        Federation activeFederation,
        Federation retiringFederation,
        BtcTransaction btcTransaction,
        long btcTransactionHeight
    ) {

        List<Federation> liveFeds = new ArrayList<>();
        liveFeds.add(activeFederation);
        if (retiringFederation != null){
            liveFeds.add(retiringFederation);
        }
        Wallet liveFederationsWallet = new BridgeBtcWallet(Context.getOrCreate(bridgeConstants.getBtcParams()), liveFeds);

        // TODO: THIS VALUE IS PENDING TO BE DEFINE. PLEASE UPDATE THIS CONSTANT LATER WITH THE CORRECT VALUE.
        int btcHeightWhenPegoutTxIndexActivates = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates();
        int pegoutTxIndexGracePeriodInBtcBlocks = bridgeConstants.getBtc2RskMinimumAcceptableConfirmations() * 5;
        boolean shouldUsePegoutTxIndex = activations.isActive(ConsensusRule.RSKIP379) && btcTransactionHeight >= btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;

        if (shouldUsePegoutTxIndex){
            return getTransactionTypeUsingPegoutIndex(
                activations,
                provider,
                liveFederationsWallet,
                btcTransaction
            );
        } else {
            Coin minimumPeginTxValue = bridgeConstants.getMinimumPeginTxValue(activations);
            Address oldFederationAddress = Address.fromBase58(
                bridgeConstants.getBtcParams(),
                bridgeConstants.getOldFederationAddress()
            );
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
