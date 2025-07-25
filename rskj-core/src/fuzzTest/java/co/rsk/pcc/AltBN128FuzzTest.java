/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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
package co.rsk.pcc;

import co.rsk.pcc.altBN128.impls.AbstractAltBN128;
import co.rsk.pcc.altBN128.impls.GoAltBN128;
import co.rsk.pcc.altBN128.impls.JavaAltBN128;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import org.junit.jupiter.api.Tag;

class AltBN128FuzzTest {
    @Tag("AltBN128FuzzAdd")
    @FuzzTest
    void fuzzAdd(FuzzedDataProvider data) {
        AbstractAltBN128 nbn = new GoAltBN128();
        AbstractAltBN128 jbn = new JavaAltBN128();
        byte[] input = data.consumeRemainingAsBytes();
        long startTime;
        int nRes;
        int jRes;
        
        startTime = System.currentTimeMillis();
        nRes = nbn.add(input, input.length);
        if (System.currentTimeMillis() - startTime > 60_000) {
            Assertions.fail("Timtout nRes");
        }
        
        startTime = System.currentTimeMillis();
        jRes = jbn.add(input, input.length);
        if (System.currentTimeMillis() - startTime > 60_000) {
            Assertions.fail("Timtout jRes");
        }
        
        assertEquals(nRes, jRes);
    }

    @Tag("AltBN128FuzzMul")
    @FuzzTest
    void fuzzMul(FuzzedDataProvider data) {
        AbstractAltBN128 nbn = new GoAltBN128();
        AbstractAltBN128 jbn = new JavaAltBN128();
        byte[] input = data.consumeRemainingAsBytes();
        long startTime;
        int nRes;
        int jRes;

        startTime = System.currentTimeMillis();
        nRes = nbn.mul(input, input.length);
        if (System.currentTimeMillis() - startTime > 60_000) {
            Assertions.fail("Timtout nRes");
        }

        startTime = System.currentTimeMillis();
        jRes = jbn.mul(input, input.length);
        if (System.currentTimeMillis() - startTime > 60_000) {
            Assertions.fail("Timtout jRes");
        }

        assertEquals(nRes, jRes);
    }

    @Tag("AltBN128FuzzPairing")
    @FuzzTest
    void fuzzPairing(FuzzedDataProvider data) {
        AbstractAltBN128 nbn = new GoAltBN128();
        AbstractAltBN128 jbn = new JavaAltBN128();
        byte[] input = data.consumeRemainingAsBytes();
        long startTime;
        int nRes;
        int jRes;
        
        startTime = System.currentTimeMillis();
        nRes = nbn.pairing(input, input.length);
        if (System.currentTimeMillis() - startTime > 60_000) {
            Assertions.fail("Timtout nRes");
        }

        startTime = System.currentTimeMillis();
        jRes = jbn.pairing(input, input.length);
        if (System.currentTimeMillis() - startTime > 60_000) {
            Assertions.fail("Timtout jRes");
        }

        assertEquals(nRes, jRes);
    }
}
