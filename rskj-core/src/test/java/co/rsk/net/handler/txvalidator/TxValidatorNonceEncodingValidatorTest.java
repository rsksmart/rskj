/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.net.handler.txvalidator;

import org.ethereum.core.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TxValidatorNonceEncodingValidatorTest {

    private TxValidatorNonceEncodingValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TxValidatorNonceEncodingValidator();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonceEncodingCases")
    void validateNonceEncoding(String description, byte[] nonce, boolean expectedValid) {
        Transaction tx = Mockito.mock(Transaction.class);
        Mockito.when(tx.getNonce()).thenReturn(nonce);

        assertEquals(expectedValid,
                validator.validate(tx, null, null, null, Long.MAX_VALUE, false).transactionIsValid(),
                description);
    }

    private static Stream<Arguments> nonceEncodingCases() {
        return Stream.of(
                // Valid cases
                Arguments.of("null nonce", null, true),
                Arguments.of("empty nonce (byte[0])", new byte[0], true),
                Arguments.of("single byte zero", new byte[]{0x00}, true),
                Arguments.of("single byte non-zero", new byte[]{0x01}, true),
                Arguments.of("two bytes, no leading zero", new byte[]{0x01, 0x00}, true),
                Arguments.of("eight bytes, no leading zeros", new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08}, true),

                // Invalid cases
                Arguments.of("nine bytes (all zeros)", new byte[9], false),
                Arguments.of("nine bytes (non-zero first byte)", new byte[]{0x01, 0, 0, 0, 0, 0, 0, 0, 0}, false),
                Arguments.of("two bytes with leading zero", new byte[]{0x00, 0x01}, false),
                Arguments.of("three bytes with leading zeros", new byte[]{0x00, 0x00, 0x05}, false)
        );
    }
}
