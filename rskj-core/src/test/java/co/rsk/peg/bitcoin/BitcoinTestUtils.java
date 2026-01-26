package co.rsk.peg.bitcoin;

import static co.rsk.bitcoinj.script.ScriptBuilder.createP2SHOutputScript;
import static co.rsk.bitcoinj.script.ScriptBuilder.createP2SHP2WSHOutputScript;
import static co.rsk.peg.bitcoin.BitcoinUtils.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import co.rsk.peg.UTXOBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;

public class BitcoinTestUtils {
    public static final Sha256Hash WITNESS_RESERVED_VALUE = Sha256Hash.ZERO_HASH;

    public static BtcECKey getBtcEcKeyFromSeed(String seed) {
        byte[] serializedSeed = HashUtil.keccak256(seed.getBytes(StandardCharsets.UTF_8));
        return BtcECKey.fromPrivate(serializedSeed);
    }

    public static List<BtcECKey> getBtcEcKeysFromSeeds(String[] seeds, boolean sorted) {
        List<BtcECKey> keys = Arrays.stream(seeds)
            .map(BitcoinTestUtils::getBtcEcKeyFromSeed)
            .collect(Collectors.toList());

        if (sorted) {
            keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        }

        return keys;
    }

    public static List<BtcECKey> getBtcEcKeys(int amount) {
        List<BtcECKey> keys = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            String seed = "seed" + i;
            BtcECKey key = getBtcEcKeyFromSeed(seed);
            keys.add(key);
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
        Script outputScript = createP2SHOutputScript(redeemScript);

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

    public static List<UTXO> createUTXOs(int amount, Address address) {
        Script outputScript = ScriptBuilder.createOutputScript(address);
        UTXOBuilder utxoBuilder = UTXOBuilder.builder().withOutputScript(outputScript);
        List<UTXO> utxos = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            UTXO utxo = utxoBuilder
                    .withTransactionHash(createHash(i + 1))
                    .build();
            utxos.add(utxo);
        }

        return utxos;
    }

    public static List<UTXO> createUTXOs(int amount, Script outputScript) {
        UTXOBuilder utxoBuilder = UTXOBuilder.builder()
            .withOutputScript(outputScript);
        List<UTXO> utxos = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            UTXO utxo = utxoBuilder
                .withTransactionHash(createHash(i + 1))
                .build();
            utxos.add(utxo);
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

    public static byte[] flatKeysAsByteArray(List<BtcECKey> keys) {
        List<byte[]> pubKeys = keys.stream()
            .map(BtcECKey::getPubKey)
            .toList();
        int pubKeysLength = pubKeys.stream().mapToInt(key -> key.length).sum();

        byte[] flatPubKeys = new byte[pubKeysLength];
        int copyPos = 0;
        for (byte[] key : pubKeys) {
            System.arraycopy(key, 0, flatPubKeys, copyPos, key.length);
            copyPos += key.length;
        }

        return flatPubKeys;
    }

    public static void signLegacyTransactionInputFromP2shMultiSig(BtcTransaction transaction, int inputIndex, List<BtcECKey> keys) {
        Script inputRedeemScript = extractRedeemScriptFromInput(transaction, inputIndex)
            .orElseThrow(() -> new IllegalArgumentException("Cannot sign inputs that are not from a p2sh multisig"));

        Script outputScript = createP2SHOutputScript(inputRedeemScript);
        Sha256Hash sigHash = transaction.hashForSignature(
            inputIndex,
            inputRedeemScript,
            BtcTransaction.SigHash.ALL,
            false
        );

        List<BtcECKey> requiredKeys = getRequiredKeysToSign(keys, inputRedeemScript);
        for (BtcECKey key : requiredKeys) {
            signTxInputWithKey(transaction, inputIndex, sigHash, key, outputScript);
        }
    }

    public static void signWitnessTransactionInputFromP2shMultiSig(
        BtcTransaction transaction,
        int inputIndex,
        Coin inputValue,
        List<BtcECKey> keys
    ) {
        Script inputRedeemScript = extractRedeemScriptFromInput(transaction, inputIndex)
            .orElseThrow(() -> new IllegalArgumentException("Cannot sign inputs that are not from a p2sh multisig"));

        Script outputScript = createP2SHP2WSHOutputScript(inputRedeemScript);
        Sha256Hash sigHash = transaction.hashForWitnessSignature(
            inputIndex,
            inputRedeemScript,
            inputValue,
            BtcTransaction.SigHash.ALL,
            false
        );

        List<BtcECKey> requiredKeys = getRequiredKeysToSign(keys, inputRedeemScript);
        for (BtcECKey key : requiredKeys) {
            signTxInputWithKey(transaction, inputIndex, sigHash, key, outputScript);
        }
    }

    private static List<BtcECKey> getRequiredKeysToSign(List<BtcECKey> allKeys, Script redeemScript) {
        int requiredSignatures = redeemScript.getNumberOfSignaturesRequiredToSpend();
        int keysToSign = Math.min(requiredSignatures, allKeys.size());

        return new ArrayList<>(allKeys.subList(0, keysToSign));
    }

    public static void signTxInputWithKey(
        BtcTransaction tx,
        int inputIndex,
        Sha256Hash sigHash,
        BtcECKey signingKey,
        Script outputScript
    ) {
        int sigInsertionIndex = getSigInsertionIndex(tx, inputIndex, sigHash, signingKey);
        byte[] sig = signingKey.sign(sigHash).encodeToDER();

        TransactionSignature federatorTxSig = new TransactionSignature(
            BtcECKey.ECDSASignature.decodeFromDER(sig),
            BtcTransaction.SigHash.ALL,
            false
        );
        signInput(tx, inputIndex, federatorTxSig, sigInsertionIndex, outputScript);
    }

    public static List<Sha256Hash> generateTransactionInputsSigHashes(BtcTransaction btcTx) {
        return IntStream.range(0, btcTx.getInputs().size())
            .mapToObj(i -> generateTransactionInputSigHash(btcTx, i))
            .toList();
    }

    public static Sha256Hash generateTransactionInputSigHash(BtcTransaction btcTx, int inputIndex) {
        if (!inputHasWitness(btcTx, inputIndex)) {
            return generateSigHashForLegacyTransactionInput(btcTx, inputIndex);
        }
        return generateSigHashForSegwitTransactionInput(btcTx, inputIndex, btcTx.getInput(inputIndex).getValue());
    }

    public static List<byte[]> generateSignerEncodedSignatures(BtcECKey signingKey, List<Sha256Hash> sigHashes) {
        return sigHashes.stream()
            .map(signingKey::sign)
            .map(BtcECKey.ECDSASignature::encodeToDER)
            .toList();
    }

    public static BtcTransaction createCoinbaseTransaction(NetworkParameters networkParameters) {
        Address rewardAddress = createP2PKHAddress(networkParameters, "miner");
        Script inputScript = new Script(new byte[]{ 1, 0 }); // Free-form, as long as it's has at least 2 bytes

        BtcTransaction coinbaseTx = new BtcTransaction(networkParameters);
        coinbaseTx.addInput(
            Sha256Hash.ZERO_HASH,
            -1L,
            inputScript
        );
        coinbaseTx.addOutput(Coin.COIN, rewardAddress);
        coinbaseTx.verify();

        return coinbaseTx;
    }

    public static BtcTransaction createCoinbaseTransactionWithWitnessCommitment(
        NetworkParameters networkParameters,
        Sha256Hash witnessCommitment
    ) {
        BtcTransaction coinbaseTx = createCoinbaseTxWithWitnessReservedValue(networkParameters);

        byte[] witnessCommitmentWithHeader = ByteUtil.merge(
            BitcoinUtils.WITNESS_COMMITMENT_HEADER,
            witnessCommitment.getBytes()
        );
        coinbaseTx.addOutput(Coin.ZERO, ScriptBuilder.createOpReturnScript(witnessCommitmentWithHeader));
        coinbaseTx.verify();

        return coinbaseTx;
    }

    public static BtcTransaction createCoinbaseTransactionWithMultipleWitnessCommitments(
        NetworkParameters networkParameters,
        List<Sha256Hash> witnessCommitments
    ) {
        BtcTransaction coinbaseTx = createCoinbaseTxWithWitnessReservedValue(networkParameters);

        for (Sha256Hash witnessCommitment : witnessCommitments) {
            byte[] witnessCommitmentWithHeader = ByteUtil.merge(
                BitcoinUtils.WITNESS_COMMITMENT_HEADER,
                witnessCommitment.getBytes()
            );
            coinbaseTx.addOutput(Coin.ZERO, ScriptBuilder.createOpReturnScript(witnessCommitmentWithHeader));
        }
        coinbaseTx.verify();

        return coinbaseTx;
    }

    public static BtcTransaction createCoinbaseTransactionWithWrongWitnessCommitment(
        NetworkParameters networkParameters,
        Sha256Hash witnessCommitment
    ) {
        BtcTransaction coinbaseTx = createCoinbaseTxWithWitnessReservedValue(networkParameters);

        byte[] wrongWitnessCommitmentWithHeader = ByteUtil.merge(
            new byte[]{ScriptOpCodes.OP_RETURN},
            new byte[]{ScriptOpCodes.OP_PUSHDATA1},
            new byte[]{(byte) BitcoinUtils.WITNESS_COMMITMENT_LENGTH},
            BitcoinUtils.WITNESS_COMMITMENT_HEADER,
            witnessCommitment.getBytes()
        );
        Script wrongWitnessCommitmentScript = new Script(wrongWitnessCommitmentWithHeader);
        coinbaseTx.addOutput(Coin.ZERO, wrongWitnessCommitmentScript);
        coinbaseTx.verify();

        return coinbaseTx;
    }

    private static BtcTransaction createCoinbaseTxWithWitnessReservedValue(NetworkParameters networkParameters) {
        BtcTransaction coinbaseTx = createCoinbaseTransaction(networkParameters);

        TransactionWitness txWitness = new TransactionWitness(1);
        txWitness.setPush(0, WITNESS_RESERVED_VALUE.getBytes());
        coinbaseTx.setWitness(0, txWitness);

        return coinbaseTx;
    }

    public static void addInputFromMatchingOutputScript(BtcTransaction transaction, BtcTransaction sourceTransaction, Script expectedOutputScript) {
        List<TransactionOutput> outputs = sourceTransaction.getOutputs();
        searchForOutput(outputs, expectedOutputScript)
            .ifPresent(transaction::addInput);
    }

    public static byte[] getOutputScriptPubKeyHash(TransactionOutput output) {
        return output.getScriptPubKey().getPubKeyHash();
    }
}
