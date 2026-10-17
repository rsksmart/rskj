package co.rsk.peg;

import static co.rsk.peg.bitcoin.BitcoinUtils.getMultiSigTransactionHashWithoutSignatures;
import static co.rsk.peg.bitcoin.BitcoinUtilsLegacy.getMultiSigTransactionHashWithoutSignaturesBeforeRSKIP305;
import static co.rsk.peg.pegin.RejectedPeginReason.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.FlyoverRedeemScriptBuilderImpl;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.pegin.PeginEvaluationResult;
import co.rsk.peg.pegin.PeginProcessAction;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import java.util.*;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.HashUtil;
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

        Optional<Sha256Hash> inputSigHash = BitcoinUtils.getSigHashForPegoutIndex(btcTransaction);
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
    public static boolean allUTXOsToFedAreAboveMinimumPeginValue(
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
        FederationContext federationContext,
        BtcTransaction btcTransaction,
        long btcTransactionHeight
    ) {
        List<Federation> liveFeds = federationContext.getLiveFederations();
        Context context = Context.getOrCreate(bridgeConstants.getBtcParams());
        Wallet liveFederationsWallet = new BridgeBtcWallet(context, liveFeds);

        if (!isPegoutTxIndexEnabled(bridgeConstants, activations, btcTransactionHeight)) {
            // Use legacy logic
            Coin minimumPeginTxValue = bridgeConstants.getMinimumPeginTxValue(activations);
            FederationConstants federationConstants = bridgeConstants.getFederationConstants();
            Address oldFederationAddress = Address.fromBase58(
                bridgeConstants.getBtcParams(),
                federationConstants.getOldFederationAddress()
            );

            return PegUtilsLegacy.getTransactionType(
                btcTransaction,
                federationContext,
                oldFederationAddress,
                activations,
                minimumPeginTxValue,
                liveFederationsWallet
            );
        }

        // Check first if the transaction is part of an SVP process
        if (isTheSvpFundTransaction(provider, activations, btcTransaction)) {
            return PegTxType.SVP_FUND_TX;
        }
        if (isTheSvpSpendTransaction(provider, activations, btcTransaction)) {
            return PegTxType.SVP_SPEND_TX;
        }

        return getTransactionTypeUsingPegoutIndex(
            activations,
            provider,
            liveFederationsWallet,
            btcTransaction
        );
    }

    private static boolean isPegoutTxIndexEnabled(
        BridgeConstants bridgeConstants,
        ActivationConfig.ForBlock activations,
        long btcTransactionHeight
    ) {
        int btcHeightWhenPegoutTxIndexActivates = bridgeConstants.getBtcHeightWhenPegoutTxIndexActivates();
        int pegoutTxIndexGracePeriodInBtcBlocks = bridgeConstants.getPegoutTxIndexGracePeriodInBtcBlocks();
        int heightAtWhichToStartUsingPegoutIndex = btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;
        return activations.isActive(ConsensusRule.RSKIP379) &&
            btcTransactionHeight >= heightAtWhichToStartUsingPegoutIndex;
    }

    private static boolean isTheSvpFundTransaction(
        BridgeStorageProvider provider,
        ActivationConfig.ForBlock activations,
        BtcTransaction transaction
    ) {
        return provider.getSvpFundTxHashUnsigned()
            .map(svpFundTxHashUnsigned -> {
                try {
                    Sha256Hash txHashWithoutSignatures;
                    if (!activations.isActive(ConsensusRule.RSKIP305)) {
                        txHashWithoutSignatures = getMultiSigTransactionHashWithoutSignaturesBeforeRSKIP305(transaction);
                    } else {
                        txHashWithoutSignatures = getMultiSigTransactionHashWithoutSignatures(transaction);
                    }
                    return svpFundTxHashUnsigned.equals(txHashWithoutSignatures);
                } catch (IllegalArgumentException e) {
                    logger.trace(
                        "[isTheSvpFundTransaction] Btc tx {} either has witness or non p2sh-legacy-multisig inputs, so we'll assume is not the fund tx",
                        transaction.getHash(),
                        e
                    );
                    return false;
                }
            })
            .orElse(false);
    }

    private static boolean isTheSvpSpendTransaction(
        BridgeStorageProvider provider,
        ActivationConfig.ForBlock activations,
        BtcTransaction transaction
    ) {
        return provider.getSvpSpendTxHashUnsigned()
            .map(svpSpendTxHashUnsigned -> {
                try {
                    Sha256Hash txHashWithoutSignatures;
                    if (!activations.isActive(ConsensusRule.RSKIP305)) {
                        txHashWithoutSignatures = getMultiSigTransactionHashWithoutSignaturesBeforeRSKIP305(transaction);
                    } else {
                        txHashWithoutSignatures = getMultiSigTransactionHashWithoutSignatures(transaction);
                    }
                    return svpSpendTxHashUnsigned.equals(txHashWithoutSignatures);
                } catch (IllegalArgumentException e) {
                    logger.trace(
                        "[isTheSvpSpendTransaction] Btc tx {} either has witness or non p2sh-legacy-multisig inputs, so we'll assume is not the spend tx",
                        transaction.getHash(),
                        e
                    );
                    return false;
                }
            })
            .orElse(false);
    }

    static PeginEvaluationResult evaluatePegin(
        BtcTransaction btcTx,
        PeginInformation peginInformation,
        Coin minimumPeginTxValue,
        Wallet fedWallet,
        ActivationConfig.ForBlock activations
    ) {
        if (!activations.isActive(ConsensusRule.RSKIP379)) {
            throw new IllegalStateException("Can't call this method before RSKIP379 activation");
        }

        if (!allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPeginTxValue, activations)) {
            logger.debug("[evaluatePegin] Peg-in contains at least one utxo below the minimum value");
            return new PeginEvaluationResult(PeginProcessAction.NO_REFUND, INVALID_AMOUNT);
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
                PeginProcessAction.REFUND :
                PeginProcessAction.NO_REFUND;

            return new PeginEvaluationResult(peginProcessAction, PEGIN_V1_INVALID_PAYLOAD);
        }

        int protocolVersion = peginInformation.getProtocolVersion();
        switch (protocolVersion) {
            case 0:
                return evaluateLegacyPegin(btcTx, fedWallet, peginInformation.getSenderBtcAddressType());
            case 1:
                return new PeginEvaluationResult(PeginProcessAction.REGISTER);
            default:
                // This flow should never be reached.
                String message = String.format("Invalid state. Unexpected pegin protocol %d", protocolVersion);
                logger.error("[evaluatePegin] {}", message);
                throw new IllegalStateException(message);
        }
    }

    private static PeginEvaluationResult evaluateLegacyPegin(BtcTransaction btcTx, Wallet fedWallet, TxSenderAddressType senderAddressType)  {
        return switch (senderAddressType) {
            case P2PKH, P2SHP2WPKH -> new PeginEvaluationResult(PeginProcessAction.REGISTER);
            case P2SHMULTISIG, P2SHP2WSH -> {
                if (hasOutputsToDifferentTypesOfFeds(btcTx, fedWallet)) {
                    yield new PeginEvaluationResult(PeginProcessAction.NO_REFUND, LEGACY_PEGIN_MULTISIG_SENDER);
                }
                yield new PeginEvaluationResult(PeginProcessAction.REFUND, LEGACY_PEGIN_MULTISIG_SENDER);
            }
            default -> new PeginEvaluationResult(PeginProcessAction.NO_REFUND, LEGACY_PEGIN_UNDETERMINED_SENDER);
        };
    }

    private static boolean hasOutputsToDifferentTypesOfFeds(BtcTransaction btcTx, Wallet fedWallet) {
        Set<Federation> destinationFeds = new HashSet<>();
        for (TransactionOutput output : btcTx.getOutputs()) {
            byte[] outputP2shScript = output.getScriptPubKey().getPubKeyHash();
            Optional<Federation> destinationFed = ((BridgeBtcWallet) fedWallet).getDestinationFederation(outputP2shScript);
            destinationFed.ifPresent(destinationFeds::add);
        }
        if (destinationFeds.size() == 1) {
            return false;
        }

        Iterator<Federation> iterator = destinationFeds.iterator();
        Federation firstFed = iterator.next();
        Federation secondFed = iterator.next();
        return firstFed.getFormatVersion() != secondFed.getFormatVersion();
    }

    public static Keccak256 getFlyoverDerivationHash(
        Keccak256 derivationArgumentsHash,
        Address userRefundAddress,
        Address lpBtcAddress,
        RskAddress lbcAddress,
        ActivationConfig.ForBlock activations
    ) {
        byte[] flyoverDerivationHashData = derivationArgumentsHash.getBytes();
        byte[] userRefundAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(activations, userRefundAddress);
        byte[] lpBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(activations, lpBtcAddress);
        byte[] lbcAddressBytes = lbcAddress.getBytes();
        byte[] result = new byte[
            flyoverDerivationHashData.length +
                userRefundAddressBytes.length +
                lpBtcAddressBytes.length +
                lbcAddressBytes.length
            ];

        int dstPosition = 0;

        System.arraycopy(
            flyoverDerivationHashData,
            0,
            result,
            dstPosition,
            flyoverDerivationHashData.length
        );
        dstPosition += flyoverDerivationHashData.length;

        System.arraycopy(
            userRefundAddressBytes,
            0,
            result,
            dstPosition,
            userRefundAddressBytes.length
        );
        dstPosition += userRefundAddressBytes.length;

        System.arraycopy(
            lbcAddressBytes,
            0,
            result,
            dstPosition,
            lbcAddressBytes.length
        );
        dstPosition += lbcAddressBytes.length;

        System.arraycopy(
            lpBtcAddressBytes,
            0,
            result,
            dstPosition,
            lpBtcAddressBytes.length
        );

        return new Keccak256(HashUtil.keccak256(result));
    }

    public static Address getFlyoverFederationAddress(NetworkParameters networkParameters, Keccak256 flyoverDerivationHash, Federation federation) {
        Script flyoverScriptPubKey = getFlyoverFederationScriptPubKey(flyoverDerivationHash, federation);
        return Address.fromP2SHScript(networkParameters, flyoverScriptPubKey);
    }

    public static Script getFlyoverFederationScriptPubKey(Keccak256 flyoverDerivationHash, Federation federation) {
        Script flyoverRedeemScript = getFlyoverFederationRedeemScript(flyoverDerivationHash, federation.getRedeemScript());
        return getFlyoverFederationOutputScript(flyoverRedeemScript, federation.getFormatVersion());
    }

    public static Script getFlyoverFederationRedeemScript(Keccak256 flyoverDerivationHash, Script federationRedeemScript) {
        return FlyoverRedeemScriptBuilderImpl.builder()
            .of(flyoverDerivationHash, federationRedeemScript);
    }

    public static Script getFlyoverFederationOutputScript(Script flyoverRedeemScript, int federationFormatVersion) {
        if (federationFormatVersion != FederationFormatVersion.P2SH_P2WSH_ERP_FEDERATION.getFormatVersion()) {
            return ScriptBuilder.createP2SHOutputScript(flyoverRedeemScript);
        }

        return ScriptBuilder.createP2SHP2WSHOutputScript(flyoverRedeemScript);
    }
}
