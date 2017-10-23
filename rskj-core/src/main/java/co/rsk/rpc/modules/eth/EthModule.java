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

import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.facade.Ethereum;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.dto.CompilationResultDTO;
import org.ethereum.rpc.exception.JsonRpcUnimplementedMethodException;
import org.ethereum.vm.program.ProgramResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.ethereum.rpc.TypeConverter.toJsonHex;

// TODO add all RPC methods
@Component
public class EthModule
    implements EthModuleSolidity, EthModuleWallet {

    private static final Logger LOGGER = LoggerFactory.getLogger("web3");

    private final Ethereum eth;
    private final EthModuleSolidity ethModuleSolidity;
    private final EthModuleWallet ethModuleWallet;

    @Autowired
    public EthModule(Ethereum eth, EthModuleSolidity ethModuleSolidity, EthModuleWallet ethModuleWallet) {
        this.eth = eth;
        this.ethModuleSolidity = ethModuleSolidity;
        this.ethModuleWallet = ethModuleWallet;
    }

    @Override
    public String[] accounts() {
        return ethModuleWallet.accounts();
    }

    public String call(Web3.CallArguments args, String bnOrId) {
        String s = null;
        try {
            if (!"latest".equals(bnOrId)) {
                throw new JsonRpcUnimplementedMethodException("Method only supports 'latest' as a parameter so far.");
            }

            ProgramResult res = createCallTxAndExecute(args);
            return s = toJsonHex(res.getHReturn());
        } finally {
            LOGGER.debug("eth_call(): {}" + s);
        }
    }

    @Override
    public Map<String, CompilationResultDTO> compileSolidity(String contract) throws Exception {
        return ethModuleSolidity.compileSolidity(contract);
    }

    public String estimateGas(Web3.CallArguments args) {
        String s = null;
        try {
            ProgramResult res = createCallTxAndExecute(args);
            return s = toJsonHex(res.getGasUsed());
        } finally {
            LOGGER.debug("eth_estimateGas(): {}" + s);
        }
    }

    @Override
    public String sendTransaction(Web3.CallArguments args) {
        return ethModuleWallet.sendTransaction(args);
    }

    private ProgramResult createCallTxAndExecute(Web3.CallArguments args) {
        byte[] nonce = new byte[]{0};
        Transaction tx = Transaction.create(nonce, args);

        // sign with an empty key because we don't need to use a real key for constant calls
        tx.sign(new byte[32]);

        // TODO inject Blockchain through constructor if necessary
        Block block = eth.getWorldManager().getBlockchain().getBestBlock();

        return eth.callConstantCallTransaction(tx, block);
    }

    public String sign(String addr, String data) {
        return ethModuleWallet.sign(addr, data);
    }
}