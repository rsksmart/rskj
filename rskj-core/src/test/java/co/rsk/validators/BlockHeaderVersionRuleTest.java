/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.validators;

import co.rsk.blockchain.utils.BlockGenerator;
import com.typesafe.config.ConfigFactory;
import org.ethereum.TestUtils;
import org.ethereum.config.SystemProperties;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockHeaderVersionRuleTest {

    private static final long BLOCK_NUMBER = 1L;

    /** Where the version byte sits in a full (non-compressed) encoding: after the 16 base fields and the ummRoot. */
    private static final int VERSION_FIELD_INDEX = 17;

    /** A config that requires exactly this header version at every height above genesis. */
    private static ActivationConfig configRequiring(int version) {
        switch (version) {
            case 0:
                return ActivationConfigsForTest.allBut(ConsensusRule.RSKIP351, ConsensusRule.RSKIP144, ConsensusRule.RSKIP535);
            case 1:
                return ActivationConfigsForTest.allBut(ConsensusRule.RSKIP535);
            default:
                return ActivationConfigsForTest.all();
        }
    }

    /**
     * A config with every upgrade active from height 0 except RSKIP351 and RSKIP535, which activate at the
     * given heights: version 0 below {@code rskip351Height}, version 1 from there, version 2 from
     * {@code rskip535Height}.
     */
    private static ActivationConfig activatingRskip351At(long rskip351Height, long rskip535Height) {
        Map<ConsensusRule, Long> heights = EnumSet.allOf(ConsensusRule.class).stream()
                .collect(Collectors.toMap(Function.identity(), ignored -> 0L));
        heights.put(ConsensusRule.RSKIP351, rskip351Height);
        heights.put(ConsensusRule.RSKIP535, rskip535Height);
        return new ActivationConfig(heights, new HashMap<>());
    }

    /** The real testnet config: RSKIP351 activates at 7,139,600 and RSKIP535 at 7,604,200. */
    private static ActivationConfig testnet() {
        return ActivationConfig.read(ConfigFactory.load("config/testnet").getConfig(SystemProperties.PROPERTY_BLOCKCHAIN_CONFIG));
    }

    private static BlockHeader headerOfVersion(int version) {
        return headerOfVersion(version, BLOCK_NUMBER);
    }

    /** A real header at {@code number} built the way the miner builds it under the config that requires {@code version}. */
    private static BlockHeader headerOfVersion(int version, long number) {
        BlockHeader header = new BlockHeaderBuilder(configRequiring(version)).setNumber(number).build();
        assertEquals((byte) version, header.getVersion());
        return header;
    }

    @Test
    void rejectsANullActivationConfig() {
        assertThrows(NullPointerException.class, () -> new BlockHeaderVersionRule(null));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void acceptsTheVersionTheHeightRequires(int version) {
        BlockHeaderVersionRule rule = new BlockHeaderVersionRule(configRequiring(version));

        assertTrue(rule.isValid(headerOfVersion(version)));
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "0, 2", "1, 0", "1, 2", "2, 0", "2, 1"})
    void rejectsEveryOtherVersion(int requiredVersion, int headerVersion) {
        BlockHeaderVersionRule rule = new BlockHeaderVersionRule(configRequiring(requiredVersion));

        assertFalse(rule.isValid(headerOfVersion(headerVersion)));
    }

    @ParameterizedTest(name = "height {0} accepts version {1} and rejects version {2}")
    @CsvSource({"9, 0, 1", "10, 1, 0", "19, 1, 2", "20, 2, 1"})
    void switchesTheRequiredVersionExactlyAtEachActivationHeight(long height, int acceptedVersion, int rejectedVersion) {
        // RSKIP351 from 10 and RSKIP535 from 20: the last block before each activation keeps the old
        // version and the activation block itself already carries the new one
        BlockHeaderVersionRule rule = new BlockHeaderVersionRule(activatingRskip351At(10, 20));

        assertTrue(rule.isValid(headerOfVersion(acceptedVersion, height)));
        assertFalse(rule.isValid(headerOfVersion(rejectedVersion, height)));
    }

    @ParameterizedTest(name = "testnet height {0} accepts version {1} and rejects version {2}")
    @CsvSource({"7139599, 0, 1", "7139600, 1, 0", "7604199, 1, 2", "7604200, 2, 1"})
    void switchesTheRequiredVersionAtTheTestnetActivationHeights(long height, int acceptedVersion, int rejectedVersion) {
        BlockHeaderVersionRule rule = new BlockHeaderVersionRule(testnet());

        assertTrue(rule.isValid(headerOfVersion(acceptedVersion, height)));
        assertFalse(rule.isValid(headerOfVersion(rejectedVersion, height)));
    }

    @ParameterizedTest(name = "height {0}: a wire version byte of {1} still decodes, and only the rule rejects it")
    @CsvSource({"19, 2", "20, 1"})
    void rejectsAWireVersionTheDecoderStillAcceptsOnEitherSideOfTheRskip535Boundary(long height, int wireVersion) {
        // BlockFactory takes the version from the wire and picks the header class from it: a version 2 byte in the
        // RSKIP351-only window yields a V2 header with no baseEvent, a version 1 byte at a RSKIP535 height yields a V1
        // header. Nothing else compares that byte with the activation state; this rule is the only check.
        ActivationConfig config = activatingRskip351At(10, 20);
        BlockFactory factory = new BlockFactory(config);
        BlockHeaderVersionRule rule = new BlockHeaderVersionRule(config);
        byte[] compliant = new BlockHeaderBuilder(config)
                .setNumber(height)
                .setCoinbase(TestUtils.generateAddress("coinbase"))
                .build()
                .getFullEncoded();

        BlockHeader decoded = factory.decodeHeader(withVersionByte(compliant, (byte) wireVersion), false);

        assertEquals((byte) wireVersion, decoded.getVersion());
        assertEquals(height, decoded.getNumber());
        assertTrue(rule.isValid(factory.decodeHeader(compliant, false)));
        assertFalse(rule.isValid(decoded));
    }

    /** The same header bytes with the version element replaced; every header field is a byte-string element. */
    private static byte[] withVersionByte(byte[] encodedHeader, byte version) {
        RLPList fields = RLP.decodeList(encodedHeader);
        byte[][] encodedFields = new byte[fields.size()][];
        for (int i = 0; i < fields.size(); i++) {
            encodedFields[i] = RLP.encodeElement(fields.get(i).getRLPRawData());
        }
        assertArrayEquals(encodedHeader, RLP.encodeList(encodedFields), "re-encoding reproduces the header");
        assertEquals(1, fields.get(VERSION_FIELD_INDEX).getRLPData().length, "the slot holds a one-byte version");
        encodedFields[VERSION_FIELD_INDEX] = RLP.encodeByte(version);
        return RLP.encodeList(encodedFields);
    }

    @Test
    void validatesTheHeaderOfABlock() {
        Block block = mock(Block.class);
        when(block.getHeader()).thenReturn(headerOfVersion(1));

        assertTrue(new BlockHeaderVersionRule(configRequiring(1)).isValid(block));
        assertFalse(new BlockHeaderVersionRule(configRequiring(2)).isValid(block));
    }

    @Test
    void skipsGenesisWhateverVersionTheConfigRequiresAtHeightZero() {
        // all() activates RSKIP351 and RSKIP535 at height 0, so it requires version 2 there,
        // while the genesis header is always version 0.
        BlockHeader genesis = new BlockGenerator().getGenesisBlock().getHeader();
        assertEquals((byte) 0, genesis.getVersion());
        assertEquals((byte) 2, ActivationConfigsForTest.all().getHeaderVersion(genesis.getNumber()));

        assertTrue(new BlockHeaderVersionRule(ActivationConfigsForTest.all()).isValid(genesis));
    }
}
