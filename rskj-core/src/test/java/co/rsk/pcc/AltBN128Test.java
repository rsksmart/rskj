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

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.pcc.altBN128.BN128Addition;
import co.rsk.pcc.altBN128.BN128Multiplication;
import co.rsk.pcc.altBN128.BN128Pairing;
import co.rsk.pcc.altBN128.BN128PrecompiledContract;
import co.rsk.pcc.altBN128.impls.AbstractAltBN128;
import co.rsk.pcc.altBN128.impls.JavaAltBN128;
import co.rsk.vm.BytecodeCompiler;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockFactory;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.VM;
import org.ethereum.vm.exception.VMException;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.Stack;
import org.ethereum.vm.program.invoke.ProgramInvokeMockImpl;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigInteger;
import java.util.HashSet;

import static org.ethereum.util.ByteUtil.stripLeadingZeroes;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Sebastian Sicardi
 * @since 10.09.2019
 */
@ExtendWith(MockitoExtension.class)
// to avoid Junit5 unnecessary stub error due to different activation codes being called
@MockitoSettings(strictness = Strictness.LENIENT)
class AltBN128Test {

    private TestSystemProperties config;
    private PrecompiledContracts precompiledContracts;
    private ActivationConfig.ForBlock activations;

    private static final int ADD_GAS_COST = 150;
    private static final int MUL_GAS_COST = 6000;

    @BeforeEach
    void init() {
        config = new TestSystemProperties();
        precompiledContracts = new PrecompiledContracts(config, null);
        activations = mock(ActivationConfig.ForBlock.class);

        when(activations.isActive(ConsensusRule.RSKIP137)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP197)).thenReturn(false);
    }

    @Test
    void testCorrectAddressForALTBN128Contracts() {
        DataWord altBN128AddAddr = PrecompiledContracts.ALT_BN_128_ADD_DW;
        DataWord altBN128MulAddr = PrecompiledContracts.ALT_BN_128_MUL_DW;
        DataWord altBN128PairAddr = PrecompiledContracts.ALT_BN_128_PAIRING_DW;

        PrecompiledContracts.PrecompiledContract altBN128Add = precompiledContracts.getContractForAddress(activations, altBN128AddAddr);
        PrecompiledContracts.PrecompiledContract altBN128Mul = precompiledContracts.getContractForAddress(activations, altBN128MulAddr);
        PrecompiledContracts.PrecompiledContract altBN128Pair = precompiledContracts.getContractForAddress(activations, altBN128PairAddr);

        MatcherAssert.assertThat(altBN128Add, notNullValue());
        MatcherAssert.assertThat(altBN128Mul, notNullValue());
        MatcherAssert.assertThat(altBN128Pair, notNullValue());

        MatcherAssert.assertThat(altBN128Add, instanceOf(BN128Addition.class));
        MatcherAssert.assertThat(altBN128Mul, instanceOf(BN128Multiplication.class));
        MatcherAssert.assertThat(altBN128Pair, instanceOf(BN128Pairing.class));
    }

    @Test
    void testALTBN128ShouldFailOnNotActivatedHardFork() {
        /*
            Test should return null on those contract addresses because
            RSKIP is not activated
         */
        when(activations.isActive(ConsensusRule.RSKIP137)).thenReturn(false);

        DataWord altBN128AddAddr = PrecompiledContracts.ALT_BN_128_ADD_DW;
        DataWord altBN128MulAddr = PrecompiledContracts.ALT_BN_128_MUL_DW;
        DataWord altBN128PairAddr = PrecompiledContracts.ALT_BN_128_PAIRING_DW;

        PrecompiledContracts.PrecompiledContract altBN128Add = precompiledContracts.getContractForAddress(activations, altBN128AddAddr);
        PrecompiledContracts.PrecompiledContract altBN128Mul = precompiledContracts.getContractForAddress(activations, altBN128MulAddr);
        PrecompiledContracts.PrecompiledContract altBN128Pair = precompiledContracts.getContractForAddress(activations, altBN128PairAddr);

        MatcherAssert.assertThat(altBN128Add, nullValue());
        MatcherAssert.assertThat(altBN128Mul, nullValue());
        MatcherAssert.assertThat(altBN128Pair, nullValue());
    }

    @Test
    void testALTBN128AddTwoPoints() throws VMException {
        /*
         Test should return correct result (taken from parity impl)
         */
        String input = "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002";

        String output = "030644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd3" +
                "15ed738c0e0a7c92e7845f96b2ae9c0a68a6a449e3538fc7ff3ebf7a5a18a2c4";

        executePrecompileAndAssert(input, output, "Sum is incorrect",
                PrecompiledContracts.ALT_BN_128_ADD_DW,
                ADD_GAS_COST);
    }

    @Test
    void testALTBN128AddZeroPointsShouldBeZero() throws VMException {
        /*
         Test should return zero
         */
        String input = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";

        String output = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";

        executePrecompileAndAssert(input,
                output,
                "Output of sum should be zero",
                PrecompiledContracts.ALT_BN_128_ADD_DW,
                ADD_GAS_COST);
    }

    @Test
    void testALTBN128AddEmptyInputShouldBeZero() throws VMException {
        /*
         Test should return zero
         */
        String input = "";

        String output = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";

        executePrecompileAndAssert(input,
                output,
                "Output of sum should be zero",
                PrecompiledContracts.ALT_BN_128_ADD_DW,
                ADD_GAS_COST);
    }

    @Test
    void testALTBN128AddPointPlusInfinityIsPoint() throws VMException {
        /*
         Test should return same point
         */
        String point = "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002";

        String input = point +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";
        executePrecompileAndAssert(input,
                point,
                "Output should be the same as input",
                PrecompiledContracts.ALT_BN_128_ADD_DW,
                ADD_GAS_COST);
    }

    @Test
    void testALTBN128AddInfinityPlusPointIsPoint() throws VMException {
        /*
         Test should return same point
         */
        String point = "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002";

        String input = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000" + point;

        executePrecompileAndAssert(input,
                point,
                "Output should be the same as input",
                PrecompiledContracts.ALT_BN_128_ADD_DW,
                ADD_GAS_COST);
    }

    @Test
    void testALTBN128AddPointNotOnCurveShouldFail() throws VMException {
        /*
         Test should return empty byte array because point is not on curve
         */
        String input = "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111";

        String output = "";

        executePrecompileAndAssert(input,
                output,
                "Error: Point is not on curve, should return zero",
                PrecompiledContracts.ALT_BN_128_ADD_DW,
                ADD_GAS_COST);


        when(activations.isActive(ConsensusRule.RSKIP197)).thenReturn(true);

        executePrecompileAndAssertError(input,
                "Invalid result.",
                PrecompiledContracts.ALT_BN_128_ADD_DW,
                ADD_GAS_COST,
                activations);

    }

    @Test
    void shouldReturnInfinityOnIdenticalInputPointValuesOfX() throws VMException {
        BigInteger p0x = new BigInteger(
                "10744596414106452074759370245733544594153395043370666422502510773307029471145");
        BigInteger p0y = new BigInteger(
                "848677436511517736191562425154572367705380862894644942948681172815252343932");
        BigInteger p1x = new BigInteger(
                "10744596414106452074759370245733544594153395043370666422502510773307029471145");
        BigInteger p1y = new BigInteger(
                "21039565435327757486054843320102702720990930294403178719740356721829973864651");

        String output = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";

        altBN128AddTest(p0x, p0y, p1x, p1y, output, "Output should be infinity point");
    }

    @Test
    void shouldReturnTrueAddAndComputeSlope() throws VMException {
        BigInteger p0x = new BigInteger(
                "10744596414106452074759370245733544594153395043370666422502510773307029471145");
        BigInteger p0y = new BigInteger(
                "848677436511517736191562425154572367705380862894644942948681172815252343932");

        BigInteger p1x = new BigInteger(
                "1624070059937464756887933993293429854168590106605707304006200119738501412969");
        BigInteger p1y = new BigInteger(
                "3269329550605213075043232856820720631601935657990457502777101397807070461336");

        String output = "15bf2bb17880144b5d1cd2b1f46eff9d617bffd1ca57c37fb5a49bd84e53cf66" +
                "049c797f9ce0d17083deb32b5e36f2ea2a212ee036598dd7624c168993d1355f";

        altBN128AddTest(p0x, p0y, p1x, p1y, output, "Output is incorrect");

    }

    @Test
    void shouldReturnTrueMultiplyScalarAndPoint() throws VMException {
        BigInteger x = BigInteger.valueOf(1);
        BigInteger y = BigInteger.valueOf(2);

        BigInteger multiplier = new BigInteger(
                "115792089237316195423570985008687907853269984665640564039457584007913129639935");

        String output = "2f588cffe99db877a4434b598ab28f81e0522910ea52b45f0adaa772b2d5d352" +
                "12f42fa8fd34fb1b33d8c6a718b6590198389b26fc9d8808d971f8b009777a97";

        altBN128MulTest(x, y, multiplier, output);
    }

    @Test
    void shouldReturnIdentityWhenMultipliedByScalarValueOne() throws VMException {
        BigInteger x = new BigInteger("11999875504842010600789954262886096740416429265635183817701593963271973497827");
        BigInteger y = new BigInteger("11843594000332171325303933275547366297934113019079887694534126289021216356598");

        BigInteger multiplier = BigInteger.valueOf(1);

        String output = "1a87b0584ce92f4593d161480614f2989035225609f08058ccfa3d0f940febe3" +
                "1a2f3c951f6dadcc7ee9007dff81504b0fcd6d7cf59996efdc33d92bf7f9f8f6";

        altBN128MulTest(x, y, multiplier, output);
    }

    @Test
    void shouldReturnTrueMultiplyPointByScalar() throws VMException {
        BigInteger x = BigInteger.valueOf(1);
        BigInteger y = BigInteger.valueOf(2);

        BigInteger multiplier = BigInteger.valueOf(9);

        String output = "039730ea8dff1254c0fee9c0ea777d29a9c710b7e616683f194f18c43b43b869" +
                "073a5ffcc6fc7a28c30723d6e58ce577356982d65b833a5a5c15bf9024b43d98";

        altBN128MulTest(x, y, multiplier, output);
    }

    @Test
    void shouldReturnSumMultiplyPointByScalar() throws VMException {
        BigInteger x = new BigInteger("11999875504842010600789954262886096740416429265635183817701593963271973497827");
        BigInteger y = new BigInteger("11843594000332171325303933275547366297934113019079887694534126289021216356598");

        BigInteger multiplier = BigInteger.valueOf(2);
        String output = "03d64e49ebb3c56c99e0769c1833879c9b86ead23945e1e7477cbd057e961c50" +
                "0d6840b39f8c2fefe0eced3e7d210b830f50831e756f1cc9039af65dc292e6d0";

        altBN128MulTest(x, y, multiplier, output);
    }

    @Test
    void shouldFailForPointNotOnCurve() throws VMException {
        String input = "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111";

        String output = "";

        executePrecompileAndAssert(input, output, "Output should be empty",
                PrecompiledContracts.ALT_BN_128_MUL_DW,
                MUL_GAS_COST);

        when(activations.isActive(ConsensusRule.RSKIP197)).thenReturn(true);

        executePrecompileAndAssertError(input, "Invalid result.",
                PrecompiledContracts.ALT_BN_128_MUL_DW,
                MUL_GAS_COST, activations);
    }

    @Test
    void mulShouldFailForNotEnoughParams() throws VMException {
        String input = "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111";

        String output = "";

        executePrecompileAndAssert(input, output, "Output should be empty",
                PrecompiledContracts.ALT_BN_128_MUL_DW,
                MUL_GAS_COST);

        when(activations.isActive(ConsensusRule.RSKIP197)).thenReturn(true);

        executePrecompileAndAssertError(input, "Invalid result.",
                PrecompiledContracts.ALT_BN_128_MUL_DW,
                MUL_GAS_COST, activations);
    }

    @Test
    void mulShouldFailEmptyParams() throws VMException {
        /*
         * Behaviour on empty params establishes that correct output is zero byte array
         */

        String input = "";
        String output = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";
        executePrecompileAndAssert(input, output, "Output should be empty",
                PrecompiledContracts.ALT_BN_128_MUL_DW,
                MUL_GAS_COST);

        when(activations.isActive(ConsensusRule.RSKIP197)).thenReturn(true);

        executePrecompileAndAssert(input, output, "Output should be empty",
                PrecompiledContracts.ALT_BN_128_MUL_DW,
                MUL_GAS_COST);
    }

    @Test
    void shouldReturnTrueForNullInput() throws VMException {
        /*
        Null acts as empty input so it should return true
         */
        final String output = "0000000000000000000000000000000000000000000000000000000000000001";

        executePrecompileAndAssert(null, output, "Incorrect gas cost",
                PrecompiledContracts.ALT_BN_128_PAIRING_DW,
                45_000L);
        when(activations.isActive(ConsensusRule.RSKIP197)).thenReturn(true);

        executePrecompileAndAssert(null, output, "Incorrect gas cost",
                PrecompiledContracts.ALT_BN_128_PAIRING_DW,
                45_000L);

    }

    @Test
    void shouldReturnTrueForValidPointsPairing() throws VMException {
        final String g1Point0 = "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002";

        final String g2Point0 = "198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2" +
                "1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed" +
                "090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b" +
                "12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa";

        final String g1Point1 = "0000000000000000000000000000000000000000000000000000000000000001" +
                "30644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd45";

        final String g2Point1 = "198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2" +
                "1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed" +
                "090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b" +
                "12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa";

        String input = g1Point0 + g2Point0 + g1Point1 + g2Point1;
        String expectedOutput = "0000000000000000000000000000000000000000000000000000000000000001";
        executePrecompileAndAssert(input, expectedOutput, "Invalid output of paring of valid point",
                PrecompiledContracts.ALT_BN_128_PAIRING_DW, 113_000);
    }

    @Test
    void parityBenchmarkTenPointMatchValidInput() throws VMException {
        final String input = "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2" +
                "1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed" +
                "090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b" +
                "12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2" +
                "1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed" +
                "275dc4a288d1afb3cbb1ac09187524c7db36395df7be3b99e673b13a075a65ec" +
                "1d9befcd05a5323e6da4d435f3b617cdb3af83285c2df711ef39c01571827f9d" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2" +
                "1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed" +
                "090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b" +
                "12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2" +
                "1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed" +
                "275dc4a288d1afb3cbb1ac09187524c7db36395df7be3b99e673b13a075a65ec" +
                "1d9befcd05a5323e6da4d435f3b617cdb3af83285c2df711ef39c01571827f9d" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2" +
                "1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed" +
                "090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b" +
                "12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2" +
                "1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed" +
                "275dc4a288d1afb3cbb1ac09187524c7db36395df7be3b99e673b13a075a65ec" +
                "1d9befcd05a5323e6da4d435f3b617cdb3af83285c2df711ef39c01571827f9d" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2" +
                "1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed" +
                "090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b" +
                "12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2" +
                "1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed" +
                "275dc4a288d1afb3cbb1ac09187524c7db36395df7be3b99e673b13a075a65ec" +
                "1d9befcd05a5323e6da4d435f3b617cdb3af83285c2df711ef39c01571827f9d" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2" +
                "1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed" +
                "090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b" +
                "12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa" +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002" +
                "198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2" +
                "1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed" +
                "275dc4a288d1afb3cbb1ac09187524c7db36395df7be3b99e673b13a075a65ec" +
                "1d9befcd05a5323e6da4d435f3b617cdb3af83285c2df711ef39c01571827f9d";

        String expectedOutput = "0000000000000000000000000000000000000000000000000000000000000001";
        executePrecompileAndAssert(input, expectedOutput, "Invalid output of paring of valid point",
                PrecompiledContracts.ALT_BN_128_PAIRING_DW, 385_000);
    }

    @Test
    void invalidInputForPairing() throws VMException {
        String input = "11";

        String expectedOutput = "";

        executePrecompileAndAssert(input, expectedOutput, "Output should be empty",
                PrecompiledContracts.ALT_BN_128_PAIRING_DW, 45_000);

        when(activations.isActive(ConsensusRule.RSKIP197)).thenReturn(true);

        executePrecompileAndAssertError(input, "Invalid result.",
                PrecompiledContracts.ALT_BN_128_PAIRING_DW, 45_000, activations);

    }

    @Test
    void emptyInputForPairing() throws VMException {
        String input = "";

        String expectedOutput = "0000000000000000000000000000000000000000000000000000000000000001";

        executePrecompileAndAssert(input, expectedOutput, "Output should be valid",
                PrecompiledContracts.ALT_BN_128_PAIRING_DW, 45_000);

        when(activations.isActive(ConsensusRule.RSKIP197)).thenReturn(true);

        executePrecompileAndAssert(input, expectedOutput, "Output should be valid",
                PrecompiledContracts.ALT_BN_128_PAIRING_DW, 45_000);
    }

    @Test
    void shouldReturnEmptyForPairingPointsNotOnCurve() throws VMException {
        String g1Point0 = "0000000000000000000000000000000000000000000000000000000000000001" +
                "0000000000000000000000000000000000000000000000000000000000000002";
        String g2Point0 = "1382cd45e5674247f9c900b5c6f6cabbc189c2fabe2df0bf5acd84c97818f508" +
                "1246178655ab8f2f26956b189894b7eb93cd4215b9937e7969e44305f80f521e" +
                "08331c0a261a74e7e75db1232956663cbc88110f726159c5cba1857ecd03fa64" +
                "1fbf8045ce3e79b5cde4112d38bcd0efbdb1295d2eefdf58151ae309d7ded7db";
        String g1Point1 = "0000000000000000000000000000000000000000000000000000000000000001" +
                "30644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd45";
        String g2Point1 = "1382cd45e5674247f9c900b5c6f6cabbc189c2fabe2df0bf5acd84c97818f508" +
                "1246178655ab8f2f26956b189894b7eb93cd4215b9937e7969e44305f80f521e" +
                "08331c0a261a74e7e75db1232956663cbc88110f726159c5cba1857ecd03fa64" +
                "1fbf8045ce3e79b5cde4112d38bcd0efbdb1295d2eefdf58151ae309d7ded7db";

        String input = g1Point0 + g2Point0 + g1Point1 + g2Point1;

        String expectedOutput = "";

        executePrecompileAndAssert(input, expectedOutput, "Invalid output of paring of valid point",
                PrecompiledContracts.ALT_BN_128_PAIRING_DW, 113_000);

        when(activations.isActive(ConsensusRule.RSKIP197)).thenReturn(true);

        executePrecompileAndAssertError(input, "Invalid result.",
                PrecompiledContracts.ALT_BN_128_PAIRING_DW, 113_000, activations);
    }

    @Test
    void shouldReturnFalseForValidPointsPairing() throws VMException {
        String inputString = "1c76476f4def4bb94541d57ebba1193381ffa7aa76ada664dd31c16024c43f59" +
                "3034dd2920f673e204fee2811c678745fc819b55d3e9d294e45c9b03a76aef41" +
                "209dd15ebff5d46c4bd888e51a93cf99a7329636c63514396b4a452003a35bf7" +
                "04bf11ca01483bfa8b34b43561848d28905960114c8ac04049af4b6315a41678" +
                "2bb8324af6cfc93537a2ad1a445cfd0ca2a71acd7ac41fadbf933c2a51be344d" +
                "120a2a4cf30c1bf9845f20c6fe39e07ea2cce61f0c9bb048165fe5e4de877550" +
                "111e129f1cf1097710d41c4ac70fcdfa5ba2023c6ff1cbeac322de49d1b6df7c" +
                "103188585e2364128fe25c70558f1560f4f9350baf3959e603cc91486e110936" +
                "198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2" +
                "1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed" +
                "090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b" +
                "12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa";

        String expectedOutput = "0000000000000000000000000000000000000000000000000000000000000000";

        executePrecompileAndAssert(inputString, expectedOutput, "Invalid output of paring of valid point",
                PrecompiledContracts.ALT_BN_128_PAIRING_DW, 113_000);

        when(activations.isActive(ConsensusRule.RSKIP197)).thenReturn(true);

        executePrecompileAndAssert(inputString, expectedOutput, "Invalid output of paring of valid point",
                PrecompiledContracts.ALT_BN_128_PAIRING_DW, 113_000);

    }

    @Test
    void testALTBN128AddFromVM() {
        BigInteger p0x = new BigInteger("1");
        BigInteger p0y = new BigInteger("2");
        BigInteger p1x = new BigInteger("0");
        BigInteger p1y = new BigInteger("0");

        String[] inputs = {ByteUtil.toHexString(p0x.toByteArray()),
                ByteUtil.toHexString(p0y.toByteArray()),
                ByteUtil.toHexString(p1x.toByteArray()),
                ByteUtil.toHexString(p1y.toByteArray())};

        String[] expects = {"0000000000000000000000000000000000000000000000000000000000000001",
                "0000000000000000000000000000000000000000000000000000000000000002"};

        executeVMAltBNOperation(inputs, expects, PrecompiledContracts.ALT_BN_128_ADD_ADDR_STR);
    }

    @Test
    void testALTBN128AddTwoPointsFromVM() {
        /*
         Test should return correct result (taken from parity impl)
         */
        BigInteger p0x = new BigInteger("1");
        BigInteger p0y = new BigInteger("2");
        BigInteger p1x = new BigInteger("1");
        BigInteger p1y = new BigInteger("2");

        String[] inputs = {ByteUtil.toHexString(p0x.toByteArray()),
                ByteUtil.toHexString(p0y.toByteArray()),
                ByteUtil.toHexString(p1x.toByteArray()),
                ByteUtil.toHexString(p1y.toByteArray())};


        String[] expects = {"030644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd3",
                "15ed738c0e0a7c92e7845f96b2ae9c0a68a6a449e3538fc7ff3ebf7a5a18a2c4"};

        executeVMAltBNOperation(inputs, expects, PrecompiledContracts.ALT_BN_128_ADD_ADDR_STR);
    }

    @Test
    void shouldReturnTrueForValidPointsPairingFromVM() {
        String[] inputs = {
                "0000000000000000000000000000000000000000000000000000000000000001",
                "0000000000000000000000000000000000000000000000000000000000000002",

                "198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2",
                "1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed",
                "090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b",
                "12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa",

                "0000000000000000000000000000000000000000000000000000000000000001",
                "30644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd45",

                "198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c2",
                "1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed",
                "090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b",
                "12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa"};

        String[] expects = {"0000000000000000000000000000000000000000000000000000000000000001"};

        executeVMAltBNOperation(inputs, expects, PrecompiledContracts.ALT_BN_128_PAIRING_ADDR_STR);
    }

    @Test
    void shouldReturnSumMultiplyPointByScalarFromVM() {
        BigInteger multiplicandX = new BigInteger("11999875504842010600789954262886096740416429265635183817701593963271973497827");
        BigInteger multiplicandY = new BigInteger("11843594000332171325303933275547366297934113019079887694534126289021216356598");
        BigInteger multiplier = BigInteger.valueOf(2);

        String[] expects = {"03d64e49ebb3c56c99e0769c1833879c9b86ead23945e1e7477cbd057e961c50",
                "0d6840b39f8c2fefe0eced3e7d210b830f50831e756f1cc9039af65dc292e6d0"};

        String[] inputs = {ByteUtil.toHexString(multiplicandX.toByteArray()),
                ByteUtil.toHexString(multiplicandY.toByteArray()),
                ByteUtil.toHexString(multiplier.toByteArray())};

        executeVMAltBNOperation(inputs, expects, PrecompiledContracts.ALT_BN_128_MUL_ADDR_STR);
    }

    private void executePrecompileAndAssertError(String inputString, String errorMessage,
                                                 DataWord contractAddress, long gasCost, ActivationConfig.ForBlock activations) {
        BN128PrecompiledContract contract = (BN128PrecompiledContract) spy(precompiledContracts.getContractForAddress(this.activations, contractAddress));
        runAndAssertError(inputString, errorMessage, contract, gasCost);
        //force Java execution.
        runAndAssertError(inputString, errorMessage, javaContract(contractAddress, activations), gasCost);
    }

    private void executePrecompileAndAssert(String inputString, String expectedOutput, String errorMessage,
                                            DataWord contractAddress, long gasCost) throws VMException {
        BN128PrecompiledContract contract = (BN128PrecompiledContract) spy(precompiledContracts.getContractForAddress(activations, contractAddress));
        runAndAssert(inputString, expectedOutput, errorMessage, contract, gasCost);
        //force Java execution.
        runAndAssert(inputString, expectedOutput, errorMessage, javaContract(contractAddress, activations), gasCost);
    }

    private void runAndAssert(String inputString, String expectedOutput, String errorMessage, BN128PrecompiledContract contract, long gasCost) throws VMException {
        byte[] input = inputString == null ? null : Hex.decode(inputString);
        byte[] output = contract.execute(input);

        MatcherAssert.assertThat("Incorrect gas cost", contract.getGasForData(input), is(gasCost));
        MatcherAssert.assertThat(errorMessage, ByteUtil.toHexString(output), is(expectedOutput));
    }

    private void runAndAssertError(String inputString, String errorMessage, BN128PrecompiledContract contract, long gasCost) {
        byte[] input = inputString == null ? null : Hex.decode(inputString);
        try {
            contract.execute(input);
            fail();
        } catch (VMException e) {
            assertEquals(errorMessage, e.getMessage());
        }
        MatcherAssert.assertThat("Incorrect gas cost", contract.getGasForData(input), is(gasCost));
    }

    private void altBN128MulTest(BigInteger x1, BigInteger y1, BigInteger scalar, String expectedOutput) throws VMException {
        byte[] input = new byte[96];

        byte[] x1Bytes = stripLeadingZeroes(x1.toByteArray());
        byte[] y1Bytes = stripLeadingZeroes(y1.toByteArray());
        byte[] scalarBytes = stripLeadingZeroes(scalar.toByteArray());

        System.arraycopy(x1Bytes, 0, input, 32 - x1Bytes.length, x1Bytes.length);
        System.arraycopy(y1Bytes, 0, input, 64 - y1Bytes.length, y1Bytes.length);
        System.arraycopy(scalarBytes, 0, input, 96 - scalarBytes.length, scalarBytes.length);

        executePrecompileAndAssert(ByteUtil.toHexString(input), expectedOutput,
                "Wrong result of multiplication",
                PrecompiledContracts.ALT_BN_128_MUL_DW,
                MUL_GAS_COST);
    }

    private void altBN128AddTest(BigInteger x1, BigInteger y1, BigInteger x2, BigInteger y2, String expectedOutput, String errorMessage) throws VMException {
        byte[] input = new byte[128];

        byte[] x1Bytes = stripLeadingZeroes(x1.toByteArray());
        byte[] y1Bytes = stripLeadingZeroes(y1.toByteArray());
        byte[] x2Bytes = stripLeadingZeroes(x2.toByteArray());
        byte[] y2Bytes = stripLeadingZeroes(y2.toByteArray());

        System.arraycopy(x1Bytes, 0, input, 32 - x1Bytes.length, x1Bytes.length);
        System.arraycopy(y1Bytes, 0, input, 64 - y1Bytes.length, y1Bytes.length);
        System.arraycopy(x2Bytes, 0, input, 96 - x2Bytes.length, x2Bytes.length);
        System.arraycopy(y2Bytes, 0, input, 128 - y2Bytes.length, y2Bytes.length);

        executePrecompileAndAssert(ByteUtil.toHexString(input), expectedOutput,
                errorMessage, PrecompiledContracts.ALT_BN_128_ADD_DW, ADD_GAS_COST);
    }

    private void executeVMAltBNOperation(String[] inputs, String[] expect, String operationAddress) {
        /*
         * Store in memory precompiled contracts inputs
         * Push to stack CALL inputs
         * Load to stack precompiled contracts outputs
         * */

        StringBuilder codeBuilder = new StringBuilder();
        for (int i = 0; i < inputs.length; i++) {
            codeBuilder
                    .append(" PUSH32 0x").append(DataWord.valueOf(Hex.decode(inputs[i])))
                    .append(" PUSH32 0x").append(DataWord.valueOf(BigInteger.valueOf(i * DataWord.BYTES).toByteArray()))
                    .append(" MSTORE ");
        }

        byte[] argsLengthBytes = BigInteger.valueOf(inputs.length * DataWord.BYTES).toByteArray();
        String retLength = ByteUtil.toHexString(BigInteger.valueOf(expect.length * DataWord.BYTES).toByteArray());
        String argsLength = ByteUtil.toHexString(argsLengthBytes);

        codeBuilder.append(" PUSH").append(retLength.length() / 2)
                .append(" 0x").append(retLength); // retLength

        codeBuilder.append(" PUSH").append(argsLength.length() / 2)
                .append(" 0x").append(argsLength); // retOffset (same as argsLength)

        codeBuilder.append(" PUSH").append(argsLength.length() / 2)
                .append(" 0x").append(argsLength); // argsLength

        codeBuilder.append(" PUSH1 0x00"); // argsoffset
        codeBuilder.append(" PUSH1 0x00"); // value
        codeBuilder.append(" PUSH32 0x").append(DataWord.valueFromHex(operationAddress)); // address
        codeBuilder.append(" PUSH6 0xFFFFFFFFFFFF"); // gas
        codeBuilder.append(" CALL");

        for (int i = 0; i < expect.length; i++) {
            codeBuilder.append(" PUSH32 0x")
                    .append(DataWord.valueOf(BigInteger.valueOf((inputs.length + i) * DataWord.BYTES).toByteArray()))
                    .append(" MLOAD ");
        }

        String code = codeBuilder.toString();

        Program program = executeCode(code);
        Stack stack = program.getStack();

        MatcherAssert.assertThat(stack.size(), is(1 + expect.length));

        for (int j = expect.length - 1; j >= 0; j--) {
            DataWord output = stack.pop();
            MatcherAssert.assertThat(DataWord.valueFromHex(expect[j]), is(output));
        }

        DataWord contractIsSuccessful = stack.pop();
        MatcherAssert.assertThat(DataWord.valueOf(1), is(contractIsSuccessful));
    }

    private Program executeCode(String code) {
        VmConfig vmConfig = config.getVmConfig();
        BlockFactory blockFactory = new BlockFactory(config.getActivationConfig());
        ProgramInvokeMockImpl invoke = new ProgramInvokeMockImpl();
        BytecodeCompiler compiler = new BytecodeCompiler();

        byte[] compiledCode = compiler.compile(code);
        VM vm = new VM(vmConfig, precompiledContracts);
        Program program = new Program(vmConfig, precompiledContracts, blockFactory,
                activations, compiledCode, invoke, null, new HashSet<>());

        vm.play(program);

        return program;
    }

    private BN128PrecompiledContract javaContract(DataWord contractAddress, ActivationConfig.ForBlock activations) {
        AbstractAltBN128 javaAltbn128 = new JavaAltBN128();
        if (contractAddress.equals(PrecompiledContracts.ALT_BN_128_ADD_DW)) {
            return new BN128Addition(activations, javaAltbn128);
        } else if (contractAddress.equals(PrecompiledContracts.ALT_BN_128_MUL_DW)) {
            return new BN128Multiplication(activations, javaAltbn128);
        } else if (contractAddress.equals(PrecompiledContracts.ALT_BN_128_PAIRING_DW)) {
            return new BN128Pairing(activations, javaAltbn128);
        }

        Assertions.fail("this is unexpected");

        return null;
    }
}
