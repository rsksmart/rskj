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

package org.ethereum.config.blockchain.upgrades;

import co.rsk.bitcoinj.core.Sha256Hash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PegoutsOverwritesTest {
    private static final String HASH = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
    private static final String FORMAT_ERROR = String.format(
        "'blockchain.config.%s' has wrong format. Expected SHA256HEX@LONG.",
        ActivationConfig.PEGOUTS_OVERWRITES_RULES
    );

    @Test
    void pegoutRefParseValidValue() {
        PegoutsOverwrites.PegoutRef pegoutRef = PegoutsOverwrites.PegoutRef.parse(HASH + "@123");

        assertEquals(Sha256Hash.wrap(HASH), pegoutRef.btcTxHash());
        assertEquals(123L, pegoutRef.rskBlock());
    }

    @Test
    void pegoutRefToStringReturnsParseableValue() {
        PegoutsOverwrites.PegoutRef pegoutRef = new PegoutsOverwrites.PegoutRef(Sha256Hash.wrap(HASH), 123L);

        assertEquals(HASH + "@123", pegoutRef.toString());
        assertEquals(pegoutRef, PegoutsOverwrites.PegoutRef.parse(pegoutRef.toString()));
    }

    @Test
    void pegoutRefParseRejectsWrongFormat() {
        assertWrongFormat(null);
        assertWrongFormat("");
        assertWrongFormat(HASH);
        assertWrongFormat(HASH + "@");
        assertWrongFormat("@123");
        assertWrongFormat(HASH + "@123@456");
    }

    @Test
    void pegoutRefParseRejectsInvalidHash() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> PegoutsOverwrites.PegoutRef.parse("not-a-hash@123")
        );

        assertTrue(exception.getMessage().contains("'blockchain.config." + ActivationConfig.PEGOUTS_OVERWRITES_RULES + "'"));
        assertTrue(exception.getMessage().contains("TX hash 'not-a-hash' is invalid"));
    }

    @Test
    void pegoutRefParseRejectsInvalidBlockNumber() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> PegoutsOverwrites.PegoutRef.parse(HASH + "@not-a-long")
        );

        assertTrue(exception.getMessage().contains("'blockchain.config." + ActivationConfig.PEGOUTS_OVERWRITES_RULES + "'"));
        assertTrue(exception.getMessage().contains("block number 'not-a-long' is invalid"));
    }

    private static void assertWrongFormat(String value) {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> PegoutsOverwrites.PegoutRef.parse(value)
        );

        assertEquals(FORMAT_ERROR, exception.getMessage());
    }
}
