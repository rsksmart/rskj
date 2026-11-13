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
package org.ethereum.core.transaction.encoder;

import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Test;

import static org.ethereum.core.transaction.encoder.EncoderTestSupport.CHAIN_ID;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.unsignedLegacy;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.unsignedType1;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.unsignedType2;
import static org.ethereum.core.transaction.encoder.EncoderTestSupport.unsignedType4;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for {@link TransactionEncoderFactory} routing. A wrong route silently
 * produces valid-looking bytes with the wrong layout, so each supported type is pinned.
 */
class TransactionEncoderFactoryTest {

    @Test
    void getEncoder_legacy_returnsType0Encoder() {
        assertInstanceOf(Type0TransactionEncoder.class,
                TransactionEncoderFactory.getEncoder(unsignedLegacy(CHAIN_ID)));
    }

    @Test
    void getEncoder_legacyChainIdZero_returnsType0Encoder() {
        assertInstanceOf(Type0TransactionEncoder.class,
                TransactionEncoderFactory.getEncoder(unsignedLegacy((byte) 0)));
    }

    @Test
    void getEncoder_type1_returnsType1Encoder() {
        assertInstanceOf(Type1TransactionEncoder.class,
                TransactionEncoderFactory.getEncoder(unsignedType1()));
    }

    @Test
    void getEncoder_type2_returnsType2Encoder() {
        assertInstanceOf(Type2TransactionEncoder.class,
                TransactionEncoderFactory.getEncoder(unsignedType2()));
    }

    @Test
    void getEncoder_type4_returnsType4Encoder() {
        assertInstanceOf(Type4TransactionEncoder.class,
                TransactionEncoderFactory.getEncoder(unsignedType4()));
    }

    @Test
    void getEncoder_reusesSingletonInstances() {
        Transaction legacy = unsignedLegacy(CHAIN_ID);
        Transaction type1 = unsignedType1();
        Transaction type2 = unsignedType2();
        Transaction type4 = unsignedType4();

        assertSame(TransactionEncoderFactory.getEncoder(legacy), TransactionEncoderFactory.getEncoder(legacy));
        assertSame(TransactionEncoderFactory.getEncoder(type1), TransactionEncoderFactory.getEncoder(type1));
        assertSame(TransactionEncoderFactory.getEncoder(type2), TransactionEncoderFactory.getEncoder(type2));
        assertSame(TransactionEncoderFactory.getEncoder(type4), TransactionEncoderFactory.getEncoder(type4));
    }
}
