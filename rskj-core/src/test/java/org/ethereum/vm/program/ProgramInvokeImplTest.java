package org.ethereum.vm.program;

import org.ethereum.core.Repository;
import org.ethereum.db.BlockStore;
import org.ethereum.db.BlockStoreDummy;
import org.ethereum.jsontestsuite.builder.RepositoryBuilder;
import org.ethereum.vm.program.invoke.ProgramInvokeImpl;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ProgramInvokeImplTest {

    @Test
    public void testEquals_OK() {

        byte[] address1 = new byte[]{1};
        byte[] origin1 = new byte[]{2};
        byte[] caller1 = new byte[]{3};
        byte[] balance1 = new byte[]{4};
        byte[] gasPrice1 = new byte[]{5};
        byte[] gas1 = new byte[]{6};
        byte[] callValue1 = new byte[]{7};
        byte[] msgData1 = new byte[]{8};
        byte[] lastHash1 = new byte[]{9};
        byte[] coinbase1 = new byte[]{10};
        long timestamp1 = 1;
        long number1 = 2;
        int transactionIndex1 = 3;
        byte[] difficulty1 = new byte[]{11};
        byte[] gasLimit1 = new byte[]{12};
        Repository repository1 = RepositoryBuilder.build(Collections.emptyMap());
        BlockStore blockStore1 = new BlockStoreDummy();
        boolean byTestingSuite1 = true;

        byte[] address2 = new byte[]{12};
        byte[] origin2 = new byte[]{11};
        byte[] caller2 = new byte[]{10};
        byte[] balance2 = new byte[]{9};
        byte[] gasPrice2 = new byte[]{8};
        byte[] gas2 = new byte[]{7};
        byte[] callValue2 = new byte[]{6};
        byte[] msgData2 = new byte[]{5};
        byte[] lastHash2 = new byte[]{4};
        byte[] coinbase2 = new byte[]{3};
        long timestamp2 = 3;
        long number2 = 1;
        int transactionIndex2 = 2;
        byte[] difficulty2 = new byte[]{2};
        byte[] gasLimit2 = new byte[]{1};
        Repository repository2 = null;
        BlockStore blockStore2 = null;
        boolean byTestingSuite2 = false;

        // An object must be equal to itself

        ProgramInvokeImpl programInvokeA = new ProgramInvokeImpl(address1, origin1, caller1, balance1, gasPrice1, gas1, callValue1, msgData1, lastHash1, coinbase1, timestamp1, number1, transactionIndex1, difficulty1, gasLimit1, repository1, blockStore1, byTestingSuite1);

        assertEquals(programInvokeA, programInvokeA);

        // An object must be different from null

        assertNotEquals(programInvokeA, null);

        // Same property values make objects to be equal

        ProgramInvokeImpl programInvokeB = new ProgramInvokeImpl(address1, origin1, caller1, balance1, gasPrice1, gas1, callValue1, msgData1, lastHash1, coinbase1, timestamp1, number1, transactionIndex1, difficulty1, gasLimit1, repository1, blockStore1, byTestingSuite1);

        assertEquals(programInvokeA, programInvokeB);

        // Different combinations of property values make objects to be different

        ProgramInvokeImpl programInvokeC = new ProgramInvokeImpl(address2, origin2, caller2, balance2, gasPrice2, gas2, callValue2, msgData2, lastHash2, coinbase2, timestamp2, number2, transactionIndex2, difficulty2, gasLimit2, repository2, blockStore2, byTestingSuite2);
        ProgramInvokeImpl programInvokeD = new ProgramInvokeImpl(address2, origin2, caller2, balance1, gasPrice2, gas2, callValue2, msgData2, lastHash2, coinbase2, timestamp2, number1, transactionIndex2, difficulty2, gasLimit2, repository2, blockStore2, byTestingSuite2);
        ProgramInvokeImpl programInvokeE = new ProgramInvokeImpl(address2, origin1, caller1, balance1, gasPrice1, gas1, callValue1, msgData1, lastHash1, coinbase1, timestamp1, number1, transactionIndex1, difficulty1, gasLimit1, repository1, blockStore1, byTestingSuite1);

        assertNotEquals(programInvokeA, programInvokeC);
        assertNotEquals(programInvokeA, programInvokeD);
        assertNotEquals(programInvokeA, programInvokeE);
        assertNotEquals(programInvokeC, programInvokeD);
        assertNotEquals(programInvokeD, programInvokeE);

    }

    @Test
    public void testHashcode_OK() {

        byte[] address1 = new byte[]{1};
        byte[] origin1 = new byte[]{2};
        byte[] caller1 = new byte[]{3};
        byte[] balance1 = new byte[]{4};
        byte[] gasPrice1 = new byte[]{5};
        byte[] gas1 = new byte[]{6};
        byte[] callValue1 = new byte[]{7};
        byte[] msgData1 = new byte[]{8};
        byte[] lastHash1 = new byte[]{9};
        byte[] coinbase1 = new byte[]{10};
        long timestamp1 = 1;
        long number1 = 2;
        int transactionIndex1 = 3;
        byte[] difficulty1 = new byte[]{11};
        byte[] gasLimit1 = new byte[]{12};
        Repository repository1 = RepositoryBuilder.build(Collections.emptyMap());
        BlockStore blockStore1 = new BlockStoreDummy();
        boolean byTestingSuite1 = true;

        byte[] address2 = new byte[]{12};
        byte[] origin2 = new byte[]{11};
        byte[] caller2 = new byte[]{10};
        byte[] balance2 = new byte[]{9};
        byte[] gasPrice2 = new byte[]{8};
        byte[] gas2 = new byte[]{7};
        byte[] callValue2 = new byte[]{6};
        byte[] msgData2 = new byte[]{5};
        byte[] lastHash2 = new byte[]{4};
        byte[] coinbase2 = new byte[]{3};
        long timestamp2 = 3;
        long number2 = 1;
        int transactionIndex2 = 2;
        byte[] difficulty2 = new byte[]{2};
        byte[] gasLimit2 = new byte[]{1};
        Repository repository2 = null;
        BlockStore blockStore2 = null;
        boolean byTestingSuite2 = false;

        // Same properties included in the hashcode makes hashcode to be equal

        ProgramInvokeImpl programInvokeA = new ProgramInvokeImpl(address1, origin1, caller1, balance1, gasPrice1, gas1, callValue1, msgData1, lastHash1, coinbase1, timestamp1, number1, transactionIndex1, difficulty1, gasLimit1, repository1, blockStore1, byTestingSuite1);
        ProgramInvokeImpl programInvokeB = new ProgramInvokeImpl(address1, origin1, caller1, balance1, gasPrice1, gas1, callValue1, msgData1, lastHash1, coinbase1, timestamp1, number1, transactionIndex1, difficulty1, gasLimit1, repository1, blockStore1, byTestingSuite1);

        assertEquals(programInvokeA.hashCode(), programInvokeB.hashCode());

        // Different combinations of property values makes hashcode to be different

        ProgramInvokeImpl programInvokeC = new ProgramInvokeImpl(address2, origin2, caller2, balance2, gasPrice2, gas2, callValue2, msgData2, lastHash2, coinbase2, timestamp2, number2, transactionIndex2, difficulty2, gasLimit2, repository2, blockStore2, byTestingSuite2);

        assertNotEquals(programInvokeA.hashCode(), programInvokeC.hashCode());

    }

}
