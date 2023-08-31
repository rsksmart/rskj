package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import co.rsk.bitcoinj.script.ScriptChunk;
import org.ethereum.crypto.HashUtil;

public class BitcoinTestUtils {

    private static final int FIRST_SIGNATURE_INDEX = 1;

    public static List<BtcECKey> getBtcEcKeysFromSeeds(String[] seeds, boolean sorted) {
        List<BtcECKey> keys = Arrays
            .stream(seeds)
            .map(seed -> BtcECKey.fromPrivate(HashUtil.keccak256(seed.getBytes(StandardCharsets.UTF_8))))
            .collect(Collectors.toList());

        if (sorted) {
            keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        }

        return keys;
    }

    public static Address createP2PKHAddress(NetworkParameters networkParameters, String seed) {
        BtcECKey key = BtcECKey.fromPrivate(HashUtil.keccak256(seed.getBytes(StandardCharsets.UTF_8)));
        return key.toAddress(networkParameters);
    }

    public static Address createP2SHMultisigAddress(NetworkParameters networkParameters, List<BtcECKey> keys) {
        Script redeemScript = ScriptBuilder.createRedeemScript((keys.size() / 2) + 1, keys);
        Script outputScript = ScriptBuilder.createP2SHOutputScript(redeemScript);

        return Address.fromP2SHScript(networkParameters, outputScript);
    }

    public static Sha256Hash createHash(int nHash) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) (0xFF & nHash);
        bytes[1] = (byte) (0xFF & nHash >> 8);
        bytes[2] = (byte) (0xFF & nHash >> 16);
        bytes[3] = (byte) (0xFF & nHash >> 24);

        return Sha256Hash.wrap(bytes);
    }

    public static Optional<BtcECKey.ECDSASignature> extractFirstSignature(TransactionInput txInput) {
        Script txInputScriptSig = txInput.getScriptSig();
        List<ScriptChunk> chunks = txInputScriptSig.getChunks();
        if (chunks.isEmpty())
            return Optional.empty();
        byte[] firstSignatureData = chunks.get(FIRST_SIGNATURE_INDEX).data;
        return Optional.of(TransactionSignature.decodeFromDER(firstSignatureData));
    }
}
