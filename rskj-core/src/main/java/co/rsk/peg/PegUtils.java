package co.rsk.peg;

import static co.rsk.peg.pegin.RejectedPeginReason.INVALID_AMOUNT;
import static co.rsk.peg.pegin.RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER;
import static co.rsk.peg.pegin.RejectedPeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER;
import static co.rsk.peg.pegin.RejectedPeginReason.PEGIN_V1_INVALID_PAYLOAD;

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
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.peg.pegin.PeginEvaluationResult;
import co.rsk.peg.pegin.PeginProcessAction;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PegUtils {
    private static final Logger logger = LoggerFactory.getLogger(PegUtils.class);

    private PegUtils() { }

    static PegTxType getTransactionTypeUsingPegoutIndex(
        ActivationConfig.ForBlock activations,
        BridgeStorageProvider provider,
        Wallet liveFederationsWallet,
        BtcTransaction btcTransaction
    ) {
        if (!activations.isActive(ConsensusRule.RSKIP379)) {
            throw new IllegalStateException("Can't call this method before RSKIP379 activation");
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
     * @param btcTx
     * @param fedWallet
     * @param minimumPegInTxValue
     * @param activations
     * @return
     */
    static boolean allUTXOsToFedAreAboveMinimumPeginValue(
        BtcTransaction btcTx,
        Wallet fedWallet,
        Coin minimumPegInTxValue,
        ActivationConfig.ForBlock activations
    ) {
        if(!activations.isActive(ConsensusRule.RSKIP379)) {
            return !PegUtilsLegacy.isAnyUTXOAmountBelowMinimum(minimumPegInTxValue, btcTx, fedWallet);
        }

        List<TransactionOutput> fedUtxos = btcTx.getWalletOutputs(fedWallet);
        if(fedUtxos.isEmpty()) {
            return false;
        }

        return fedUtxos.stream().allMatch(transactionOutput ->
            transactionOutput.getValue().compareTo(minimumPegInTxValue) >= 0
        );
    }

    static PegTxType getTransactionType(
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

        int btcHeightWhenPegoutTxIndexActivates = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates();
        int pegoutTxIndexGracePeriodInBtcBlocks = bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int heightAtWhichToStartUsingPegoutIndex = btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;
        boolean shouldUsePegoutTxIndex = activations.isActive(ConsensusRule.RSKIP379) &&
            btcTransactionHeight >= heightAtWhichToStartUsingPegoutIndex;

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

    static PeginEvaluationResult evaluatePegin(
        BtcTransaction btcTx,
        PeginInformation peginInformation,
        Coin minimumPeginTxValue,
        Wallet fedWallet,
        ActivationConfig.ForBlock activations
    ) {
        if(!activations.isActive(ConsensusRule.RSKIP379)) {
            throw new IllegalStateException("Can't call this method before RSKIP379 activation");
        }

        if(!allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPeginTxValue, activations)) {
            logger.debug("[evaluatePegin] Peg-in contains at least one utxo below the minimum value");
            return new PeginEvaluationResult(PeginProcessAction.CANNOT_BE_PROCESSED, INVALID_AMOUNT);
        }

        try {
            peginInformation.parse(btcTx);
        } catch (PeginInstructionsException e) {
            logger.warn("[evaluatePegin] Error while trying to parse peg-in information for tx {}. {}",
                btcTx.getHash(),
                e.getMessage()
            );

            boolean hasRefundAddress = peginInformation.getBtcRefundAddress() != null;

            PeginProcessAction peginProcessAction = hasRefundAddress ?
                                                        PeginProcessAction.CAN_BE_REFUNDED :
                                                        PeginProcessAction.CANNOT_BE_PROCESSED;

            return new PeginEvaluationResult(peginProcessAction, PEGIN_V1_INVALID_PAYLOAD);
        }

        int protocolVersion = peginInformation.getProtocolVersion();
        switch (protocolVersion) {
            case 0:
                return evaluateLegacyPeginSender(peginInformation.getSenderBtcAddressType());
            case 1:
                return new PeginEvaluationResult(PeginProcessAction.CAN_BE_REGISTERED);
            default:
                // This flow should never be reached.
                String message = String.format("Invalid state. Unexpected pegin protocol %d", protocolVersion);
                logger.error("[evaluatePegin] {}", message);
                throw new IllegalStateException(message);
        }
    }

    private static PeginEvaluationResult evaluateLegacyPeginSender(TxSenderAddressType senderAddressType)  {
        switch (senderAddressType) {
            case P2PKH:
            case P2SHP2WPKH:
                return new PeginEvaluationResult(PeginProcessAction.CAN_BE_REGISTERED);
            case P2SHMULTISIG:
            case P2SHP2WSH:
                return new PeginEvaluationResult(PeginProcessAction.CAN_BE_REFUNDED, LEGACY_PEGIN_MULTISIG_SENDER);
            default:
                return new PeginEvaluationResult(PeginProcessAction.CANNOT_BE_PROCESSED, LEGACY_PEGIN_UNDETERMINED_SENDER);
        }
    }
}
