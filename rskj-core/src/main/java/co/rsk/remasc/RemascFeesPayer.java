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

package co.rsk.remasc;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.rpc.modules.trace.CallType;
import co.rsk.rpc.modules.trace.ProgramSubtrace;
import org.ethereum.core.Repository;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.TransferInvoke;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Knows how to transfer funds between accounts and how to log that transaction.
 *
 * @author martin.medina
 */
class RemascFeesPayer {

    private final Repository repository;
    private final RskAddress contractAddress;
    private final List<ProgramSubtrace> subtraces = new ArrayList<>();

    public RemascFeesPayer(Repository repository, RskAddress contractAddress) {
        this.repository = repository;
        this.contractAddress = contractAddress;
    }

    public List<ProgramSubtrace> getSubtraces() { return Collections.unmodifiableList(this.subtraces); }

    public void payMiningFees(byte[] blockHash, Coin value, RskAddress toAddress, List<LogInfo> logs) {
        this.transferPayment(value, toAddress);
        this.logPayment(blockHash, value, toAddress, logs);
    }

    private void transferPayment(Coin value, RskAddress toAddress) {
        this.repository.addBalance(contractAddress, value.negate());
        this.repository.addBalance(toAddress, value);

        DataWord from = DataWord.valueOf(contractAddress.getBytes());
        DataWord to = DataWord.valueOf(toAddress.getBytes());
        long gas = 0L;
        DataWord amount = DataWord.valueOf(value.getBytes());

        TransferInvoke invoke = new TransferInvoke(from, to, gas, amount);
        ProgramResult result     = new ProgramResult();
        ProgramSubtrace subtrace = ProgramSubtrace.newCallSubtrace(CallType.CALL, invoke, result, null, Collections.emptyList());

        this.subtraces.add(subtrace);
    }

    private void logPayment(byte[] blockHash, Coin value, RskAddress toAddress, List<LogInfo> logs) {

        byte[] loggerContractAddress = this.contractAddress.getBytes();
        List<DataWord> topics = Arrays.asList(RemascContract.MINING_FEE_TOPIC, DataWord.valueOf(toAddress.getBytes()));
        byte[] data = RLP.encodeList(RLP.encodeElement(blockHash), RLP.encodeCoin(value));

        logs.add(new LogInfo(loggerContractAddress, topics, data));
    }
}
