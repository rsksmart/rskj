package co.rsk.peg.bitcoin;

import static co.rsk.peg.bitcoin.BitcoinUtils.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BitcoinUtilsLegacy {
    private static final Logger logger = LoggerFactory.getLogger(BitcoinUtilsLegacy.class);

    private BitcoinUtilsLegacy() {}

    /**
     * @deprecated replaced by {@link BitcoinUtils#getMultiSigTransactionHashWithoutSignatures(BtcTransaction transaction)}
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated(since="LOVELL-7.2.0")
    public static Sha256Hash getMultiSigTransactionHashWithoutSignaturesBeforeRSKIP305(BtcTransaction transaction) {
        if (!transaction.hasWitness()) {
            BtcTransaction multiSigTransactionWithoutSignatures = getMultiSigTransactionWithoutSignaturesBeforeRSKIP305(transaction);
            return multiSigTransactionWithoutSignatures.getHash();
        }

        return transaction.getHash();
    }

    /**
     * @deprecated replaced by {@link BitcoinUtils#getMultiSigTransactionWithoutSignatures(BtcTransaction transaction)}
     */
    @Deprecated(since="LOVELL-7.2.0")
    private static BtcTransaction getMultiSigTransactionWithoutSignaturesBeforeRSKIP305(BtcTransaction transaction) {
        NetworkParameters networkParameters = transaction.getParams();
        BtcTransaction transactionCopy = new BtcTransaction(networkParameters, transaction.bitcoinSerialize()); // this is needed to not remove signatures from the original tx
        removeSignaturesFromMultiSigTransactionBeforeRSKIP305(transactionCopy);
        return transactionCopy;
    }

    /**
     * @deprecated replaced by {@link BitcoinUtils#removeSignaturesFromMultiSigTransaction(BtcTransaction)}
     */
    @Deprecated(since="LOVELL-7.2.0")
    private static void removeSignaturesFromMultiSigTransactionBeforeRSKIP305(BtcTransaction transaction) {
        List<TransactionInput> inputs = transaction.getInputs();
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            Script redeemScript = extractRedeemScriptFromInput(transaction, inputIndex)
                .orElseThrow(() -> {
                    String message = "Cannot remove signatures from transaction inputs that do not have P2SH multisig input script.";
                    logger.error("[removeSignaturesFromMultiSigTransaction] {}", message);
                    return new IllegalArgumentException(message);
                });

            boolean inputHasWitness = inputHasWitness(transaction, inputIndex);
            if (inputHasWitness) {
                setSpendingBaseScriptSegwit(transaction, inputIndex, redeemScript);
            } else {
                setSpendingBaseScriptLegacy(transaction, inputIndex, redeemScript);
            }
        }
    }
}
