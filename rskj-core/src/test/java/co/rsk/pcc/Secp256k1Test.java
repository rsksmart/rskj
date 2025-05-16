package co.rsk.pcc;

import co.rsk.config.TestSystemProperties;
import co.rsk.config.VmConfig;
import co.rsk.pcc.secp256k1.Secp256k1Addition;
import co.rsk.pcc.secp256k1.Secp256k1Multiplication;
import co.rsk.pcc.secp256k1.Secp256k1PrecompiledContract;
import co.rsk.pcc.secp256k1.impls.AbstractSecp256k1;
import co.rsk.pcc.secp256k1.impls.JavaSecp256k1;
import co.rsk.vm.BytecodeCompiler;
import org.bouncycastle.asn1.x509.V1TBSCertificateGenerator;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
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
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.spy;


@ExtendWith(MockitoExtension.class)
// to avoid Junit5 unnecessary stub error due to different activation codes being called
@MockitoSettings(strictness = Strictness.LENIENT)
class Secp256k1Test {

    private TestSystemProperties config;
    private PrecompiledContracts precompiledContracts;
    private ActivationConfig.ForBlock activations;
    private SignatureCache signatureCache;

    private static final int ADD_GAS_COST = 150;
    private static final int MUL_GAS_COST = 3000;

    @BeforeEach
    void init() {
        config = new TestSystemProperties();
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        precompiledContracts = new PrecompiledContracts(config, null, signatureCache);
        activations = mock(ActivationConfig.ForBlock.class);

        when(activations.isActive(ConsensusRule.RSKIP137)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP197)).thenReturn(false);
    }

    @Test
    void testCorrectAddressForContracts() {
        DataWord secpAddAddr = PrecompiledContracts.SECP256K1_ADD_DW;
        DataWord secpMulAddr = PrecompiledContracts.SECP256K1_MUL_DW;
        PrecompiledContracts.PrecompiledContract secpAdd = precompiledContracts.getContractForAddress(activations, secpAddAddr);
        PrecompiledContracts.PrecompiledContract secpMul = precompiledContracts.getContractForAddress(activations, secpMulAddr);


        MatcherAssert.assertThat(secpAdd, notNullValue());
        MatcherAssert.assertThat(secpMul, notNullValue());

        MatcherAssert.assertThat(secpAdd, instanceOf(Secp256k1Addition.class));
        MatcherAssert.assertThat(secpMul, instanceOf(Secp256k1Multiplication.class));
    }

    @Test
    void testSecpShouldFailOnNotActivatedHardFork() {
        /*
            Test should return null on those contract addresses because
            RSKIP is not activated
         */
        when(activations.isActive(ConsensusRule.RSKIP137)).thenReturn(false);

        DataWord secpAddAddr = PrecompiledContracts.SECP256K1_ADD_DW;
        DataWord secpMulAddr = PrecompiledContracts.SECP256K1_MUL_DW;
        PrecompiledContracts.PrecompiledContract secpAdd = precompiledContracts.getContractForAddress(activations, secpAddAddr);
        PrecompiledContracts.PrecompiledContract secpMul = precompiledContracts.getContractForAddress(activations, secpMulAddr);

        MatcherAssert.assertThat(secpAdd, nullValue());
        MatcherAssert.assertThat(secpMul, nullValue());
    }

    @Test
    void testSecpAddTwoPoints() throws VMException {
        /*
         Test should return correct result (taken from parity impl)
         */
        String input =
                "0000000000000000000000000000000000000000000000000000000000000001" +
                bigIntegerToHexDW(v1y) +
                "0000000000000000000000000000000000000000000000000000000000000001" +
                        bigIntegerToHexDW(v1y);

        BigInteger ox = new BigInteger(v1by2x);
        BigInteger oy = new BigInteger(v1by2y);
        String output = ByteUtil.bigIntegerToHex(ox) +
                ByteUtil.bigIntegerToHex(oy);

        executePrecompileAndAssert(input, output, "Sum is incorrect",
                PrecompiledContracts.SECP256K1_ADD_DW,
                ADD_GAS_COST);
    }

    @Test
    void testSecpAddZeroPointsShouldBeZero() throws VMException {
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
                PrecompiledContracts.SECP256K1_ADD_DW,
                ADD_GAS_COST);
    }

    @Test
    void testSecpAddEmptyInputShouldBeZero() throws VMException {
        /*
         Test should return zero
         */
        String input = "";

        String output = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";

        executePrecompileAndAssert(input,
                output,
                "Output of sum should be zero",
                PrecompiledContracts.SECP256K1_ADD_DW,
                ADD_GAS_COST);
    }

    @Test
    void testSecpAddPointPlusInfinityIsPoint() throws VMException {
        /*
         Test should return same point
         */
        String point = "0000000000000000000000000000000000000000000000000000000000000001" +
                bigIntegerToHexDW(v1y);

        String input = point +
                "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";
        executePrecompileAndAssert(input,
                point,
                "Output should be the same as input",
                PrecompiledContracts.SECP256K1_ADD_DW,
                ADD_GAS_COST);
    }

    @Test
    void testSecpAddInfinityPlusPointIsPoint() throws VMException {
        /*
         Test should return same point
         */
        String point = "0000000000000000000000000000000000000000000000000000000000000001" +
                bigIntegerToHexDW(v1y);

        String input = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000" + point;

        executePrecompileAndAssert(input,
                point,
                "Output should be the same as input",
                PrecompiledContracts.SECP256K1_ADD_DW,
                ADD_GAS_COST);
    }

    @Test
    void testSecpAddPointNotOnCurveShouldFail() throws VMException {
        /*
         Test should return empty byte array because point is not on curve
         */
        String input =
                "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111";

        String output = "";

        executePrecompileAndAssert(input,
                output,
                "Error: Point is not on curve, should return zero",
                PrecompiledContracts.SECP256K1_ADD_DW,
                ADD_GAS_COST);


        when(activations.isActive(ConsensusRule.RSKIP197)).thenReturn(true);

        executePrecompileAndAssertError(input,
                "Invalid result.",
                PrecompiledContracts.SECP256K1_ADD_DW,
                ADD_GAS_COST,
                activations);

    }

    @Test
    void shouldReturnInfinityOnIdenticalInputPointValuesOfX() throws VMException {
        BigInteger p0x = new BigInteger(
                "3");
        BigInteger p0y = new BigInteger(
                "21320899557911560362763253855565071047772010424612278905734793689199612115787");
        BigInteger p1x = new BigInteger(
                "3");
        BigInteger p1y = new BigInteger(
                "-21320899557911560362763253855565071047772010424612278905734793689203907084060");
        byte[] p1y_val = JavaSecp256k1.getNegate(ByteUtil.bigIntegerToBytes(p0x),ByteUtil.bigIntegerToBytes(p0y));
        BigInteger p1y2 = new BigInteger(p1y_val);


        String output = "0000000000000000000000000000000000000000000000000000000000000000" +
                "0000000000000000000000000000000000000000000000000000000000000000";

        secpAddTest(p0x, p0y, p1x, p1y, output, "Output should be infinity point");
    }

    @Test
    void shouldReturnTrueAddAndComputeSlope() throws VMException {
        BigInteger p0x = new BigInteger(
                "4");
        BigInteger p0y = new BigInteger(
                "40508090799132825824753983223610497876805216745196355809233758402754120847507");

        BigInteger p1x = new BigInteger(
                    "1624070059937464756887933993293429854168590106605707304006200119738501412969");
        BigInteger p1y = new BigInteger(
                "48810817106871756219742442189260392858217846784043974224646271552914041676099");
        BigInteger ox = new BigInteger("59470963110652214182270290319243047549711080187995156844066669631124720856270");
        BigInteger oy = new BigInteger("75549874947483386113764723043915448105868538368156141886808196158351727282824");
        String output = ByteUtil.bigIntegerToHex(ox) +
                ByteUtil.bigIntegerToHex(oy);

        secpAddTest(p0x, p0y, p1x, p1y, output, "Output is incorrect");

    }

    @Test
    void shouldReturnTrueMultiplyScalarAndPoint() throws VMException {
        BigInteger x = BigInteger.valueOf(1);
        BigInteger y = new BigInteger(v1y);

        BigInteger multiplier = new BigInteger(
                "115792089237316195423570985008687907853269984665640564039457584007913129639935");

        BigInteger ox = new BigInteger("68306631035792818416930554521980007078198693994042647901813352646899028694565");
        BigInteger oy = new BigInteger("763410389832780290161227297165449309800016629866253823160953352172730927280");
        String output = ByteUtil.bigIntegerToHex(ox) +
                ByteUtil.bigIntegerToHex(oy);

        secpMulTest(x, y, multiplier, output);
    }
    public static byte[] byte32Of(byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[32];
        }

        if (data.length > 32) {
            throw new IllegalArgumentException(String.format("A DataWord must be %d bytes long", 32));
        }

        // if there is not enough data
        // trailing zeros are assumed (this is required for PUSH opcode semantics)
        byte[] copiedData = new byte[32];
        int dlen = Integer.min(data.length, data.length );
        System.arraycopy(data, 0, copiedData, 32 - data.length, dlen);
        return copiedData;
    }
    public static String bigIntegerToHexDW(String valueBase10) {
        return ByteUtil.toHexString(byte32Of(ByteUtil.bigIntegerToBytes(new BigInteger(valueBase10))));
    }
    @Test
    void shouldReturnIdentityWhenMultipliedByScalarValueOne() throws VMException {
        BigInteger x = new BigInteger("1");
        BigInteger y = new BigInteger(v1y);

        BigInteger multiplier = BigInteger.valueOf(1);
        String output = bigIntegerToHexDW("1") +
                bigIntegerToHexDW(v1y);

        secpMulTest(x, y, multiplier, output);
    }

    String v1by9x = "46171929588085016379679198610744759757996296651373714437564035753833216770329";
    String v1by9y  = "4076329532618667641907419885981677362511359868272295070859229146922980867493";


    @Test
    void shouldReturnTrueMultiplyPointByScalar() throws VMException {
        BigInteger x = BigInteger.valueOf(1);
        BigInteger y = new BigInteger(v1y);

        BigInteger multiplier = BigInteger.valueOf(9);
        BigInteger ox = new BigInteger(v1by9x);
        BigInteger oy = new BigInteger(v1by9y);
        String output = ByteUtil.bigIntegerToHex(ox) +
                ByteUtil.bigIntegerToHex(oy);

        secpMulTest(x, y, multiplier, output);
    }

    String v1by2x ="90462569716653277674664832038037428010367175520031690655826237506178777087235";
    String v1by2y ="30122570767565969031174451675354718271714177419582540229636601003470726681395";
    @Test
    void shouldReturnSumMultiplyPointByScalar() throws VMException {
        BigInteger x = new BigInteger("1");
        BigInteger y = new BigInteger(v1y);

        BigInteger multiplier = BigInteger.valueOf(2);
        BigInteger ox = new BigInteger(v1by2x );
        BigInteger oy = new BigInteger(v1by2y);
        String output = ByteUtil.bigIntegerToHex(ox) +
                ByteUtil.bigIntegerToHex(oy);

        secpMulTest(x, y, multiplier, output);
    }

    @Test
    void shouldFailForPointNotOnCurve() throws VMException {
        String input = "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111";

        String output = "";

        executePrecompileAndAssert(input, output, "Output should be empty",
                PrecompiledContracts.SECP256K1_MUL_DW,
                MUL_GAS_COST);

        when(activations.isActive(ConsensusRule.RSKIP197)).thenReturn(true);

        executePrecompileAndAssertError(input, "Invalid result.",
                PrecompiledContracts.SECP256K1_MUL_DW,
                MUL_GAS_COST, activations);
    }

    @Test
    void mulShouldFailForNotEnoughParams() throws VMException {
        String input = "1111111111111111111111111111111111111111111111111111111111111111" +
                "1111111111111111111111111111111111111111111111111111111111111111";

        String output = "";

        executePrecompileAndAssert(input, output, "Output should be empty",
                PrecompiledContracts.SECP256K1_MUL_DW,
                MUL_GAS_COST);

        when(activations.isActive(ConsensusRule.RSKIP197)).thenReturn(true);

        executePrecompileAndAssertError(input, "Invalid result.",
                PrecompiledContracts.SECP256K1_MUL_DW,
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
                PrecompiledContracts.SECP256K1_MUL_DW,
                MUL_GAS_COST);

        when(activations.isActive(ConsensusRule.RSKIP197)).thenReturn(true);

        executePrecompileAndAssert(input, output, "Output should be empty",
                PrecompiledContracts.SECP256K1_MUL_DW,
                MUL_GAS_COST);
    }


    @Test
    void testSecpAddFromVM() {
        BigInteger p0x = new BigInteger("1");
        BigInteger p0y = new BigInteger(v1y);

        // Adding point at infinity returns the same point
        BigInteger p1x = new BigInteger("0");
        BigInteger p1y = new BigInteger("0");

        String[] inputs = {
                ByteUtil.toHexString(p0x.toByteArray()),
                ByteUtil.toHexString(p0y.toByteArray()),
                ByteUtil.toHexString(p1x.toByteArray()),
                ByteUtil.toHexString(p1y.toByteArray())};

        String[] expects = {"0000000000000000000000000000000000000000000000000000000000000001",
                bigIntegerToHexDW(v1y)};

        executeVMSecpOperation(inputs, expects, PrecompiledContracts.SECP256K1_ADD_ADDR_STR);
    }
    String v1y = "29896722852569046015560700294576055776214335159245303116488692907525646231534";
    String v2y = "69211104694897500952317515077652022726490027694212560352756646854116994689233";

    @Test
    void testSecpAddTwoPointsFromVM() {
        /*
         Test should return correct result (taken from parity impl)
         */
        BigInteger p0x = new BigInteger("1");
        BigInteger p0y = new BigInteger(v1y);
        BigInteger p1x = new BigInteger("2");
        BigInteger p1y = new BigInteger(v2y);

        String[] inputs = {
                ByteUtil.toHexString(ByteUtil.bigIntegerToBytes(p0x)),
                ByteUtil.toHexString(ByteUtil.bigIntegerToBytes(p0y)),
                ByteUtil.toHexString(ByteUtil.bigIntegerToBytes(p1x)),
                ByteUtil.toHexString(ByteUtil.bigIntegerToBytes(p1y))};


        String sx = new BigInteger("109562500687829935604265064386702914290271628241900466384583316550888437213118").toString(16);
        String sy = new BigInteger("54782835737747434227939451500021052510566980337100013600092875738315717035444").toString(16);
        String[] expects = {sx,sy};

        executeVMSecpOperation(inputs, expects, PrecompiledContracts.SECP256K1_ADD_ADDR_STR);
    }


    @Test
    void shouldReturnSumMultiplyPointByScalarFromVM() {
        BigInteger multiplicandX = new BigInteger("1");
        BigInteger multiplicandY = new BigInteger(v1y);
        BigInteger multiplier = BigInteger.valueOf(9);

        BigInteger ox = new BigInteger(v1by9x);
        BigInteger oy = new BigInteger(v1by9y);
        String[] expects = {ByteUtil.bigIntegerToHex(ox),
                ByteUtil.bigIntegerToHex(oy)};

        String[] inputs = {
                ByteUtil.toHexString(multiplicandX.toByteArray()),
                ByteUtil.toHexString(multiplicandY.toByteArray()),
                ByteUtil.toHexString(multiplier.toByteArray())};

        executeVMSecpOperation(inputs, expects, PrecompiledContracts.SECP256K1_MUL_ADDR_STR);
    }

    private void executePrecompileAndAssertError(String inputString, String errorMessage,
                                                 DataWord contractAddress, long gasCost, ActivationConfig.ForBlock activations) {
        Secp256k1PrecompiledContract contract = (Secp256k1PrecompiledContract) spy(precompiledContracts.getContractForAddress(this.activations, contractAddress));
        runAndAssertError(inputString, errorMessage, contract, gasCost);
        //force Java execution.
        runAndAssertError(inputString, errorMessage, javaContract(contractAddress, activations), gasCost);
    }

    private void executePrecompileAndAssert(String inputString, String expectedOutput, String errorMessage,
                                            DataWord contractAddress, long gasCost) throws VMException {
        Secp256k1PrecompiledContract contract = (Secp256k1PrecompiledContract) spy(precompiledContracts.getContractForAddress(activations, contractAddress));
        runAndAssert(inputString, expectedOutput, errorMessage, contract, gasCost);
        //force Java execution.
        runAndAssert(inputString, expectedOutput, errorMessage, javaContract(contractAddress, activations), gasCost);
    }

    private void runAndAssert(String inputString, String expectedOutput, String errorMessage, Secp256k1PrecompiledContract contract, long gasCost) throws VMException {
        byte[] input = inputString == null ? null : Hex.decode(inputString);
        byte[] output = contract.execute(input);

        MatcherAssert.assertThat("Incorrect gas cost", contract.getGasForData(input), is(gasCost));
        MatcherAssert.assertThat(errorMessage, ByteUtil.toHexString(output), is(expectedOutput));
    }

    private void runAndAssertError(String inputString, String errorMessage, Secp256k1PrecompiledContract contract, long gasCost) {
        byte[] input = inputString == null ? null : Hex.decode(inputString);
        try {
            contract.execute(input);
            fail();
        } catch (VMException e) {
            assertEquals(errorMessage, e.getMessage());
        }
        MatcherAssert.assertThat("Incorrect gas cost", contract.getGasForData(input), is(gasCost));
    }

    private void secpMulTest(BigInteger x1, BigInteger y1, BigInteger scalar, String expectedOutput) throws VMException {
        byte[] input = new byte[96];

        byte[] x1Bytes = stripLeadingZeroes(x1.toByteArray());
        byte[] y1Bytes = stripLeadingZeroes(y1.toByteArray());
        byte[] scalarBytes = stripLeadingZeroes(scalar.toByteArray());

        System.arraycopy(x1Bytes, 0, input, 32 - x1Bytes.length, x1Bytes.length);
        System.arraycopy(y1Bytes, 0, input, 64 - y1Bytes.length, y1Bytes.length);
        System.arraycopy(scalarBytes, 0, input, 96 - scalarBytes.length, scalarBytes.length);

        executePrecompileAndAssert(ByteUtil.toHexString(input), expectedOutput,
                "Wrong result of multiplication",
                PrecompiledContracts.SECP256K1_MUL_DW,
                MUL_GAS_COST);
    }

    private void secpAddTest(BigInteger x1, BigInteger y1, BigInteger x2, BigInteger y2, String expectedOutput, String errorMessage) throws VMException {
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
                errorMessage, PrecompiledContracts.SECP256K1_ADD_DW, ADD_GAS_COST);
    }

    private void executeVMSecpOperation(String[] inputs, String[] expect, String operationAddress) {
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
                activations, compiledCode, invoke, null, new HashSet<>(), new BlockTxSignatureCache(new ReceivedTxSignatureCache()));

        vm.play(program);

        return program;
    }

    private Secp256k1PrecompiledContract javaContract(DataWord contractAddress, ActivationConfig.ForBlock activations) {
        AbstractSecp256k1 javaSecp = new JavaSecp256k1();
        if (contractAddress.equals(PrecompiledContracts.SECP256K1_ADD_DW)) {
            return new Secp256k1Addition(activations, javaSecp);
        } else if (contractAddress.equals(PrecompiledContracts.SECP256K1_MUL_DW)) {
            return new Secp256k1Multiplication(activations, javaSecp);
        }

        Assertions.fail("this is unexpected");

        return null;
    }
}
