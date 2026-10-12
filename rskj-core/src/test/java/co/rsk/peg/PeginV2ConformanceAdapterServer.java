package co.rsk.peg;

import static co.rsk.RskTestUtils.createRskBlock;
import static co.rsk.peg.BridgeSupportTestUtil.*;
import static co.rsk.peg.PegTestUtils.*;
import static co.rsk.peg.bitcoin.BitcoinUtils.createBaseInputScriptThatSpendsFromRedeemScript;
import static co.rsk.peg.bitcoin.BitcoinUtils.createBaseWitnessThatSpendsFromErpRedeemScript;
import static co.rsk.peg.bitcoin.UtxoUtils.extractOutpointValues;
import static co.rsk.peg.pegin.RejectedPeginReason.*;
import static co.rsk.peg.utils.NonRefundablePeginReason.OUTPUTS_SENT_TO_DIFFERENT_TYPES_OF_FEDS;
import static co.rsk.peg.utils.NonRefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.PegoutsWaitingForConfirmations.Entry;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.*;
import co.rsk.peg.lockingcap.LockingCapSupport;
import co.rsk.peg.pegin.RejectedPeginReason;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import org.ethereum.util.ByteUtil;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.pegininstructions.PeginRefundScriptType;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.peg.utils.NonRefundablePeginReason;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.WhitelistStorageProvider;
import co.rsk.peg.whitelist.WhitelistSupportImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.*;
import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.peg.pegin.RejectedPeginReason;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.peg.utils.NonRefundablePeginReason;
import co.rsk.peg.vote.ABICallSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.ethereum.core.CallTransaction;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;

/**
 * PoC-only adapter server for the SLUICE peg-in instructions v2 conformance suite.
 *
 * Reads one JSON request per connection, builds fresh Bridge fixtures (the same wiring as
 * {@link BridgeSupportRegisterBtcTransactionTest}, with a real Repository so credits are observed
 * as balance changes and a real {@link BridgeEventLoggerImpl} so events are decoded from receipt logs),
 * runs registerBtcTransaction, and replies with the observed outcome.
 *
 * Known limits (reported to the harness rather than hidden):
 *  - "active" is wired as an activation switch (all rules vs. all-but-v2), not a boundary height.
 *  - Taproot sender inputs carry a 64-byte placeholder witness signature: bitcoinj-thin cannot sign BIP340.
 *  - Relay (federation signing + Bitcoin Core testmempoolaccept) is not implemented and returns "unsupported".
 */
public class PeginV2ConformanceAdapterServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final FederationConstants FEDERATION_CONSTANTS = BRIDGE_CONSTANTS.getFederationConstants();
    private static final NetworkParameters PARAMS = BRIDGE_CONSTANTS.getBtcParams();
    private static final ActivationConfig.ForBlock ALL = ActivationConfigsForTest.all().forBlock(0);
    private static final ActivationConfig.ForBlock ALL_BUT_V2 =
        ActivationConfigsForTest.allBut(ConsensusRule.RSKIP_PEGIN_INSTRUCTIONS_V2).forBlock(0);
    private static final int FIRST_OUTPUT_INDEX = 0;

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 47777;
        new Context(PARAMS);
        try (ServerSocket server = new ServerSocket(port)) {
            System.err.println("pegin-v2 conformance adapter listening on " + port);
            while (true) {
                try (Socket socket = server.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
                    String line = in.readLine();
                    ObjectNode reply;
                    try {
                        reply = handle(MAPPER.readTree(line));
                    } catch (Exception e) {
                        reply = MAPPER.createObjectNode();
                        reply.put("unsupported", "adapter error: " + e);
                        e.printStackTrace();
                    } finally {
                        PegUtils.testOnlyInjectedRefundableRejection = Optional.empty();
                    }
                    out.write(MAPPER.writeValueAsString(reply));
                    out.write("\n");
                    out.flush();
                }
            }
        }
    }

    static ObjectNode handle(JsonNode request) throws Exception {
        String operation = request.get("operation").asText();
        return switch (operation) {
            case "parse" -> parse(request);
            case "pegin" -> pegin(request);
            default -> throw new IllegalArgumentException("unknown operation " + operation);
        };
    }

    // ---------------------------------------------------------------- parse

    static ObjectNode parse(JsonNode request) throws Exception {
        byte[] payload = Hex.decode(request.get("payload_hex").asText());
        BtcTransaction btcTx = new BtcTransaction(PARAMS);
        // Built by hand: ScriptBuilder.createOpReturnScript caps data at 80 bytes (relay policy), but the Bridge
        // parser must be exercised with longer pushes too, which are consensus-valid on-chain.
        btcTx.addOutput(Coin.ZERO, new ScriptBuilder().op(co.rsk.bitcoinj.script.ScriptOpCodes.OP_RETURN).data(payload).build());

        ObjectNode reply = MAPPER.createObjectNode();
        try {
            // Actual dispatcher and parser; a PeginInstructionsException here is what PegUtils.evaluatePegin
            // classifies as PEGIN_V1_INVALID_PAYLOAD.
            Optional<co.rsk.peg.pegininstructions.PeginInstructions> parsed =
                new co.rsk.peg.pegininstructions.PeginInstructionsProvider().buildPeginInstructions(btcTx);
            if (parsed.isEmpty()) {
                throw new PeginInstructionsException("no RSKT output");
            }
            co.rsk.peg.pegininstructions.PeginInstructions instructions = parsed.get();
            Script refund = null;
            if (instructions instanceof co.rsk.peg.pegininstructions.PeginInstructionsVersion2 v2) {
                refund = v2.getBtcRefundScript().orElse(null);
            } else if (instructions instanceof co.rsk.peg.pegininstructions.PeginInstructionsVersion1 v1) {
                refund = v1.getBtcRefundAddress().map(ScriptBuilder::createOutputScript).orElse(null);
            }
            reply.put("ok", true);
            reply.put("destination_hex", Hex.toHexString(instructions.getRskDestinationAddress().getBytes()));
            if (refund == null) {
                reply.putNull("refund_script_hex");
            } else {
                reply.put("refund_script_hex", Hex.toHexString(refund.getProgram()));
            }
        } catch (PeginInstructionsException e) {
            reply.put("ok", false);
            reply.putNull("destination_hex");
            reply.putNull("refund_script_hex");
            reply.put("reason", PEGIN_V1_INVALID_PAYLOAD.name());
        }
        return reply;
    }

    // ---------------------------------------------------------------- pegin

    /** One sender input: the funding output it spends (independently built) and how to finish its scriptSig/witness. */
    private record SenderInput(Script fundingScriptPubKey, boolean derivable, List<BtcECKey> keys, Script redeemScript, String kind) {}

    static ObjectNode pegin(JsonNode request) throws Exception {
        boolean relay = request.get("complete_signing_and_check_relay").asBoolean();
        BitcoinCore core = relay ? BitcoinCore.fromEnvironment() : null;

        boolean active = request.get("active").asBoolean();
        ActivationConfig.ForBlock activations = active ? ALL : ALL_BUT_V2;
        Coin minimum = BRIDGE_CONSTANTS.getMinimumPeginTxValue(activations);
        byte[] payload = Hex.decode(request.get("payload_hex").asText());

        List<Coin> outputValues = new ArrayList<>();
        for (JsonNode label : request.get("federation_outputs")) {
            outputValues.add(switch (label.asText()) {
                case "minimum-1" -> minimum.subtract(Coin.SATOSHI);
                case "minimum" -> minimum;
                case "minimum+1" -> minimum.add(Coin.SATOSHI);
                case "large" -> Coin.valueOf(Math.max(100_000_000L, 100L * minimum.getValue()));
                default -> throw new IllegalArgumentException("unknown federation output label " + label);
            });
        }
        Coin total = outputValues.stream().reduce(Coin.ZERO, Coin::add);

        Fixture fx = new Fixture(activations);

        // --- Peg-in transaction with genuine inputs of the requested kinds
        BtcTransaction btcTx = new BtcTransaction(PARAMS);
        List<SenderInput> senders = new ArrayList<>();
        if (relay) {
            // Real chain: Bitcoin Core signs a taproot spend of its own mined coins and mines the peg-in,
            // so the release's prevout exists for testmempoolaccept.
            for (JsonNode kind : request.get("sender_kinds")) {
                if (!"p2tr".equals(kind.asText())) {
                    throw new IllegalArgumentException("relay fixture only funds taproot senders, got " + kind);
                }
                senders.add(senderInput("p2tr", senders.size()));
            }
            btcTx = core.buildAndMinePegin(outputValues, fx.activeFederation.getAddress(), payload);
        } else {
            int senderIndex = 0;
            for (JsonNode kind : request.get("sender_kinds")) {
                SenderInput sender = senderInput(kind.asText(), senderIndex++);
                senders.add(sender);
                BtcTransaction funding = new BtcTransaction(PARAMS);
                funding.addInput(BitcoinTestUtils.createHash(100 + senderIndex), 0, new Script(new byte[]{}));
                funding.addOutput(total.add(Coin.COIN), sender.fundingScriptPubKey());
                btcTx.addInput(funding.getOutput(0));
            }
            for (Coin value : outputValues) {
                btcTx.addOutput(value, fx.activeFederation.getAddress());
            }
            btcTx.addOutput(Coin.ZERO, ScriptBuilder.createOpReturnScript(payload));
            for (int i = 0; i < senders.size(); i++) {
                finishInput(btcTx, i, senders.get(i));
            }
        }

        if (request.get("inject_refundable_rejection").asBoolean()) {
            PegUtils.testOnlyInjectedRefundableRejection = Optional.of(PEGIN_CAP_SURPASSED);
        }

        int height = fx.heightAtWhichToStartUsingPegoutIndex;
        PartialMerkleTree pmt = fx.createPmtAndMockBlockStore(btcTx, height);
        if (btcTx.hasWitness()) {
            pmt = fx.createPmtWithWitness(btcTx);
        }

        co.rsk.core.Coin bridgeBalanceBefore = fx.repository.getBalance(PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupport = fx.buildBridgeSupport();
        bridgeSupport.registerBtcTransaction(fx.rskTx, btcTx.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

        // --- Observe
        ObjectNode reply = MAPPER.createObjectNode();
        reply.put("minimum_pegin_sats", minimum.getValue());
        ArrayNode values = reply.putArray("federation_output_values_sats");
        outputValues.forEach(v -> values.add(v.getValue()));
        ArrayNode senderScripts = reply.putArray("fixture_sender_scripts_hex");
        for (SenderInput s : senders) {
            if (s.derivable()) {
                senderScripts.add(Hex.toHexString(s.fundingScriptPubKey().getProgram()));
            } else {
                senderScripts.addNull();
            }
        }

        ArrayNode events = reply.putArray("events");
        for (LogInfo log : fx.logs) {
            decodeEvent(log).ifPresent(events::add);
        }

        ArrayNode credits = reply.putArray("credits");
        co.rsk.core.Coin bridgeBalanceAfter = fx.repository.getBalance(PrecompiledContracts.BRIDGE_ADDR);
        co.rsk.core.Coin debited = bridgeBalanceBefore.subtract(bridgeBalanceAfter);
        if (debited.compareTo(co.rsk.core.Coin.ZERO) > 0) {
            // Find who received it: the destination named in the payload is the only candidate the fixture funds.
            RskAddress destination = destinationOf(payload);
            co.rsk.core.Coin received = fx.repository.getBalance(destination);
            ObjectNode credit = credits.addObject();
            credit.put("destination_hex", Hex.toHexString(destination.getBytes()));
            credit.put("amount_wei", received.asBigInteger());
            if (!received.equals(debited)) {
                credit.put("note", "bridge debited " + debited.asBigInteger() + " but destination received " + received.asBigInteger());
            }
        }

        ArrayNode releases = reply.putArray("releases");
        for (PegoutsWaitingForConfirmations.Entry entry : fx.pegoutsWaitingForConfirmations.getEntries()) {
            BtcTransaction release = entry.getBtcTransaction();
            ObjectNode r = releases.addObject();
            r.put("queue", "pegoutsWaitingForConfirmations");
            r.put("unsigned_tx_hex", Hex.toHexString(release.bitcoinSerialize()));
            r.put("txid", release.getHash().toString());
            // Input values from the actual spent prevouts (the peg-in's federation outputs)
            long inputValue = 0;
            for (TransactionInput input : release.getInputs()) {
                TransactionOutPoint outpoint = input.getOutpoint();
                if (!outpoint.getHash().equals(btcTx.getHash())) {
                    throw new IllegalStateException("release spends an unknown prevout " + outpoint);
                }
                inputValue += btcTx.getOutput(outpoint.getIndex()).getValue().getValue();
            }
            long outputValue = release.getOutputs().stream().mapToLong(o -> o.getValue().getValue()).sum();
            r.put("input_value_sats", inputValue);
            r.put("fee_sats", inputValue - outputValue);

            if (relay) {
                // Federation signs a copy of the queued release with the fixture keys (the Bridge's own
                // addSignature path needs federator RSK transactions; the signature content is identical).
                BtcTransaction signed = new BtcTransaction(PARAMS, release.bitcoinSerialize());
                for (int i = 0; i < signed.getInputs().size(); i++) {
                    BitcoinTestUtils.signLegacyTransactionInputFromP2shMultiSig(signed, i, fx.activeFedSigners);
                }
                String signedHex = Hex.toHexString(signed.bitcoinSerialize());
                r.put("signed_tx_hex", signedHex);
                r.put("signed_txid", signed.getHash().toString());

                ObjectNode bitcoinCore = reply.putObject("bitcoin_core");
                JsonNode networkInfo = core.rpc("getnetworkinfo");
                bitcoinCore.put("version", networkInfo.get("subversion").asText() + " (" + networkInfo.get("version").asLong() + ")");
                bitcoinCore.put("acceptnonstdtxn", core.acceptNonStdTxn());
                ArrayNode acceptance = bitcoinCore.putArray("testmempoolaccept");
                for (JsonNode verdict : core.rpc("testmempoolaccept", List.of(signedHex))) {
                    ObjectNode a = acceptance.addObject();
                    a.put("txid", verdict.get("txid").asText());
                    a.put("allowed", verdict.get("allowed").asBoolean());
                    if (verdict.has("reject-reason")) {
                        a.put("reject_reason", verdict.get("reject-reason").asText());
                    }
                    if (verdict.has("fees")) {
                        a.set("fees", verdict.get("fees"));
                    }
                }
            }
        }

        boolean credited = credits.size() > 0;
        boolean unrefundable = false;
        for (JsonNode e : events) {
            if ("unrefundable_pegin".equals(e.get("name").asText())) {
                unrefundable = true;
            }
        }
        reply.put("outcome", credited ? "registered" : (releases.size() > 0 ? "refunded" : (unrefundable ? "unrefundable" : "ignored")));
        reply.put("activation_wiring", "switch: all rules vs all-but-v2; boundary-height wiring untested");
        return reply;
    }

    private static RskAddress destinationOf(byte[] payload) {
        return new RskAddress(Arrays.copyOfRange(payload, 5, 25));
    }

    // ---------------------------------------------------------------- sender fixtures

    private static SenderInput senderInput(String kind, int index) {
        BtcECKey key = BitcoinTestUtils.getBtcEcKeyFromSeed("sender-" + kind + "-" + index);
        List<BtcECKey> multisigKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"ms1-" + kind + index, "ms2-" + kind + index, "ms3-" + kind + index}, true);
        Script redeem = ScriptBuilder.createRedeemScript(2, multisigKeys);
        return switch (kind) {
            case "p2pkh" -> new SenderInput(ScriptBuilder.createOutputScript(key.toAddress(PARAMS)), true, List.of(key), null, kind);
            case "p2sh_p2wpkh" -> {
                byte[] redeemP2wpkh = ByteUtil.merge(new byte[]{0x00, 0x14}, key.getPubKeyHash());
                yield new SenderInput(ScriptBuilder.createP2SHOutputScript(Utils.sha256hash160(redeemP2wpkh)), true, List.of(key), new Script(redeemP2wpkh), kind);
            }
            case "p2sh_multisig" -> new SenderInput(ScriptBuilder.createP2SHOutputScript(redeem), true, multisigKeys, redeem, kind);
            case "p2sh_p2wsh_multisig" -> new SenderInput(ScriptBuilder.createP2SHP2WSHOutputScript(redeem), true, multisigKeys, redeem, kind);
            case "p2wpkh" -> new SenderInput(new Script(ByteUtil.merge(new byte[]{0x00, 0x14}, key.getPubKeyHash())), false, List.of(key), null, kind);
            case "p2wsh" -> new SenderInput(new Script(ByteUtil.merge(new byte[]{0x00, 0x20}, Sha256Hash.hash(redeem.getProgram()))), false, multisigKeys, redeem, kind);
            case "p2tr" -> new SenderInput(new Script(ByteUtil.merge(new byte[]{0x51, 0x20}, Arrays.copyOfRange(key.getPubKey(), 1, 33))), false, List.of(key), null, kind);
            default -> throw new IllegalArgumentException("unknown sender kind " + kind);
        };
    }

    private static TransactionSignature sign(BtcECKey key, Sha256Hash sigHash) {
        return new TransactionSignature(key.sign(sigHash), BtcTransaction.SigHash.ALL, false);
    }

    /** Signs input {@code i} as the given kind, producing the scriptSig/witness shape the Bridge's lock-sender parsers see on-chain. */
    private static void finishInput(BtcTransaction tx, int i, SenderInput s) {
        Coin value = tx.getInput(i).getConnectedOutput().getValue();
        BtcECKey key = s.keys().get(0);
        switch (s.kind()) {
            case "p2pkh" -> {
                Sha256Hash h = tx.hashForSignature(i, s.fundingScriptPubKey(), BtcTransaction.SigHash.ALL, false);
                tx.getInput(i).setScriptSig(ScriptBuilder.createInputScript(sign(key, h), key));
            }
            case "p2sh_p2wpkh", "p2wpkh" -> {
                Script scriptCode = ScriptBuilder.createOutputScript(key.toAddress(PARAMS));
                Sha256Hash h = tx.hashForWitnessSignature(i, scriptCode, value, BtcTransaction.SigHash.ALL, false);
                TransactionWitness w = new TransactionWitness(2);
                w.setPush(0, sign(key, h).encodeToBitcoin());
                w.setPush(1, key.getPubKey());
                tx.setWitness(i, w);
                tx.getInput(i).setScriptSig(s.kind().equals("p2sh_p2wpkh")
                    ? new ScriptBuilder().data(s.redeemScript().getProgram()).build()
                    : new Script(new byte[]{}));
            }
            case "p2sh_multisig" -> {
                Sha256Hash h = tx.hashForSignature(i, s.redeemScript(), BtcTransaction.SigHash.ALL, false);
                List<TransactionSignature> sigs = List.of(sign(s.keys().get(0), h), sign(s.keys().get(1), h));
                tx.getInput(i).setScriptSig(ScriptBuilder.createP2SHMultiSigInputScript(sigs, s.redeemScript()));
            }
            case "p2sh_p2wsh_multisig", "p2wsh" -> {
                Sha256Hash h = tx.hashForWitnessSignature(i, s.redeemScript(), value, BtcTransaction.SigHash.ALL, false);
                TransactionWitness w = new TransactionWitness(4);
                w.setPush(0, new byte[]{});
                w.setPush(1, sign(s.keys().get(0), h).encodeToBitcoin());
                w.setPush(2, sign(s.keys().get(1), h).encodeToBitcoin());
                w.setPush(3, s.redeemScript().getProgram());
                tx.setWitness(i, w);
                tx.getInput(i).setScriptSig(s.kind().equals("p2sh_p2wsh_multisig")
                    ? new ScriptBuilder().data(ByteUtil.merge(new byte[]{0x00, 0x20}, Sha256Hash.hash(s.redeemScript().getProgram()))).build()
                    : new Script(new byte[]{}));
            }
            case "p2tr" -> {
                // bitcoinj-thin has no BIP340 signing; the Bridge does not validate input signatures, so a
                // 64-byte placeholder gives the on-chain key-path witness shape (one push).
                TransactionWitness w = new TransactionWitness(1);
                w.setPush(0, new byte[64]);
                tx.setWitness(i, w);
                tx.getInput(i).setScriptSig(new Script(new byte[]{}));
            }
            default -> throw new IllegalArgumentException(s.kind());
        }
    }

    // ---------------------------------------------------------------- event decoding

    private static Optional<ObjectNode> decodeEvent(LogInfo log) {
        for (BridgeEvents candidate : List.of(BridgeEvents.PEGIN_BTC, BridgeEvents.REJECTED_PEGIN,
            BridgeEvents.UNREFUNDABLE_PEGIN, BridgeEvents.RELEASE_REQUESTED)) {
            CallTransaction.Function event = candidate.getEvent();
            if (!Arrays.equals(event.encodeSignatureLong(), log.getTopics().get(0).getData())) {
                continue;
            }
            ObjectNode node = MAPPER.createObjectNode();
            node.put("name", event.name);
            Object[] data = event.decodeEventData(log.getData());
            switch (candidate) {
                case REJECTED_PEGIN -> node.put("reason", reasonName(RejectedPeginReason.values(), data[0]));
                case UNREFUNDABLE_PEGIN -> node.put("reason", reasonName(NonRefundablePeginReason.values(), data[0]));
                case RELEASE_REQUESTED -> {
                    node.put("btc_txid", Hex.toHexString(log.getTopics().get(2).getData()));
                    node.put("amount_sats", ((java.math.BigInteger) data[0]).longValueExact());
                }
                case PEGIN_BTC -> {
                    node.put("amount_sats", ((java.math.BigInteger) data[0]).longValueExact());
                    node.put("protocol_version", ((java.math.BigInteger) data[1]).intValueExact());
                }
                default -> { }
            }
            return Optional.of(node);
        }
        return Optional.empty();
    }

    private static String reasonName(Enum<?>[] values, Object decoded) {
        int code = ((java.math.BigInteger) decoded).intValueExact();
        for (Enum<?> v : values) {
            int value = v instanceof RejectedPeginReason r ? r.getValue() : ((NonRefundablePeginReason) v).getValue();
            if (value == code) {
                return v.name();
            }
        }
        return "UNKNOWN_" + code;
    }

    // ---------------------------------------------------------------- Bridge fixture (mirrors BridgeSupportRegisterBtcTransactionTest)

    private static final class Fixture {
        final ActivationConfig.ForBlock activations;
        final Federation activeFederation;
        final List<BtcECKey> activeFedSigners;
        final Repository repository = BridgeSupportTestUtil.createRepository();
        final List<LogInfo> logs = new ArrayList<>();
        final BridgeStorageProvider bridgeStorageProvider = mock(BridgeStorageProvider.class);
        final FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        final BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        final PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        final List<UTXO> activeFederationUtxos = new ArrayList<>();
        final Block rskExecutionBlock = mock(Block.class);
        final Transaction rskTx = mock(Transaction.class);
        final int heightAtWhichToStartUsingPegoutIndex;

        Fixture(ActivationConfig.ForBlock activations) throws IOException {
            this.activations = activations;
            List<BtcECKey> activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"fa07", "fa08", "fa09", "fa10", "fa11"}, true);
            activeFedSigners.sort(BtcECKey.PUBKEY_COMPARATOR);
            this.activeFedSigners = activeFedSigners;
            FederationArgs activeFedArgs = new FederationArgs(
                FederationTestUtils.getFederationMembersWithBtcKeys(activeFedSigners), Instant.ofEpochMilli(1000L), 2L, PARAMS);
            activeFederation = FederationFactory.buildP2shErpFederation(
                activeFedArgs, FEDERATION_CONSTANTS.getErpFedPubKeysList(), FEDERATION_CONSTANTS.getErpFedActivationDelay());

            when(bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());
            when(bridgeStorageProvider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);
            when(federationStorageProvider.getOldFederationBtcUTXOs()).thenReturn(new ArrayList<>());
            when(federationStorageProvider.getNewFederationBtcUTXOs(any(NetworkParameters.class), any(ActivationConfig.ForBlock.class))).thenReturn(activeFederationUtxos);
            when(federationStorageProvider.getNewFederation(any(FederationConstants.class), any(ActivationConfig.ForBlock.class))).thenReturn(activeFederation);

            long blockNumber = activeFederation.getCreationBlockNumber()
                + FEDERATION_CONSTANTS.getFederationActivationAge(activations)
                + FEDERATION_CONSTANTS.getFundsMigrationAgeSinceActivationBegin() + 1;
            when(rskExecutionBlock.getNumber()).thenReturn(blockNumber);
            when(rskTx.getHash()).thenReturn(PegTestUtils.createHash3(1));

            heightAtWhichToStartUsingPegoutIndex = BRIDGE_CONSTANTS.getBtcHeightWhenPegoutTxIndexActivates()
                + BRIDGE_CONSTANTS.getPegoutTxIndexGracePeriodInBtcBlocks();

            // Liquidity: the Bridge holds the whole supply so credits never fail for lack of funds
            repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, co.rsk.core.Coin.fromBitcoin(BRIDGE_CONSTANTS.getMaxRbtc()));
        }

        PartialMerkleTree createPmtAndMockBlockStore(BtcTransaction btcTransaction, int height) throws BlockStoreException {
            PartialMerkleTree pmt = new PartialMerkleTree(PARAMS, new byte[]{0x3f}, Collections.singletonList(btcTransaction.getHash()), 1);
            Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
            co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
                PARAMS, 1, BitcoinTestUtils.createHash(1), blockMerkleRoot, 1, 1, 1, new ArrayList<>());
            StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);
            BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
            co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
                PARAMS, 1, BitcoinTestUtils.createHash(2), Sha256Hash.of(new byte[]{1}), 1, 1, 1, new ArrayList<>());
            int headHeight = height + BRIDGE_CONSTANTS.getBtc2RskMinimumAcceptableConfirmations();
            when(btcBlockStore.getChainHead()).thenReturn(new StoredBlock(headBlock, new BigInteger("0"), headHeight));
            when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
            when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);
            BridgeSupportTestUtil.mockChainOfStoredBlocks(btcBlockStore, registerHeader, headHeight, height);
            return pmt;
        }

        PartialMerkleTree createPmtWithWitness(BtcTransaction btcTx) {
            PartialMerkleTree pmtWithWitness = new PartialMerkleTree(PARAMS, new byte[]{0x3f}, List.of(btcTx.getHash(true)), 1);
            Sha256Hash witnessMerkleRoot = pmtWithWitness.getTxnHashAndMerkleRoot(new ArrayList<>());
            when(bridgeStorageProvider.getCoinbaseInformation(any())).thenReturn(new CoinbaseInformation(witnessMerkleRoot));
            return pmtWithWitness;
        }

        BridgeSupport buildBridgeSupport() {
            StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);
            FeePerKbSupport feePerKbSupport = new FeePerKbSupportImpl(
                BRIDGE_CONSTANTS.getFeePerKbConstants(), new FeePerKbStorageProviderImpl(bridgeStorageAccessor));

            LockWhitelist lockWhitelist = mock(LockWhitelist.class);
            WhitelistStorageProvider whitelistStorageProvider = mock(WhitelistStorageProvider.class);
            when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
            when(whitelistStorageProvider.getLockWhitelist(activations, PARAMS)).thenReturn(lockWhitelist);

            FederationSupport federationSupport = FederationSupportBuilder.builder()
                .withFederationConstants(FEDERATION_CONSTANTS)
                .withFederationStorageProvider(federationStorageProvider)
                .withActivations(activations)
                .withRskExecutionBlock(rskExecutionBlock)
                .build();

            return BridgeSupportBuilder.builder()
                .withBtcBlockStoreFactory(mockFactory)
                .withBridgeConstants(BRIDGE_CONSTANTS)
                .withRepository(repository)
                .withProvider(bridgeStorageProvider)
                .withActivations(activations)
                .withSignatureCache(new BlockTxSignatureCache(new ReceivedTxSignatureCache()))
                .withEventLogger(new BridgeEventLoggerImpl(BRIDGE_CONSTANTS, activations, logs))
                .withBtcLockSenderProvider(new BtcLockSenderProvider())
                .withPeginInstructionsProvider(new co.rsk.peg.pegininstructions.PeginInstructionsProvider())
                .withExecutionBlock(rskExecutionBlock)
                .withFeePerKbSupport(feePerKbSupport)
                .withWhitelistSupport(new WhitelistSupportImpl(BRIDGE_CONSTANTS.getWhitelistConstants(), whitelistStorageProvider, activations, mock(SignatureCache.class)))
                .withFederationSupport(federationSupport)
                .build();
        }
    }

    // ---------------------------------------------------------------- Bitcoin Core (regtest) for relay cases

    /** Minimal JSON-RPC client. The node is launched with -acceptnonstdtxn=0, which this adapter reports as the effective policy. */
    static final class BitcoinCore {
        private final String url;
        private final String auth;
        private final HttpClient http = HttpClient.newHttpClient();
        private final String walletUrl;

        BitcoinCore(String url, String user, String password) {
            this.url = url;
            this.auth = "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
            this.walletUrl = url + "/wallet/relay";
        }

        static BitcoinCore fromEnvironment() {
            String url = System.getenv().getOrDefault("BTC_RPC_URL", "http://btc-regtest-relay:18443");
            return new BitcoinCore(url, System.getenv().getOrDefault("BTC_RPC_USER", "poc"), System.getenv().getOrDefault("BTC_RPC_PASS", "poc"));
        }

        boolean acceptNonStdTxn() {
            return Boolean.parseBoolean(System.getenv().getOrDefault("BTC_ACCEPT_NONSTD_TXN", "false"));
        }

        JsonNode rpc(String method, Object... params) throws Exception {
            return call(url, method, params);
        }

        JsonNode wallet(String method, Object... params) throws Exception {
            return call(walletUrl, method, params);
        }

        private JsonNode call(String endpoint, String method, Object... params) throws Exception {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("jsonrpc", "1.0");
            body.put("id", "pegin-v2");
            body.put("method", method);
            body.set("params", MAPPER.valueToTree(params));
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Authorization", auth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode node = MAPPER.readTree(res.body());
            if (node.hasNonNull("error")) {
                throw new IllegalStateException("bitcoind " + method + ": " + node.get("error"));
            }
            return node.get("result");
        }

        private void ensureFundedWallet() throws Exception {
            boolean loaded = false;
            for (JsonNode w : rpc("listwallets")) {
                loaded |= "relay".equals(w.asText());
            }
            if (!loaded) {
                try {
                    rpc("createwallet", "relay");
                } catch (IllegalStateException alreadyExists) {
                    rpc("loadwallet", "relay");
                }
            }
            // Mine to a bech32m address so every spendable coin is a taproot output
            if (wallet("getbalance").asDouble() < 5) {
                String mineTo = wallet("getnewaddress", "", "bech32m").asText();
                rpc("generatetoaddress", 110, mineTo);
            }
        }

        /** Builds the peg-in from a taproot-owned coin, has Core sign and mine it, and returns the mined transaction. */
        BtcTransaction buildAndMinePegin(List<Coin> federationOutputs, Address federationAddress, byte[] payload) throws Exception {
            ensureFundedWallet();
            Coin total = federationOutputs.stream().reduce(Coin.ZERO, Coin::add);
            Coin needed = total.add(Coin.CENT); // leaves room for the fee and a change output

            JsonNode chosen = null;
            for (JsonNode utxo : wallet("listunspent", 1, 9_999_999)) {
                boolean taproot = utxo.has("desc") && utxo.get("desc").asText().startsWith("tr(");
                if (taproot && Coin.parseCoin(utxo.get("amount").asText()).compareTo(needed) >= 0) {
                    chosen = utxo;
                    break;
                }
            }
            if (chosen == null) {
                throw new IllegalStateException("no taproot coin large enough for " + needed);
            }
            Coin inputValue = Coin.parseCoin(chosen.get("amount").asText());

            BtcTransaction tx = new BtcTransaction(PARAMS);
            tx.addInput(Sha256Hash.wrap(chosen.get("txid").asText()), chosen.get("vout").asLong(), new Script(new byte[]{}));
            for (Coin value : federationOutputs) {
                tx.addOutput(value, federationAddress);
            }
            tx.addOutput(Coin.ZERO, ScriptBuilder.createOpReturnScript(payload));
            String changeAddress = wallet("getnewaddress", "", "bech32m").asText();
            byte[] changeScript = Hex.decode(wallet("getaddressinfo", changeAddress).get("scriptPubKey").asText());
            Coin fee = Coin.valueOf(5_000);
            tx.addOutput(inputValue.subtract(total).subtract(fee), new Script(changeScript));

            JsonNode signed = wallet("signrawtransactionwithwallet", Hex.toHexString(tx.bitcoinSerialize()));
            if (!signed.get("complete").asBoolean()) {
                throw new IllegalStateException("Core could not sign the peg-in: " + signed);
            }
            String signedHex = signed.get("hex").asText();
            String txid = rpc("sendrawtransaction", signedHex).asText();
            rpc("generatetoaddress", 1, wallet("getnewaddress", "", "bech32m").asText());

            BtcTransaction mined = new BtcTransaction(PARAMS, Hex.decode(signedHex));
            if (!mined.getHash().toString().equals(txid)) {
                throw new IllegalStateException("txid mismatch: bitcoinj " + mined.getHash() + " vs core " + txid);
            }
            return mined;
        }
    }
}
