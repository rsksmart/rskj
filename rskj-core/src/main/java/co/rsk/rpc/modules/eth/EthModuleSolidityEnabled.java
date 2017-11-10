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

package co.rsk.rpc.modules.eth;

import org.ethereum.core.CallTransaction;
import org.ethereum.rpc.dto.CompilationInfoDTO;
import org.ethereum.rpc.dto.CompilationResultDTO;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class EthModuleSolidityEnabled implements EthModuleSolidity {

    private static final Logger LOGGER = LoggerFactory.getLogger("web3");

    private final SolidityCompiler solidityCompiler;

    public EthModuleSolidityEnabled(SolidityCompiler solidityCompiler) {
        this.solidityCompiler = solidityCompiler;
    }

    @Override
    public Map<String, CompilationResultDTO> compileSolidity(String contract) throws Exception {
        Map<String, CompilationResultDTO> compilationResultDTOMap = new HashMap<>();
        try {
            SolidityCompiler.Result res = solidityCompiler.compile(contract.getBytes(StandardCharsets.UTF_8), true, SolidityCompiler.Options.ABI, SolidityCompiler.Options.BIN);
            if (!res.errors.isEmpty()) {
                throw new RuntimeException("Compilation error: " + res.errors);
            }
            org.ethereum.solidity.compiler.CompilationResult result = org.ethereum.solidity.compiler.CompilationResult.parse(res.output);
            org.ethereum.solidity.compiler.CompilationResult.ContractMetadata contractMetadata = result.contracts.values().iterator().next();

            CompilationInfoDTO compilationInfo = new CompilationInfoDTO();
            compilationInfo.setSource(contract);
            compilationInfo.setLanguage("Solidity");
            compilationInfo.setLanguageVersion("0");
            compilationInfo.setCompilerVersion(result.version);
            compilationInfo.setAbiDefinition(new CallTransaction.Contract(contractMetadata.abi));

            CompilationResultDTO compilationResult = new CompilationResultDTO(contractMetadata, compilationInfo);
            String contractName = (String)result.contracts.keySet().toArray()[0];

            compilationResultDTOMap.put(contractName, compilationResult);

            return compilationResultDTOMap;
        } finally {
            LOGGER.debug("eth_compileSolidity(" + contract + ")" + compilationResultDTOMap);
        }
    }
}