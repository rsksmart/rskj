package co.rsk.peg.pegininstructions;

import static org.junit.jupiter.api.Assertions.*;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.core.RskAddress;
import co.rsk.peg.PegTestUtils;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class PeginInstructionsVersion2Test {
    private final NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
    private final RskAddress rskDestination = new RskAddress(Hex.decode("0101010101010101010101010101010101010101"));

    private static byte[] program(int length, int fill) {
        byte[] program = new byte[length];
        Arrays.fill(program, (byte) fill);
        return program;
    }

    private static byte[] payloadFor(Script opReturnScript) {
        return opReturnScript.getChunks().get(1).data;
    }

    @Test
    void getProtocolVersion() {
        assertEquals(2, new PeginInstructionsVersion2(params).getProtocolVersion());
    }

    @ParameterizedTest
    @EnumSource(PeginRefundScriptType.class)
    void parse_eachTypeWithCorrectProgramLength_yieldsExpectedOutputScript(PeginRefundScriptType type) throws PeginInstructionsException {
        // Arrange
        byte[] program = program(type.getProgramLength(), 0xab);
        byte[] payload = payloadFor(PegTestUtils.createOpReturnScriptForRskV2(rskDestination, Optional.of(type), program));
        assertEquals(type.getProgramLength() == 20 ? 46 : 58, payload.length);

        // Act
        PeginInstructionsVersion2 instructions = new PeginInstructionsVersion2(params);
        instructions.parse(payload);

        // Assert
        assertEquals(rskDestination, instructions.getRskDestinationAddress());
        assertEquals(Optional.of(type), instructions.getBtcRefundScriptType());
        Script expected = switch (type) {
            case P2PKH -> ScriptBuilder.createOutputScript(new Address(params, program));
            case P2SH -> ScriptBuilder.createP2SHOutputScript(program);
            case P2WPKH, P2WSH -> new Script(Hex.decode("00" + (type.getProgramLength() == 20 ? "14" : "20") + Hex.toHexString(program)));
            case P2TR -> new Script(Hex.decode("5120" + Hex.toHexString(program)));
        };
        assertEquals(expected, instructions.getBtcRefundScript().orElseThrow());
    }

    @Test
    void parse_noRefund_yieldsNoScript() throws PeginInstructionsException {
        byte[] payload = payloadFor(PegTestUtils.createOpReturnScriptForRskV2(rskDestination, Optional.empty(), new byte[0]));
        assertEquals(25, payload.length);

        PeginInstructionsVersion2 instructions = new PeginInstructionsVersion2(params);
        instructions.parse(payload);

        assertEquals(rskDestination, instructions.getRskDestinationAddress());
        assertTrue(instructions.getBtcRefundScript().isEmpty());
        assertTrue(instructions.getBtcRefundAddress().isEmpty());
    }

    @Test
    void getBtcRefundAddress_onlyForLegacyTypes() throws PeginInstructionsException {
        BtcECKey key = new BtcECKey();
        Address p2pkh = key.toAddress(params);

        PeginInstructionsVersion2 legacy = new PeginInstructionsVersion2(params);
        legacy.parse(payloadFor(PegTestUtils.createOpReturnScriptForRskV2(rskDestination, Optional.of(PeginRefundScriptType.P2PKH), p2pkh.getHash160())));
        assertEquals(Optional.of(p2pkh), legacy.getBtcRefundAddress());

        PeginInstructionsVersion2 witness = new PeginInstructionsVersion2(params);
        witness.parse(payloadFor(PegTestUtils.createOpReturnScriptForRskV2(rskDestination, Optional.of(PeginRefundScriptType.P2WPKH), p2pkh.getHash160())));
        assertTrue(witness.getBtcRefundAddress().isEmpty(), "witness programs have no Address form in bitcoinj-thin");
        assertTrue(witness.getBtcRefundScript().isPresent());
    }

    private static Stream<Arguments> invalidPayloads() {
        return Stream.of(
            // wrong program length for a type: 58-byte payload with a 20-byte type
            Arguments.of("p2wpkh with 32-byte program", PeginRefundScriptType.P2WPKH, program(32, 1)),
            Arguments.of("p2pkh with 32-byte program", PeginRefundScriptType.P2PKH, program(32, 1)),
            // 46-byte payload with a 32-byte type
            Arguments.of("p2tr with 20-byte program", PeginRefundScriptType.P2TR, program(20, 1)),
            Arguments.of("p2wsh with 20-byte program", PeginRefundScriptType.P2WSH, program(20, 1))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPayloads")
    void parse_programLengthNotMatchingType_throws(String name, PeginRefundScriptType type, byte[] program) {
        byte[] payload = payloadFor(PegTestUtils.createOpReturnScriptForRskV2(rskDestination, Optional.of(type), program));
        PeginInstructionsVersion2 instructions = new PeginInstructionsVersion2(params);
        assertThrows(PeginInstructionsParseException.class, () -> instructions.parse(payload));
    }

    @Test
    void parse_unknownType_throws() {
        byte[] payload = payloadFor(PegTestUtils.createOpReturnScriptForRskV2(rskDestination, Optional.of(PeginRefundScriptType.P2WPKH), program(20, 1)));
        payload[25] = 0x06;
        PeginInstructionsVersion2 instructions = new PeginInstructionsVersion2(params);
        assertThrows(PeginInstructionsParseException.class, () -> instructions.parse(payload));

        payload[25] = 0x00;
        assertThrows(PeginInstructionsParseException.class, () -> instructions.parse(payload));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {5, 24, 26, 45, 47, 57, 59, 80})
    void parse_invalidLengths_throw(int length) {
        PeginInstructionsVersion2 instructions = new PeginInstructionsVersion2(params);
        assertThrows(PeginInstructionsParseException.class, () -> instructions.parse(new byte[length]));
    }
}
