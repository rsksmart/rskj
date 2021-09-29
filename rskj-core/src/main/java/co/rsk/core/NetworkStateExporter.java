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
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import co.rsk.panic.PanicProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ethereum.core.Blockchain;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final RepositoryLocator repositoryLocator;
    private final Blockchain blockchain;

    private static final PanicProcessor panicProcessor = new PanicProcessor();

    public NetworkStateExporter(RepositoryLocator repositoryLocator, Blockchain blockchain) {
        this.repositoryLocator = repositoryLocator;
        this.blockchain = blockchain;
    }

    public boolean exportStatus(String outputFile) {
        return exportStatus(outputFile, "",true,true);
    }

    public boolean exportStatus(String outputFile,String accountKey, boolean exportStorageKeys,boolean exportCode) {
        RepositorySnapshot frozenRepository = repositoryLocator.snapshotAt(blockchain.getBestBlock().getHeader());

        File dumpFile = new File(outputFile);

        try(FileWriter fw = new FileWriter(dumpFile.getAbsoluteFile()); BufferedWriter bw = new BufferedWriter(fw)) {
            JsonNodeFactory jsonFactory = new JsonNodeFactory(false);
            ObjectNode mainNode = jsonFactory.objectNode();
            if (accountKey.length()==0) {
                for (RskAddress addr : frozenRepository.getAccountsKeys()) {
                    if (!addr.equals(RskAddress.nullAddress())) {
                        mainNode.set(addr.toString(), createAccountNode(mainNode, addr, frozenRepository, exportStorageKeys,exportCode));
                    }
                }
            } else {
                RskAddress addr = new RskAddress(accountKey);
                if (!addr.equals(RskAddress.nullAddress())) {
                    mainNode.set(addr.toString(), createAccountNode(mainNode, addr, frozenRepository, exportStorageKeys,exportCode));
                }

            }
            ObjectMapper mapper = new ObjectMapper();
            ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
            bw.write(writer.writeValueAsString(mainNode));
            return true;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            panicProcessor.panic("dumpstate", e.getMessage());
            return false;
        }
    }

    private ObjectNode createContractNode(ObjectNode accountNode, AccountInformationProvider accountInformation, RskAddress addr, Iterator<DataWord> contractKeys,boolean exportCode) {
        ObjectNode contractNode = accountNode.objectNode();
        if (exportCode) {
            byte[] code = accountInformation.getCode(addr);
            String codeStr = "";
            if (code == null) {
                codeStr = "<empty>";
            } else {
                codeStr = ByteUtil.toHexString(code);
            }
            contractNode.put("code", codeStr);
        }
        contractNode.put("codeHash", accountInformation.getCodeHashStandard(addr).toHexString());

        if (contractKeys!=null) {
            ObjectNode dataNode = contractNode.objectNode();
            while (contractKeys.hasNext()) {
                DataWord key = contractKeys.next();
                byte[] value = accountInformation.getStorageBytes(addr, key);
                dataNode.put(ByteUtil.toHexString(key.getData()), ByteUtil.toHexString(value));
            }
            contractNode.set("data", dataNode);
        }
        return contractNode;
    }

    private ObjectNode createAccountNode(ObjectNode mainNode, RskAddress addr, AccountInformationProvider accountInformation,
                                         boolean exportStorageKeys,
                                         boolean exportCode) {
        ObjectNode accountNode = mainNode.objectNode();
        Coin balance = accountInformation.getBalance(addr);
        accountNode.put("balance", balance.asBigInteger().toString());
        BigInteger nonce = accountInformation.getNonce(addr);
        accountNode.put("nonce", nonce.toString());

        if (accountInformation.isContract(addr)) {
            Iterator<DataWord> contractKeys = null;
            if (exportStorageKeys) {
                contractKeys = accountInformation.getStorageKeys(addr);
            }
            if (((contractKeys == null) || contractKeys.hasNext()) && !PrecompiledContracts.REMASC_ADDR.equals(addr)) {
                accountNode.set("contract", createContractNode(accountNode, accountInformation, addr, contractKeys,exportCode));
            }
        }

        return accountNode;
    }
}
