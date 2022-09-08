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

package co.rsk.jsontestsuite;

import org.ethereum.jsontestsuite.GitHubJSONTestSuite;
import org.ethereum.jsontestsuite.JSONReader;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.ethereum.jsontestsuite.JSONReader.getFileNamesForTreeSha;

/**
 * @author Angel J Lopez
 * @since 02.24.2016
 */
@TestMethodOrder(MethodOrderer.MethodName.class)

public class LocalStateTest {

    @Disabled // this method is mostly for hands-on convenient testing
    public void stSingleTest() throws ParseException, IOException {
        String json = getJSON("stSystemOperationsTest");
        GitHubJSONTestSuite.runStateTest(json, "suicideSendEtherPostDeath");
    }

    @Test
    public void stExample() throws ParseException, IOException {

        Set<String> excluded = new HashSet<>();
        String json = getJSON("stExample");
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stCallCodes() throws ParseException, IOException {

        Set<String> excluded = new HashSet<>();
        String json = getJSON("stCallCodes");
        /* Recursive tests excluded */
        excluded.add("callcodecallcode_11");
        excluded.add("callcodecallcode_11");
        excluded.add("callcodecallcallcode_101");
        excluded.add("callcallcodecallcode_011");
        excluded.add("callcodecallcall_100");
        excluded.add("callcall_00_OOGE_valueTransfer");
        excluded.add("callcallcodecall_010");
        excluded.add("callcall_00");
        excluded.add("callcallcode_01");
        excluded.add("callcodecallcodecallcode_111");
        excluded.add("callcodecallcodecall_110");
        excluded.add("callcallcallcode_001");
        excluded.add("callcallcall_000");
        excluded.add("callcodecall_10");
        excluded.add("callcodecallcodecallcode_ABCB_RECURSIVE");
        excluded.add("callcodecallcodecall_ABCB_RECURSIVE");
        excluded.add("callcallcodecall_ABCB_RECURSIVE");
        excluded.add("callcallcall_ABCB_RECURSIVE");
        excluded.add("callcallcallcode_ABCB_RECURSIVE");
        excluded.add("callcodecallcall_ABCB_RECURSIVE");
        excluded.add("callcallcodecallcode_ABCB_RECURSIVE");
        excluded.add("callcodecallcallcode_ABCB_RECURSIVE");
        /* */
        GitHubJSONTestSuite.runStateTest(json, excluded);


    }

    @Test
    public void stCallDelegateCodes() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = getJSON("stCallDelegateCodes");

        // Recursive tests excluded */
        excluded.add("callcodecallcodecallcode_ABCB_RECURSIVE");
        excluded.add("callcodecallcodecall_ABCB_RECURSIVE");
        excluded.add("callcallcodecall_ABCB_RECURSIVE");
        excluded.add("callcallcallcode_ABCB_RECURSIVE");
        excluded.add("callcodecallcall_ABCB_RECURSIVE");
        excluded.add("callcallcodecallcode_ABCB_RECURSIVE");
        excluded.add("callcodecallcallcode_ABCB_RECURSIVE");

        /* These tests would fail if balances are checked
        excluded.add("callcodecallcallcode_101");
        excluded.add("callcallcodecallcode_011");
        excluded.add("callcodecallcall_100");
        excluded.add("callcallcodecall_010");
        excluded.add("callcallcode_01");
        excluded.add("callcodecallcodecall_110");
        excluded.add("callcallcallcode_001");
        excluded.add("callcodecall_10");
        */
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stCallDelegateCodesCallCode() throws ParseException, IOException {

        Set<String> excluded = new HashSet<>();
        String json = getJSON("stCallDelegateCodesCallCode");

        // Recursive tests excluded
        excluded.add("callcodecallcodecallcode_ABCB_RECURSIVE");
        excluded.add("callcodecallcodecall_ABCB_RECURSIVE");
        excluded.add("callcallcodecall_ABCB_RECURSIVE");
        excluded.add("callcallcallcode_ABCB_RECURSIVE");
        excluded.add("callcodecallcall_ABCB_RECURSIVE");
        excluded.add("callcallcodecallcode_ABCB_RECURSIVE");
        excluded.add("callcodecallcallcode_ABCB_RECURSIVE");

        /* These tests would fail if balances are checked
        excluded.add("callcodecallcallcode_101");
        excluded.add("callcallcodecallcode_011");
        excluded.add("callcodecallcall_100");
        excluded.add("callcallcodecall_010");
        excluded.add("callcodecallcodecallcode_111_SuicideEnd");
        excluded.add("callcallcode_01");
        excluded.add("callcodecallcodecall_110");
        excluded.add("callcallcallcode_001");
        excluded.add("callcodecall_10");
        */
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stHomeSteadSpecific() throws ParseException, IOException {

        Set<String> excluded = new HashSet<>();
        String json = getJSON("stHomeSteadSpecific");

        /* These tests would fail if balances are checked
        excluded.add("contractCreationOOGdontLeaveEmptyContract");
        excluded.add("createContractViaContractOOGInitCode");
        */
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stCallCreateCallCodeTest() throws ParseException, IOException {

        Set<String> excluded = new HashSet<>();
        String json = getJSON("stCallCreateCallCodeTest");
        // *** java.lang.instrument ASSERTION FAILED ***: "!errorOutstanding" with message transform method call failed at JPLISAgent.c line: 844

        /* Recursive tests excluded */
        excluded.add("Callcode1024OOG");
        excluded.add("Call1024PreCalls");
        excluded.add("callWithHighValueOOGinCall");
        excluded.add("createNameRegistratorPreStore1NotEnoughGas");
        excluded.add("Callcode1024BalanceTooLow");
        excluded.add("Call1024BalanceTooLow");
        excluded.add("callWithHighValueAndGasOOG");
        excluded.add("createInitFailUndefinedInstruction");
        excluded.add("callWithHighValue");
        excluded.add("CallRecursiveBombPreCall");
        excluded.add("createInitFailStackUnderflow");
        excluded.add("createInitFailStackSizeLargerThan1024");
        excluded.add("callWithHighValueAndOOGatTxLevel");
        excluded.add("Call1024OOG");
        excluded.add("callcodeWithHighValue");
        excluded.add("createInitFailBadJumpDestination");
        excluded.add("callcodeWithHighValueAndGasOOG");
        /* */
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stDelegatecallTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("stDelegatecallTest");

        // All these tests use recursive calls and therefore are incompatible
        excluded.add("Delegatecall1024OOG");
        excluded.add("Call1024PreCalls");
        excluded.add("Call1024BalanceTooLow");
        excluded.add("CallRecursiveBombPreCall");
        excluded.add("Call1024OOG");
        excluded.add("Delegatecall1024");

        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stInitCodeTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("stInitCodeTest");

        /* These tests require not to check account balances
        excluded.add("CallContractToCreateContractWhichWouldCreateContractIfCalled");
        excluded.add("CallContractToCreateContractOOGBonusGas");
        */
        // CallRecursiveContract must be excluded because it creates
        // contracts recursively and the number of contracts to create
        // is given by the amount of gas (it calls itself until
        // CALL fails with OOG).
        // Since CALL works differently between RSK and Ethereum, the test fails.
        //
        // "code": "{[[ 2 ]](ADDRESS)(CODECOPY 0 0 32)(CREATE 0 0 32)}",
        // SSTORE[2] ADDRESS
        // CODECOPY (to:0 from:0 length:32)
        // CREATE a contract (Value=0 InOffset =0 InSize =32)
        // This creates a CLONE of the contract
         excluded.add("CallRecursiveContract");


        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stLogTests() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("stLogTests");


        /* All these tests use CALL to a contract that executes LOG
         * Because CALL consumes a different amount of gas compared to Ethereum
         * all these tests will fail.
         * To enable them, we indicate not to check balances.
         */

        /* These tests would fail if balances are checked
        excluded.add("log3_nonEmptyMem");
        excluded.add("log1_emptyMem");
        excluded.add("log2_nonEmptyMem");
        excluded.add("log1_MaxTopic");
        excluded.add("log2_logMemsizeZero");
        excluded.add("log4_MaxTopic");
        excluded.add("log3_PC");
        excluded.add("log3_nonEmptyMem_logMemSize1");
        excluded.add("log4_emptyMem");
        excluded.add("log4_nonEmptyMem");
        excluded.add("log1_nonEmptyMem_logMemSize1_logMemStart31");
        excluded.add("log3_nonEmptyMem_logMemSize1_logMemStart31");
        excluded.add("log0_nonEmptyMem_logMemSize1_logMemStart31");
        excluded.add("log4_nonEmptyMem_logMemSize1_logMemStart31");
        excluded.add("log2_nonEmptyMem_logMemSize1_logMemStart31");
        excluded.add("log3_logMemsizeTooHigh");
        excluded.add("log1_logMemsizeZero");
        excluded.add("log2_nonEmptyMem_logMemSize1");
        excluded.add("log0_logMemsizeTooHigh");
        excluded.add("log2_emptyMem");
        excluded.add("log1_logMemStartTooHigh");
        excluded.add("log4_logMemsizeTooHigh");
        excluded.add("log2_MaxTopic");
        excluded.add("log0_emptyMem");
        excluded.add("log4_logMemStartTooHigh");
        excluded.add("log1_nonEmptyMem_logMemSize1");
        excluded.add("logInOOG_Call");
        excluded.add("log1_logMemsizeTooHigh");
        excluded.add("log0_logMemStartTooHigh");
        excluded.add("log0_logMemsizeZero");
        excluded.add("log4_PC");
        excluded.add("log2_logMemStartTooHigh");
        excluded.add("log4_logMemsizeZero");
        excluded.add("log3_logMemStartTooHigh");
        excluded.add("log2_logMemsizeTooHigh");
        excluded.add("log3_MaxTopic");
        excluded.add("log3_emptyMem");
        excluded.add("log0_nonEmptyMem_logMemSize1");
        excluded.add("log1_Caller");
        excluded.add("log2_Caller");
        excluded.add("log1_nonEmptyMem");
        excluded.add("log3_Caller");
        excluded.add("log4_Caller");
        excluded.add("log4_nonEmptyMem_logMemSize1");
        excluded.add("log0_nonEmptyMem");
        excluded.add("log3_logMemsizeZero");
        */
        GitHubJSONTestSuite.runStateTest(json, excluded);
        /* */
    }

    @Test
    public void stPreCompiledContracts() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = getJSON("stPreCompiledContracts");
        /* These tests would fail if balances are checked
        excluded.add("CALLCODEEcrecover0_0input");
        excluded.add("CALLCODEIdentity_1_nonzeroValue");
        excluded.add("CallEcrecover80");
        excluded.add("CALLCODEEcrecover0_gas3000");
        excluded.add("CallEcrecover0_Gas2999");
        excluded.add("CallEcrecoverR_prefixed0");
        excluded.add("CallEcrecoverH_prefixed0");
        excluded.add("CALLCODEEcrecover0");
        excluded.add("CALLCODEEcrecover0_Gas2999");
        excluded.add("CALLCODEEcrecover1");
        excluded.add("CallIdentity_1_nonzeroValue");
        excluded.add("CALLCODEEcrecover2");
        excluded.add("CALLCODEEcrecover3");
        excluded.add("CallEcrecoverV_prefixed0");
        excluded.add("CallEcrecoverS_prefixed0");
        excluded.add("CallEcrecover3");
        excluded.add("CallEcrecover0_overlappingInputOutput");
        excluded.add("CallEcrecover2");
        excluded.add("CALLCODEEcrecover0_overlappingInputOutput");
        excluded.add("CallEcrecover1");
        excluded.add("CallEcrecover0");
        excluded.add("CallEcrecover0_gas3000");
        excluded.add("CALLCODESha256_1_nonzeroValue");
        excluded.add("CallEcrecover0_NoGas");
        excluded.add("CallSha256_1_nonzeroValue");
        excluded.add("CALLCODEEcrecoverV_prefixed0");
        excluded.add("CALLCODEEcrecover80");
        excluded.add("CALLCODEEcrecoverH_prefixed0");
        excluded.add("CALLCODEEcrecoverR_prefixed0");
        excluded.add("CALLCODEEcrecoverS_prefixed0");
        excluded.add("CALLCODEEcrecover0_NoGas");
        excluded.add("CallEcrecover0_0input");
        */
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stMemoryStressTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        /* These tests would fail if balances are checked
        excluded.add("mload32bitBound_return2");
        excluded.add("mload32bitBound_return");
        */

        // mload32bitBound_Msize has to be excluded because RSK does not
        // allow access to addres 4294967295 (0xFFFFFFFF).
        // "code" : "{ [4294967295] 1 [[ 0 ]] (MSIZE)} ",
        // This compiles to:
        //  PUSH 4294967295
        //  PUSH 1
        //  MSTORE (stores 1 at 4294967295, fails in RSK)
        //  PUSH 0
        //  PUSH MSIZE
        //  SSTORE (Stores MSIZE at persistent cell 0)
        excluded.add("mload32bitBound_Msize"); // Tries to store something in address 4294967295. This causes OOG in RSK

        String json = getJSON("stMemoryStressTest");

        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stMemoryTest() throws ParseException, IOException {
        String json = getJSON("stMemoryTest");
        Set<String> excluded = new HashSet<>();

        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Disabled
    // While RSK passes these tests, they have no "expect" clauses. Nothing is checked
    // after each test is finished. I suppose these tests serve only for checking
    // the performance of the VM. However there are no time contrains here, so
    // the tests are currenlty useless.

    public void stQuadraticComplexityTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("stQuadraticComplexityTest");
        // The test Call1MB1024Calldepth must be excluded because RSK doesn't
        // has the 1024 call depth limit, while Ethereum uses a 63/64 gas spending
        // per CALL to prevent hitting the limit.
        excluded.add("Call1MB1024Calldepth");
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stSolidityTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("stSolidityTest");

        /* These tests would fail if balances are checked
        excluded.add("TestBlockAndTransactionProperties");
        excluded.add("TestCryptographicFunctions");
        excluded.add("TestStructuresAndVariabless");
        excluded.add("TestOverflow");
        excluded.add("AmbiguousMethod");
        excluded.add("TestContractSuicide");
        excluded.add("CreateContractFromMethod");
        excluded.add("TestContractInteraction");
        excluded.add("TestKeywords");
        excluded.add("CallLowLevelCreatesSolidity");
        excluded.add("RecursiveCreateContractsCreate4Contracts");
        excluded.add("ContractInheritance");
        */
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stRecursiveCreate() throws IOException {
        Set<String> excluded = new HashSet<>();

        String json = getJSON("stRecursiveCreate");

        // Recursive tests excluded
        excluded.add("recursiveCreateReturnValue");
        excluded.add("recursiveCreate");
        excluded.add("testRandomTest");
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stRefundTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = getJSON("stRefundTest");

        /* These tests would fail if balances are checked
        excluded.add("refund_singleSuicide");
        excluded.add("refund_multimpleSuicide");
        excluded.add("refund600");
        excluded.add("refund50percentCap");
        */
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stSpecialTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = getJSON("stSpecialTest");
        // Why ? Recursion ?
        excluded.add("JUMPDEST_Attack");
        excluded.add("JUMPDEST_AttackwithJump");
        /* These tests would fail if balances are checked
        excluded.add("makeMoney");

        */
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stBlockHashTest() throws ParseException, IOException {
        String json = getJSON("stBlockHashTest");
        Set<String> excluded = new HashSet<>();

        /* These tests would fail if balances are checked
        excluded.add("blockhash0");
        excluded.add("blockhashDOS-sec71");
        */
        GitHubJSONTestSuite.runStateTest(json,excluded);
    }

    @Test
    public void stSystemOperationsTest() throws IOException {

        Set<String> excluded = new HashSet<>();
        String json = getJSON("stSystemOperationsTest");

        // Recursion in the Unit test framework does not work well
        // They throw an internal exception due to stack overflow.
        excluded.add("CallRecursiveBombLog2");
        excluded.add("CallRecursiveBombLog");
        excluded.add("CallRecursiveBomb0_OOG_atMaxCallDepth");
        excluded.add("CallRecursiveBomb3");
        excluded.add("CallRecursiveBomb2");
        excluded.add("CallRecursiveBomb1");
        excluded.add("CallRecursiveBomb0");
        excluded.add("ABAcallsSuicide1");
        excluded.add("ABAcalls0");
        excluded.add("ABAcalls1");
        excluded.add("ABAcalls2");
        excluded.add("ABAcalls3");

        // createWithInvalidOpcode and testRandomTest tests:
        // This test fails because it specifies a header with
        // an invalid block difficulty: A block difficulty must be positive or zero
        // This is because the test uses the difficulty 2^256-1 and this is interpreted as
        // a negative number by RLP.parseBlockDifficulty().
        // This was solved by changing the first hex digit of the difficulty from "f" to "1".
        // Same for testRandomTest
        //

        /* These tests would fail if balances are checked
        excluded.add("testRandomTest");
        excluded.add("callcodeToNameRegistratorAddresTooBigLeft");
        excluded.add("callcodeTo0");
        excluded.add("callcodeToNameRegistratorZeroMemExpanion");
        excluded.add("callcodeToReturn1");
        excluded.add("callcodeToNameRegistrator0");
        excluded.add("Call10");
        excluded.add("callcodeToNameRegistratorAddresTooBigRight");
        excluded.add("CallToNameRegistratorNotMuchMemory0");
        excluded.add("ABAcallsSuicide0");
        excluded.add("CallToNameRegistratorNotMuchMemory1");
        excluded.add("CallToNameRegistratorOutOfGas");
        excluded.add("CallToNameRegistrator0");
        excluded.add("CallToNameRegistratorAddressTooBigRight");
        excluded.add("CallToNameRegistratorAddressTooBigLeft");
        excluded.add("CallToReturn1");
        excluded.add("CallToNameRegistratorZeorSizeMemExpansion");
        */

        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stTransactionTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("stTransactionTest");

        //StoreGasOnCreate must be excluded because
        //CREATE seems to consume more gas in RSK than in Ethereum.

        excluded.add("StoreGasOnCreate");

        /* These tests would fail if balances are checked
        excluded.add("TransactionNonceCheck2");
        excluded.add("TransactionNonceCheck");
        excluded.add("InternalCallHittingGasLimit2");
        excluded.add("SuicidesAndInternlCallSuicidesBonusGasAtCall");
        excluded.add("InternlCallStoreClearsOOG");
        excluded.add("StoreClearsAndInternlCallStoreClearsOOG");
        excluded.add("InternalCallHittingGasLimitSuccess");

        excluded.add("SuicidesAndInternlCallSuicidesSuccess");
        excluded.add("InternlCallStoreClearsSucces");
        excluded.add("StoreClearsAndInternlCallStoreClearsSuccess");
        */
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stTransitionTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = getJSON("stTransitionTest");
        excluded.add("createNameRegistratorPerTxsNotEnoughGasBefore");
        // Investigate...
        excluded.add("delegatecallBeforeTransition");

        /* These tests would fail if balances are checked
        excluded.add("delegatecallAtTransition");
        excluded.add("delegatecallAfterTransition");
        excluded.add("createNameRegistratorPerTxsBefore");
        */
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Disabled
    @Test
    public void stWalletTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = getJSON("stWalletTest");

        // Investigate reason
        excluded.add("dayLimitConstruction");
        excluded.add("walletConstruction");

        /* These tests would fail if balances are checked
        excluded.add("walletExecuteUnderDailyLimit");
        excluded.add("multiOwnedConstructionCorrect");
        excluded.add("walletChangeRequirementRemovePendingTransaction");
        excluded.add("walletExecuteOverDailyLimitOnlyOneOwnerNew");
        excluded.add("multiOwnedRemoveOwner_ownerIsNotOwner");
        excluded.add("multiOwnedChangeOwner_toIsOwner");
        excluded.add("multiOwnedChangeOwner");
        excluded.add("walletRemoveOwnerRemovePendingTransaction");
        excluded.add("walletDefault");
        excluded.add("multiOwnedChangeRequirementTo0");
        excluded.add("dayLimitSetDailyLimit");
        excluded.add("multiOwnedChangeRequirementTo1");

        excluded.add("multiOwnedChangeRequirementTo2");
        excluded.add("multiOwnedIsOwnerTrue");
        excluded.add("walletChangeOwnerRemovePendingTransaction");
        excluded.add("multiOwnedIsOwnerFalse");
        excluded.add("multiOwnedChangeOwnerNoArguments");
        excluded.add("dayLimitSetDailyLimitNoData");
        excluded.add("walletExecuteOverDailyLimitMultiOwner");

        excluded.add("multiOwnedAddOwnerAddMyself");
        excluded.add("multiOwnedAddOwner");
        excluded.add("walletKill");
        excluded.add("multiOwnedRevokeNothing");
        excluded.add("walletConfirm");
        excluded.add("multiOwnedRemoveOwner");
        excluded.add("dayLimitResetSpentToday");
        excluded.add("multiOwnedChangeOwner_fromNotOwner");
        excluded.add("multiOwnedRemoveOwner_mySelf");
        excluded.add("walletAddOwnerRemovePendingTransaction");
        excluded.add("multiOwnedRemoveOwnerByNonOwner");
        excluded.add("walletKillNotByOwner");
        excluded.add("walletKillToWallet");
        excluded.add("walletExecuteOverDailyLimitOnlyOneOwner");
        */
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Disabled
    //@Test // testing full suite
    public void testRandomStateGitHub() throws ParseException, IOException {

        String sha = "99db6f4f5fea3aa5cfbe8436feba8e213d06d1e8";
        List<String> fileNames = getFileNamesForTreeSha(sha);
        List<String> includedFiles =
                Arrays.asList(
                        "st201504081841JAVA.json",
                        "st201504081842JAVA.json",
                        "st201504081843JAVA.json"
                );

        for (String fileName : fileNames) {
            if (includedFiles.contains(fileName)) {
                System.out.println("Running: " + fileName);
                String json = JSONReader.loadJSON("StateTests//RandomTests/" + fileName);
                GitHubJSONTestSuite.runStateTest(json);
            }
        }

    }

    private static String getJSON(String name) {
        String json = JSONReader.loadJSONFromResource("json/StateTests/" + name + ".json", LocalVMTest.class.getClassLoader());
        return json;
    }
}

