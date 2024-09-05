package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.ScriptException;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class BitcoinUtils {

    private static final Logger logger = LoggerFactory.getLogger(BitcoinUtils.class);
    private static final int FIRST_INPUT_INDEX = 0;

    private BitcoinUtils() { }

    public static Optional<Sha256Hash> getFirstInputSigHash(BtcTransaction btcTx){
        if (btcTx.getInputs().isEmpty()){
            return Optional.empty();
        }
        TransactionInput txInput = btcTx.getInput(FIRST_INPUT_INDEX);
        Optional<Script> redeemScript = extractRedeemScriptFromInput(txInput);
        if (!redeemScript.isPresent()) {
            return Optional.empty();
        }

        return Optional.of(btcTx.hashForSignature(
            FIRST_INPUT_INDEX,
            redeemScript.get(),
            BtcTransaction.SigHash.ALL,
            false
        ));
    }

    public static Optional<Script> extractRedeemScriptFromInput(TransactionInput txInput) {
        Script inputScript = txInput.getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        if (chunks == null || chunks.isEmpty()) {
            return Optional.empty();
        }

        byte[] program = chunks.get(chunks.size() - 1).data;
        if (program == null) {
            return Optional.empty();
        }

        try {
            Script redeemScript = new Script(program);
            return Optional.of(redeemScript);
        } catch (ScriptException e) {
            logger.debug(
                "[extractRedeemScriptFromInput] Failed to extract redeem script from tx input {}. {}",
                txInput,
                e.getMessage()
            );
            return Optional.empty();
        }
    }

    public static Script removeSignaturesFromErpScriptSig(Script scriptSig) {
        List<ScriptChunk> scriptSigChunks = scriptSig.getChunks();

        ScriptBuilder builder = new ScriptBuilder();

        int op0ChunkIndex = 0;
        int redeemScriptChunkIndex = scriptSigChunks.size() - 1;
        int opIfElseChunkIndex = redeemScriptChunkIndex - 1;

        ScriptChunk op0Chunk = scriptSigChunks.get(op0ChunkIndex);
        builder.addChunk(op0Chunk);

        int signaturesStartIndex = op0ChunkIndex + 1;
        int signaturesEndIndex = opIfElseChunkIndex - 1;
        for (int i = signaturesStartIndex; i <= signaturesEndIndex ; i++) {
            byte[] data = new byte[0];
            builder.data(data);
        }

        ScriptChunk opIfElseChunk = scriptSigChunks.get(opIfElseChunkIndex);
        builder.addChunk(opIfElseChunk);
        ScriptChunk redeemScriptChunk = scriptSigChunks.get(redeemScriptChunkIndex);
        builder.addChunk(redeemScriptChunk);

        return builder.build();
    }
}
