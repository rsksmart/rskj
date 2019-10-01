/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

import co.rsk.core.RskAddress;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.trace.ProgramTrace;

public class TraceTransformer {
    private TraceTransformer() {

    }

    public static ActionTransactionTrace toAction(ProgramTrace trace) {
        ProgramInvoke invoke = trace.getProgramInvoke();

        String from = new RskAddress(invoke.getCallerAddress().getLast20Bytes()).toJsonString();
        String to = new RskAddress(invoke.getOwnerAddress().getLast20Bytes()).toJsonString();
        String gas = TypeConverter.toQuantityJsonHex(invoke.getGas());
        String input = TypeConverter.toUnformattedJsonHex(invoke.getDataCopy(DataWord.ZERO, invoke.getDataSize()));
        String callType = "call";
        String value;

        DataWord callValue = invoke.getCallValue();

        if (callValue.isZero())
            value = "0x";
        else
            value = TypeConverter.toQuantityJsonHex(callValue.getData());

        return new ActionTransactionTrace(
                callType,
                from,
                to,
                gas,
                input,
                value
        );
    }
}
