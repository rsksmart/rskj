package co.rsk.peg;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP293;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP305;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.*;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.federation.*;
import java.util.*;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @deprecated Methods included in this class should not be used anymore
 * Instead use methods in {@link co.rsk.peg.PegUtils}
 */
@Deprecated(since = "ARROWHEAD-6.0.0")
public class PegUtilsLegacy {

    private static final Logger logger = LoggerFactory.getLogger(PegUtilsLegacy.class);

    private PegUtilsLegacy() {}

    /**
     * Legacy version for identifying if a tx is a pegout
     * Use instead {@link co.rsk.peg.PegUtils#getTransactionType}
     *
     * @param tx
     * @param federations
     * @param activations
     * @return true if it is a peg-out. Otherwise, returns false.
     */
    @Deprecated
    public static boolean isPegOutTx(BtcTransaction tx, List<Federation> federations, ActivationConfig.ForBlock activations) {
        List<Script> federationsP2shScripts = federations.stream()
            .filter(Objects::nonNull)
            .map(PegUtilsLegacy::getFederationStandardP2SHScript)
            .toList();

        return isPegOutTx(tx, activations, federationsP2shScripts);
    }

    /**
     * Legacy version for identifying if a tx is a pegout
     * Use instead {@link co.rsk.peg.PegUtils#getTransactionType}
     *
     * @param btcTx
     * @param activations
     * @param fedStandardP2shScripts
     * @return true if it is a peg-out. Otherwise, returns false.
     */
    @Deprecated
    protected static boolean isPegOutTx(BtcTransaction btcTx, ActivationConfig.ForBlock activations, List<Script> fedStandardP2shScripts) {
        int inputsSize = btcTx.getInputs().size();
        for (int i = 0; i < inputsSize; i++) {
            Optional<Script> redeemScriptOptional = BitcoinUtils.extractRedeemScriptFromInput(btcTx, i);
            if (redeemScriptOptional.isEmpty()) {
                continue;
            }

            List<ScriptChunk> redeemScriptChunks = redeemScriptOptional.get().getChunks();
            if (activations.isActive(ConsensusRule.RSKIP201)) {
                // Extract standard redeem script chunks since the registered utxo could be from a fast bridge or erp federation
                try {
                    RedeemScriptParser redeemScriptParser = RedeemScriptParserFactory.get(redeemScriptChunks);
                    redeemScriptChunks = redeemScriptParser.extractStandardRedeemScriptChunks();
                } catch (ScriptException e) {
                    logger.debug("[isPegOutTx] There is no redeem script", e);
                    // There is no redeem script
                    continue;
                }
            }

            Script redeemScript = new ScriptBuilder().addChunks(redeemScriptChunks).build();
            Script p2shOutputScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
            Script p2wshOutputScript = ScriptBuilder.createP2SHP2WSHOutputScript(redeemScript);

            if (fedStandardP2shScripts.contains(p2shOutputScript) ||
                (activations.isActive(RSKIP305) && fedStandardP2shScripts.contains(p2wshOutputScript))) {
                return true;
            }
        }

        return false;
    }

    @Deprecated
    protected static boolean scriptCorrectlySpendsTx(BtcTransaction tx, int index, Script script) {
        try {
            TransactionInput txInput = tx.getInput(index);

            // Check the input does not contain script op codes
            List<ScriptChunk> chunks = txInput.getScriptSig().getChunks();
            for (ScriptChunk chunk : chunks) {
                if (chunk.isOpCode() && chunk.opcode > ScriptOpCodes.OP_16) {
                    logger.debug("[scriptCorrectlySpendsTx] Input script sig contains an unexpected op code {}", chunk.opcode);
                    return false;
                }
            }

            txInput.getScriptSig().correctlySpends(tx, index, script, Script.ALL_VERIFY_FLAGS);
            logger.debug("[scriptCorrectlySpendsTx] Script correctly spends");
            return true;
        } catch (ScriptException ex) {
            logger.debug("[scriptCorrectlySpendsTx] Script does not spend the given input", ex);
            return false;
        }
    }

    /**
     * Legacy version for identifying if a tx is a pegout
     * Use instead {@link co.rsk.peg.PegUtils#getTransactionType}
     *
     * @param btcTx
     * @param oldFederationAddress
     * @return true if it is a peg-out. Otherwise returns false.
     */
    @Deprecated
    protected static boolean txIsFromOldFederation(BtcTransaction btcTx, Address oldFederationAddress) {
        Script p2shScript = ScriptBuilder.createP2SHOutputScript(oldFederationAddress.getHash160());

        for (int i = 0; i < btcTx.getInputs().size(); i++) {
            if (scriptCorrectlySpendsTx(btcTx, i, p2shScript)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Legacy version to identify if a btc tx is a pegin. Use instead {@link co.rsk.peg.PegUtils#getTransactionType}
     *
     * @param tx the BTC transaction to check
     * @param federation
     * @param federationsWallet live federations
     * @param bridgeConstants
     * @param activations the network HF activations configuration
     * @return true if this is a valid peg-in transaction
     */
    @Deprecated
    public static boolean isValidPegInTx(
        BtcTransaction tx,
        Federation federation,
        Wallet federationsWallet,
        BridgeConstants bridgeConstants,
        ActivationConfig.ForBlock activations) {

        return isValidPegInTx(
            tx,
            Collections.singletonList(federation),
            null,
            federationsWallet,
            bridgeConstants.getMinimumPeginTxValue(activations),
            activations
        );
    }

    /**
     * Legacy version to identify if a btc tx is a pegin. Use instead {@link co.rsk.peg.PegUtils#getTransactionType}
     *
     * @param tx the BTC transaction to check
     * @param activeFederations the active federations
     * @param retiredFederationP2SHScript the retired federation P2SHScript. Could be {@code null}.
     * @param federationsWallet live federations wallet
     * @param minimumPegInTxValue minimum peg-in tx value allowed
     * @param activations the network HF activations configuration
     * @return true if this is a valid peg-in transaction
     */
    @Deprecated
    protected static boolean isValidPegInTx(
        BtcTransaction tx,
        List<Federation> activeFederations,
        Script retiredFederationP2SHScript,
        Wallet federationsWallet,
        Coin minimumPegInTxValue,
        ActivationConfig.ForBlock activations) {

        // First, check tx is not a typical release tx (tx spending from any of the federation addresses and
        // optionally sending some change to any of the federation addresses)
        for (int i = 0; i < tx.getInputs().size(); i++) {
            final int index = i;
            if (activeFederations.stream().anyMatch(federation -> scriptCorrectlySpendsTx(tx, index, federation.getP2SHScript()))) {
                return false;
            }

            if (retiredFederationP2SHScript != null && scriptCorrectlySpendsTx(tx, index, retiredFederationP2SHScript)) {
                return false;
            }

            Optional<Script> redeemScriptOptional = BitcoinUtils.extractRedeemScriptFromInput(tx, i);
            // Check if the registered utxo is not change from an utxo spent from either a fast bridge federation,
            // erp federation, or even a retired fast bridge or erp federation
            if (activations.isActive(ConsensusRule.RSKIP201) && redeemScriptOptional.isPresent()) {
                try {
                    List<ScriptChunk> redeemScriptChunks = redeemScriptOptional.get().getChunks();
                    RedeemScriptParser redeemScriptParser = RedeemScriptParserFactory.get(redeemScriptChunks);

                    List<ScriptChunk> inputStandardRedeemScriptChunks = redeemScriptParser.extractStandardRedeemScriptChunks();
                    Script inputStandardRedeemScript = new ScriptBuilder().addChunks(inputStandardRedeemScriptChunks).build();
                    if (activeFederations.stream().anyMatch(federation -> getFederationStandardRedeemScript(federation).equals(inputStandardRedeemScript))) {
                        return false;
                    }

                    Script legacyOutputScript = ScriptBuilder.createP2SHOutputScript(inputStandardRedeemScript);
                    Script segwitOutputScript = ScriptBuilder.createP2SHP2WSHOutputScript(inputStandardRedeemScript);
                    if (legacyOutputScript.equals(retiredFederationP2SHScript) || segwitOutputScript.equals(retiredFederationP2SHScript)) {
                        return false;
                    }
                } catch (ScriptException e) {
                    // There is no redeem script, could be a peg-in from a P2PKH address
                    logger.debug("[isValidPegInTx] There is no redeem script, could be a peg-in from a P2PKH address", e);
                }
            }
        }

        Coin valueSentToMe = tx.getValueSentToMe(federationsWallet);

        boolean isUTXOsOrTxAmountBelowMinimum =
            activations.isActive(RSKIP293) ? isAnyUTXOAmountBelowMinimum(
                minimumPegInTxValue,
                tx,
                federationsWallet
            ) : valueSentToMe.isLessThan(minimumPegInTxValue); // Legacy minimum validation against the total amount

        if (!isUTXOsOrTxAmountBelowMinimum) {
            return true;
        }

        String logMessage = activations.isActive(RSKIP293) ?
            "[btctx:{}] Someone sent to the federation UTXOs amount less than {} satoshis" :
            "[btctx:{}] Someone sent to the federation less than {} satoshis";
        logger.warn(
            logMessage,
            tx.getHash(),
            minimumPegInTxValue
        );
        return false;
    }

    @Deprecated
    protected static boolean isMigrationTx(
        BtcTransaction btcTx,
        FederationContext federationContext,
        Wallet liveFederationsWallet,
        Coin minimumPeginTxValue,
        ActivationConfig.ForBlock activations) {

        Optional<Federation> retiringFederation = federationContext.getRetiringFederation();
        Optional<Script> retiredFederationP2SHScript = federationContext.getLastRetiredFederationP2SHScript();

        if (retiredFederationP2SHScript.isEmpty() && retiringFederation.isEmpty()) {
            return false;
        }

        List<Script> standardP2shScripts = new ArrayList<>();
        retiredFederationP2SHScript.ifPresent(standardP2shScripts::add);
        retiringFederation.ifPresent(
            federation -> standardP2shScripts.add(getFederationStandardP2SHScript(federation))
        );

        boolean moveFromRetiringOrRetired =  isPegOutTx(btcTx, activations, standardP2shScripts);

        Federation activeFederation = federationContext.getActiveFederation();
        BridgeBtcWallet activeFederationWallet = new BridgeBtcWallet(
            liveFederationsWallet.getContext(),
            Collections.singletonList(activeFederation)
        );
        boolean moveToActive = isValidPegInTx(
            btcTx,
            Collections.singletonList(activeFederation),
            null,
            activeFederationWallet,
            minimumPeginTxValue,
            activations
        );

        return moveFromRetiringOrRetired && moveToActive;
    }

    /**
     * Legacy version for identifying transaction peg type
     * Use instead {@link co.rsk.peg.PegUtils#getTransactionType}
     *
     * @param btcTx
     * @param federationContext
     * @param oldFederationAddress
     * @param activations
     * @param minimumPeginTxValue
     * @param federationsWallet
     * @return PegTxType indicating if the transaction is a peg-in, peg-out/migration, or unknown
     */
    @Deprecated
    protected static PegTxType getTransactionType(
        BtcTransaction btcTx,
        FederationContext federationContext,
        Address oldFederationAddress,
        ActivationConfig.ForBlock activations,
        Coin minimumPeginTxValue,
        Wallet federationsWallet
    ) {
        /************************************************************************/
        /** Special case to migrate funds from an old federation               **/
        /************************************************************************/
        if (activations.isActive(ConsensusRule.RSKIP199) && PegUtilsLegacy.txIsFromOldFederation(
            btcTx,
            oldFederationAddress
        )) {
            logger.debug(
                "[getTransactionType][btc tx {}] is from the old federation, treated as a migration",
                btcTx.getHash()
            );
            return PegTxType.PEGOUT_OR_MIGRATION;
        }

        List<Federation> liveFederations = federationContext.getLiveFederations();
        Script retiredFederationP2SHScript = federationContext.getLastRetiredFederationP2SHScript().orElse(null);

        if (isValidPegInTx(
            btcTx,
            liveFederations,
            retiredFederationP2SHScript,
            federationsWallet,
            minimumPeginTxValue,
            activations
        )) {
            logger.debug("[getTransactionType][btc tx {}] is a peg-in", btcTx.getHash());
            return PegTxType.PEGIN;
        }


        if (isMigrationTx(
            btcTx,
            federationContext,
            federationsWallet,
            minimumPeginTxValue,
            activations
        )) {
            logger.debug("[getTransactionType][btc tx {}] is a migration transaction", btcTx.getHash());
            return PegTxType.PEGOUT_OR_MIGRATION;
        }

        if (isPegOutTx(btcTx, liveFederations, activations)) {
            logger.debug("[getTransactionType][btc tx {}] is a peg-out", btcTx.getHash());
            return PegTxType.PEGOUT_OR_MIGRATION;
        }

        logger.debug("[getTransactionType][btc tx {}] is neither a peg-in, peg-out, nor migration", btcTx.getHash());
        return PegTxType.UNKNOWN;
    }

    /**
     * @param minimumPegInTxValue
     * @param btcTx
     * @param wallet
     * @return true if any UTXO in the given btcTX is below the minimum pegin tx value
     */
    @Deprecated
    protected static boolean isAnyUTXOAmountBelowMinimum(
        Coin minimumPegInTxValue,
        BtcTransaction btcTx,
        Wallet wallet
    ){
        return btcTx.getWalletOutputs(wallet).stream()
            .anyMatch(transactionOutput -> transactionOutput.getValue().isLessThan(minimumPegInTxValue)
        );
    }

    private static Script getFederationStandardRedeemScript(Federation federation) {
        return federation instanceof ErpFederation ?
            ((ErpFederation) federation).getDefaultRedeemScript() :
            federation.getRedeemScript();
    }

    private static Script getFederationStandardP2SHScript(Federation federation) {
        return federation instanceof ErpFederation ?
            ((ErpFederation) federation).getDefaultP2SHScript() :
            federation.getP2SHScript();
    }
}
