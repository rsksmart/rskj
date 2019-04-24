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

import org.apache.commons.io.FilenameUtils;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.jsontestsuite.GitHubJSONTestSuite;
import org.ethereum.jsontestsuite.JSONReader;
import org.json.simple.parser.ParseException;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.ethereum.jsontestsuite.JSONReader.getFileNamesForTreeSha;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Angel J Lopez
 * @since 02.24.2016
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)

public class LocalStateTest {

    @Test // this method is mostly for hands-on convenient testing
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

        GitHubJSONTestSuite.runStateTest(json, excluded);


    }

    @Test
    public void stCallDelegateCodes() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = getJSON("stCallDelegateCodes");
        excluded.add("callcodecallcodecallcode_ABCB_RECURSIVE");
        excluded.add("callcodecallcallcode_101");
        excluded.add("callcodecallcodecall_ABCB_RECURSIVE");
        excluded.add("callcallcodecall_ABCB_RECURSIVE");
        excluded.add("callcallcodecallcode_011");
        excluded.add("callcodecallcall_100");
        excluded.add("callcallcodecall_010");
        excluded.add("callcallcallcode_ABCB_RECURSIVE");
        excluded.add("callcodecallcall_ABCB_RECURSIVE");
        excluded.add("callcallcode_01");
        excluded.add("callcodecallcodecall_110");
        excluded.add("callcallcallcode_001");
        excluded.add("callcallcodecallcode_ABCB_RECURSIVE");
        excluded.add("callcodecall_10");
        excluded.add("callcodecallcallcode_ABCB_RECURSIVE");
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stCallDelegateCodesCallCode() throws ParseException, IOException {

        Set<String> excluded = new HashSet<>();
        String json = getJSON("stCallDelegateCodesCallCode");
        excluded.add("callcodecallcodecallcode_ABCB_RECURSIVE");
        excluded.add("callcodecallcallcode_101");
        excluded.add("callcodecallcodecall_ABCB_RECURSIVE");
        excluded.add("callcallcodecall_ABCB_RECURSIVE");
        excluded.add("callcallcodecallcode_011");
        excluded.add("callcodecallcall_100");
        excluded.add("callcallcodecall_010");
        excluded.add("callcallcallcode_ABCB_RECURSIVE");
        excluded.add("callcodecallcodecallcode_111_SuicideEnd");
        excluded.add("callcodecallcall_ABCB_RECURSIVE");
        excluded.add("callcallcode_01");
        excluded.add("callcodecallcodecall_110");
        excluded.add("callcallcallcode_001");
        excluded.add("callcallcodecallcode_ABCB_RECURSIVE");
        excluded.add("callcodecall_10");
        excluded.add("callcodecallcallcode_ABCB_RECURSIVE");
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stHomeSteadSpecific() throws ParseException, IOException {

        Set<String> excluded = new HashSet<>();
        String json = getJSON("stHomeSteadSpecific");
        excluded.add("contractCreationOOGdontLeaveEmptyContract");
        excluded.add("createContractViaContractOOGInitCode");
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stCallCreateCallCodeTest() throws ParseException, IOException {

        Set<String> excluded = new HashSet<>();
        String json = getJSON("stCallCreateCallCodeTest");
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

        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stDelegatecallTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("stDelegatecallTest");
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
        excluded.add("CallContractToCreateContractWhichWouldCreateContractIfCalled");
        excluded.add("CallContractToCreateContractOOGBonusGas");
        excluded.add("CallRecursiveContract");

        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stLogTests() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("stLogTests");
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
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stPreCompiledContracts() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = getJSON("stPreCompiledContracts");
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
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stMemoryStressTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        excluded.add("mload32bitBound_return2");// The test extends memory to 4Gb which can't be handled with Java arrays
        excluded.add("mload32bitBound_return"); // The test extends memory to 4Gb which can't be handled with Java arrays
        excluded.add("mload32bitBound_Msize"); // The test extends memory to 4Gb which can't be handled with Java arrays
        String json = getJSON("stMemoryStressTest");
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stMemoryTest() throws ParseException, IOException {
        String json = getJSON("stMemoryTest");
        Set<String> excluded = new HashSet<>();

        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stQuadraticComplexityTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("stQuadraticComplexityTest");
        excluded.add("Call1MB1024Calldepth");
        excluded.add("Call50000_sha256");

        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stSolidityTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("stSolidityTest");
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
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stRecursiveCreate() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = getJSON("stRecursiveCreate");
        excluded.add("recursiveCreateReturnValue");
        excluded.add("recursiveCreate");
        excluded.add("testRandomTest");
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stRefundTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = getJSON("stRefundTest");
        excluded.add("refund_singleSuicide");
        excluded.add("refund_multimpleSuicide");
        excluded.add("refund600");
        excluded.add("refund50percentCap");
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stSpecialTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = getJSON("stSpecialTest");
        excluded.add("makeMoney");
        excluded.add("JUMPDEST_Attack");
        excluded.add("JUMPDEST_AttackwithJump");
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stBlockHashTest() throws ParseException, IOException {
        String json = getJSON("stBlockHashTest");
        Set<String> excluded = new HashSet<>();

        excluded.add("blockhash0");
        excluded.add("blockhashDOS-sec71");
        GitHubJSONTestSuite.runStateTest(json,excluded);
    }

    @Test
    public void stSystemOperationsTest() throws IOException {

        Set<String> excluded = new HashSet<>();
        String json = getJSON("stSystemOperationsTest");
        excluded.add("createWithInvalidOpcode");
        excluded.add("CallRecursiveBombLog2");
        excluded.add("CallRecursiveBombLog");
        excluded.add("CallRecursiveBomb0_OOG_atMaxCallDepth");
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
        excluded.add("ABAcallsSuicide1");
        excluded.add("ABAcalls1");
        excluded.add("ABAcalls2");
        excluded.add("ABAcalls3");
        excluded.add("CallToNameRegistrator0");
        excluded.add("ABAcalls0");
        excluded.add("CallRecursiveBomb3");
        excluded.add("CallRecursiveBomb2");
        excluded.add("CallToNameRegistratorAddressTooBigRight");
        excluded.add("CallRecursiveBomb1");
        excluded.add("CallRecursiveBomb0");
        excluded.add("CallToNameRegistratorAddressTooBigLeft");
        excluded.add("CallToReturn1");
        excluded.add("CallToNameRegistratorZeorSizeMemExpansion");
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stTransactionTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();
        String json = getJSON("stTransactionTest");
        excluded.add("TransactionNonceCheck2");
        excluded.add("TransactionNonceCheck");
        excluded.add("InternalCallHittingGasLimit2");
        excluded.add("SuicidesAndInternlCallSuicidesBonusGasAtCall");
        excluded.add("InternlCallStoreClearsOOG");
        excluded.add("StoreClearsAndInternlCallStoreClearsOOG");
        excluded.add("InternalCallHittingGasLimitSuccess");
        excluded.add("StoreGasOnCreate");
        excluded.add("SuicidesAndInternlCallSuicidesSuccess");
        excluded.add("InternlCallStoreClearsSucces");
        excluded.add("StoreClearsAndInternlCallStoreClearsSuccess");
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Test
    public void stTransitionTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = getJSON("stTransitionTest");
        excluded.add("createNameRegistratorPerTxsNotEnoughGasBefore");
        excluded.add("delegatecallAtTransition");
        excluded.add("delegatecallBeforeTransition");
        excluded.add("delegatecallAfterTransition");
        excluded.add("createNameRegistratorPerTxsBefore");
        GitHubJSONTestSuite.runStateTest(json, excluded);
    }

    @Test
    public void stShiftTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        Set<String> fileNames = getFileNames("stShift");
        for (String fileName : fileNames) {
            String json = getJSON("stShift/"+fileName);
            GitHubJSONTestSuite.runGeneralStateTest(json, excluded);
        }
    }

    private Set<String> getFileNames(String folderName){
        Set<String> fileNames = new HashSet<>();
        File dir = new File("src/test/resources/json/StateTests/"+folderName);
        for (File file: dir.listFiles()) {
            if (FilenameUtils.getExtension(file.getName()).equals("json") )
                fileNames.add(FilenameUtils.removeExtension(file.getName()));
        }

        return fileNames;
    }

    @Ignore
    @Test
    public void stWalletTest() throws ParseException, IOException {
        Set<String> excluded = new HashSet<>();

        String json = getJSON("stWalletTest");
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
        excluded.add("walletConstruction");
        excluded.add("multiOwnedChangeRequirementTo2");
        excluded.add("multiOwnedIsOwnerTrue");
        excluded.add("walletChangeOwnerRemovePendingTransaction");
        excluded.add("multiOwnedIsOwnerFalse");
        excluded.add("multiOwnedChangeOwnerNoArguments");
        excluded.add("dayLimitSetDailyLimitNoData");
        excluded.add("walletExecuteOverDailyLimitMultiOwner");
        excluded.add("dayLimitConstruction");
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
        GitHubJSONTestSuite.runStateTest(json, excluded);

    }

    @Ignore
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

