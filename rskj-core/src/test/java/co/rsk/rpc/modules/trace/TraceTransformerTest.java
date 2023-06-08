/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.rpc.modules.trace;

import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TraceTransformerTest {
    @Test
    void getActionFromInvokeData() {
        DataWord address = DataWord.valueOf(1);
        DataWord origin = DataWord.valueOf(2);
        DataWord caller = DataWord.valueOf(3);
        long gas = 1000000;
        DataWord callValue = DataWord.valueOf(100000);
        byte[] data = new byte[]{0x01, 0x02, 0x03, 0x04};

        ProgramInvoke invoke = new ProgramInvokeImpl(
                address,
                origin,
                caller,
                null,
                null,
                gas,
                callValue,
                data,
                null, null, null, null, null, null,
                null, null, 0, null, false, false);

        TraceAction action = TraceTransformer.toAction(TraceType.CALL, invoke, CallType.CALL, null, null, null);

        Assertions.assertNotNull(action);

        Assertions.assertEquals("call", action.getCallType());
        Assertions.assertEquals("0x0000000000000000000000000000000000000001", action.getTo());
        Assertions.assertEquals("0x0000000000000000000000000000000000000003", action.getFrom());
        Assertions.assertEquals("0x01020304", action.getInput());
        Assertions.assertEquals("0xf4240", action.getGas());
        Assertions.assertEquals("0x186a0", action.getValue());
    }

    @Test
    void getActionFromInvokeDataWithCreationData() {
        DataWord address = DataWord.valueOf(1);
        DataWord origin = DataWord.valueOf(2);
        DataWord caller = DataWord.valueOf(3);
        long gas = 1000000;
        DataWord callValue = DataWord.valueOf(100000);
        byte[] data = new byte[]{0x01, 0x02, 0x03, 0x04};

        ProgramInvoke invoke = new ProgramInvokeImpl(
                address,
                origin,
                caller,
                null,
                null,
                gas,
                callValue,
                null,
                null, null, null, null, null, null,
                null, null, 0, null, false, false);

        TraceAction action = TraceTransformer.toAction(TraceType.CREATE, invoke, CallType.NONE, data, null, null);

        Assertions.assertNotNull(action);

        Assertions.assertNull(action.getCallType());
        Assertions.assertNull(action.getTo());
        Assertions.assertEquals("0x0000000000000000000000000000000000000003", action.getFrom());
        Assertions.assertEquals("0x01020304", action.getInit());
        Assertions.assertNull(action.getCreationMethod());
        Assertions.assertEquals("0xf4240", action.getGas());
        Assertions.assertEquals("0x186a0", action.getValue());
    }

    @Test
    void getActionFromInvokeDataWithCreationDataUsingCreationMethod() {
        DataWord address = DataWord.valueOf(1);
        DataWord origin = DataWord.valueOf(2);
        DataWord caller = DataWord.valueOf(3);
        long gas = 1000000;
        DataWord callValue = DataWord.valueOf(100000);
        byte[] data = new byte[]{0x01, 0x02, 0x03, 0x04};

        ProgramInvoke invoke = new ProgramInvokeImpl(
                address,
                origin,
                caller,
                null,
                null,
                gas,
                callValue,
                null,
                null, null, null, null, null, null,
                null, null, 0, null, false, false);

        TraceAction action = TraceTransformer.toAction(TraceType.CREATE, invoke, CallType.NONE, data, "create2", null);

        Assertions.assertNotNull(action);

        Assertions.assertNull(action.getCallType());
        Assertions.assertNull(action.getTo());
        Assertions.assertEquals("0x0000000000000000000000000000000000000003", action.getFrom());
        Assertions.assertEquals("0x01020304", action.getInit());
        Assertions.assertEquals("create2", action.getCreationMethod());
        Assertions.assertEquals("0xf4240", action.getGas());
        Assertions.assertEquals("0x186a0", action.getValue());
    }
}
