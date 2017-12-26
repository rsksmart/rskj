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

package co.rsk.core;

import co.rsk.panic.PanicProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.ContractDetails;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;

/**
 * Created by mario on 13/01/17.
 */
public class NetworkStateExporter {
    private static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};
    private static final Logger logger = LoggerFactory.getLogger(NetworkStateExporter.class);

    private Repository repository;

    private static final PanicProcessor panicProcessor = new PanicProcessor();

    public NetworkStateExporter(Repository repository) {
        this.repository = repository;
    }

    public boolean exportStatus(String outputFile) {
        Repository frozenRepository = this.repository.getSnapshotTo(this.repository.getRoot());

        File dumpFile = new File(outputFile);

        try(FileWriter fw = new FileWriter(dumpFile.getAbsoluteFile()); BufferedWriter bw = new BufferedWriter(fw)) {
            JsonNodeFactory jsonFactory = new JsonNodeFactory(false);
            ObjectNode mainNode = jsonFactory.objectNode();
            for (ByteArrayWrapper address : frozenRepository.getAccountsKeys()) {
                if(!address.equals(new ByteArrayWrapper(ZERO_BYTE_ARRAY))) {
                    mainNode.set(Hex.toHexString(address.getData()), createAccountNode(mainNode, address.getData(), frozenRepository));
                }
            }
            ObjectMapper mapper = new ObjectMapper();
            ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
            bw.write(writer.writeValueAsString(mainNode));
            return true;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            panicProcessor.panic("dumpstate", e.getMessage());
            return false;
        }
    }

    private ObjectNode createContractNode(ContractDetails contractDetails, ObjectNode accountNode) {
        ObjectNode contractNode = accountNode.objectNode();
        contractNode.put("code", Hex.toHexString(contractDetails.getCode()));
        ObjectNode dataNode = contractNode.objectNode();
        for (DataWord key : contractDetails.getStorageKeys()) {
            byte[] value = contractDetails.getBytes(key);
            dataNode.put(Hex.toHexString(key.getData()), Hex.toHexString(value));
        }
        contractNode.set("data", dataNode);
        return contractNode;
    }

    private ObjectNode createAccountNode(ObjectNode mainNode, byte[] address, Repository frozenRepository) {
        ObjectNode accountNode = mainNode.objectNode();
        AccountState accountState = frozenRepository.getAccountState(address);
        BigInteger balance = accountState.getBalance();
        accountNode.put("balance", balance.toString());
        BigInteger nonce = accountState.getNonce();
        accountNode.put("nonce", nonce.toString());
        ContractDetails contractDetails = frozenRepository.getContractDetails(address);
        RskAddress addWrapper = new RskAddress(address);
        if (!contractDetails.isNullObject() && !PrecompiledContracts.REMASC_ADDR.equals(addWrapper)) {
            accountNode.set("contract", createContractNode(contractDetails, accountNode));
        }
        return accountNode;
    }
}
