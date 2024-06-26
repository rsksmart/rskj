package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import co.rsk.bitcoinj.script.ScriptChunk;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.HashUtil;

public class BitcoinTestUtils {

    public static List<BtcECKey> getBtcEcKeysFromSeeds(String[] seeds, boolean sorted) {
        List<BtcECKey> keys = Arrays
            .stream(seeds)
            .map(seed -> BtcECKey.fromPrivate(
                HashUtil.keccak256(seed.getBytes(StandardCharsets.UTF_8))))
            .collect(Collectors.toList());

        if (sorted) {
            keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        }

        return keys;
    }

    public static Address createP2PKHAddress(NetworkParameters networkParameters, String seed) {
        BtcECKey key = BtcECKey.fromPrivate(
            HashUtil.keccak256(seed.getBytes(StandardCharsets.UTF_8)));
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

    public static List<BtcECKey.ECDSASignature> extractSignaturesFromTxInput(TransactionInput txInput) {
        Script scriptSig = txInput.getScriptSig();
        List<ScriptChunk> chunks = scriptSig.getChunks();
        Script redeemScript = new Script(chunks.get(chunks.size() - 1).data);
        RedeemScriptParser parser = RedeemScriptParserFactory.get(redeemScript.getChunks());

        List<BtcECKey.ECDSASignature> signatures = new ArrayList<>();
        for (int i = 1; i <= parser.getM(); i++) {
            ScriptChunk chunk = chunks.get(i);
            if (!chunk.isOpCode() && chunk.data.length > 0) {
                signatures.add(TransactionSignature.decodeFromDER(chunk.data));
            }
        }
        return signatures;
    }

    public static List<Coin> coinListOf(long... values) {
        return Arrays.stream(values)
            .mapToObj(Coin::valueOf)
            .collect(Collectors.toList());
    }

    public static UTXO createUTXO(int nHash, long index, Coin value, Address address) {
        return new UTXO(
            createHash(nHash),
            index,
            value,
            10,
            false,
            ScriptBuilder.createOutputScript(address));
    }

    public static List<UTXO> createUTXOs(int amount, Address address) {
        List<UTXO> utxos = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            utxos.add(createUTXO(i + 1, 0, Coin.COIN, address));
        }

        return utxos;
    }

    public static BtcTransaction createBtcTransactionWithOutputToAddress(
        NetworkParameters networkParameters,
        Coin amount,
        Address btcAddress) {

        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(amount, btcAddress);
        BtcECKey srcKey = BtcECKey.fromPublicOnly(Hex.decode("02550cc87fa9061162b1dd395a16662529c9d8094c0feca17905a3244713d65fe8"));
        tx.addInput(
            createHash(100),
            0,
            ScriptBuilder.createInputScript(null, srcKey)
        );

        return tx;
    }
}
