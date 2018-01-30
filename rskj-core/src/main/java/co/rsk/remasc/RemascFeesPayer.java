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

import co.rsk.core.commons.RskAddress;
import org.ethereum.core.Repository;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

/**
 * Knows how to transfer funds between accounts and how to log that transaction.
 *
 * @author martin.medina
 */
class RemascFeesPayer {

    private final Repository repository;

    private final RskAddress contractAddress;

    public RemascFeesPayer(Repository repository, RskAddress contractAddress) {
        this.repository = repository;
        this.contractAddress = contractAddress;
    }

    public void payMiningFees(byte[] blockHash, BigInteger value, RskAddress toAddress, List<LogInfo> logs) {
        this.transferPayment(value, toAddress);
        this.logPayment(blockHash, value, toAddress, logs);
    }

    private void transferPayment(BigInteger value, RskAddress toAddress) {
        this.repository.addBalance(contractAddress, value.negate());
        this.repository.addBalance(toAddress, value);
    }

    private void logPayment(byte[] blockHash, BigInteger value, RskAddress toAddress, List<LogInfo> logs) {

        byte[] loggerContractAddress = this.contractAddress.getBytes();
        List<DataWord> topics = Arrays.asList(RemascContract.MINING_FEE_TOPIC, new DataWord(toAddress.getBytes()));
        byte[] data = RLP.encodeList(RLP.encodeElement(blockHash), RLP.encodeBigInteger(value));

        logs.add(new LogInfo(loggerContractAddress, topics, data));
    }
}
