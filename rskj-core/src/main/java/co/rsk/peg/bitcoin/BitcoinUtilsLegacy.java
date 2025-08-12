package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static co.rsk.peg.bitcoin.BitcoinUtils.*;

public class BitcoinUtilsLegacy {
    private static final Logger logger = LoggerFactory.getLogger(BitcoinUtilsLegacy.class);

    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated(since="LOVELL-7.2.0", forRemoval=false)
    public static Sha256Hash getMultiSigTransactionHashWithoutSignaturesBeforeRSKIP305(BtcTransaction transaction) {
        if (!transaction.hasWitness()) {
            BtcTransaction multiSigTransactionWithoutSignatures = getMultiSigTransactionWithoutSignaturesBeforeRSKIP305(transaction);
            return multiSigTransactionWithoutSignatures.getHash();
        }

        return transaction.getHash();
    }

    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated(since="LOVELL-7.2.0", forRemoval=false)
    private static BtcTransaction getMultiSigTransactionWithoutSignaturesBeforeRSKIP305(BtcTransaction transaction) {
        NetworkParameters networkParameters = transaction.getParams();
        BtcTransaction transactionCopy = new BtcTransaction(networkParameters, transaction.bitcoinSerialize()); // this is needed to not remove signatures from the original tx
        removeSignaturesFromMultiSigTransactionBeforeRSKIP305(transactionCopy);
        return transactionCopy;
    }

    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated(since="LOVELL-7.2.0", forRemoval=false)
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
