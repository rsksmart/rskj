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

import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.panic.PanicProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.encoders.Hex;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Iterator;

/**
 * Created by mario on 13/01/17.
 */
public class NetworkStateExporter {
    private static final Logger logger = LoggerFactory.getLogger(NetworkStateExporter.class);

    private Repository repository;

    private static final PanicProcessor panicProcessor = new PanicProcessor();

    public NetworkStateExporter(Repository repository) {
        this.repository = repository;
    }

    public boolean exportStatus(String outputFile) {
        // This will only work if GlobalKeyMap.enabled = true when building the whole
        // repository, since keys are not stored on disk.
        Repository frozenRepository = this.repository.getSnapshotTo(this.repository.getRoot());

        File dumpFile = new File(outputFile);

        try(FileWriter fw = new FileWriter(dumpFile.getAbsoluteFile()); BufferedWriter bw = new BufferedWriter(fw)) {
            JsonNodeFactory jsonFactory = new JsonNodeFactory(false);
            ObjectNode mainNode = jsonFactory.objectNode();
            for (RskAddress addr : frozenRepository.getAccountsKeys()) {
                if(!addr.equals(RskAddress.nullAddress())) {
                    mainNode.set(addr.toString(), createAccountNode(mainNode, addr, frozenRepository));
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

    private ObjectNode createContractNode(ObjectNode accountNode, AccountInformationProvider accountInformation, RskAddress addr, Iterator<DataWord> contractKeys) {
        ObjectNode contractNode = accountNode.objectNode();
        contractNode.put("code", Hex.toHexString(accountInformation.getCode(addr)));
        ObjectNode dataNode = contractNode.objectNode();
        while (contractKeys.hasNext()) {
            DataWord key = contractKeys.next();
            byte[] value = accountInformation.getStorageBytes(addr, key);
            dataNode.put(Hex.toHexString(key.getData()), Hex.toHexString(value));
        }
        contractNode.set("data", dataNode);
        return contractNode;
    }

    private ObjectNode createAccountNode(ObjectNode mainNode, RskAddress addr, AccountInformationProvider accountInformation) {
        ObjectNode accountNode = mainNode.objectNode();
        Coin balance = accountInformation.getBalance(addr);
        accountNode.put("balance", balance.asBigInteger().toString());
        BigInteger nonce = accountInformation.getNonce(addr);
        accountNode.put("nonce", nonce.toString());
        Iterator<DataWord> contractKeys = accountInformation.getStorageKeys(addr);
        if (contractKeys.hasNext() && !PrecompiledContracts.REMASC_ADDR.equals(addr)) {
            accountNode.set("contract", createContractNode(accountNode, accountInformation, addr, contractKeys));
        }
        return accountNode;
    }
}
