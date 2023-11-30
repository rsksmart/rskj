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

package co.rsk.rpc.modules.eth;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.transactionRevertedExecutionError;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.unknownError;

import java.util.Optional;
import java.util.function.Function;

import org.ethereum.config.Constants;
import org.ethereum.core.Blockchain;
import org.ethereum.core.TransactionPool;
import org.ethereum.db.TransactionInfo;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.program.ProgramResult;

import co.rsk.core.Wallet;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.crypto.Keccak256;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.net.TransactionGateway;
import co.rsk.util.HexUtils;

public class EthModuleTransactionInstant extends EthModuleTransactionBase {

    private final MinerServer minerServer;
    private final MinerClient minerClient;
    private final Blockchain blockchain;
    private final BlockExecutor blockExecutor;

    public EthModuleTransactionInstant(
            Constants constants,
            Wallet wallet,
            TransactionPool transactionPool,
            MinerServer minerServer,
            MinerClient minerClient,
            Blockchain blockchain,
            TransactionGateway transactionGateway,
            BlockExecutor blockExecutor) {
        super(constants, wallet, transactionPool, transactionGateway);

        this.minerServer = minerServer;
        this.minerClient = minerClient;
        this.blockchain = blockchain;
        this.blockExecutor = blockExecutor;
    }

    @Override
    public synchronized String sendTransaction(CallArguments args) {
        try {
            this.blockExecutor.setRegisterProgramResults(true);

            String txHash = super.sendTransaction(args);

            mineTransaction();

            return getReturnMessage(txHash);
        }
        finally {
            this.blockExecutor.setRegisterProgramResults(false);
        }
    }

    @Override
    public String sendRawTransaction(String rawData) {
        return internalSendRawTransaction(rawData, r -> super.sendRawTransaction(r));
    }

    private String internalSendRawTransaction(String rawData, Function<String, String> fun) {
        try {
            this.blockExecutor.setRegisterProgramResults(true);

            String txHash = fun.apply(rawData);

            mineTransaction();

            return getReturnMessage(txHash);
        }
        finally {
            this.blockExecutor.setRegisterProgramResults(false);
        }
    }

    @Override
    public String sendEncryptedTransaction(String rawData) {
        return internalSendRawTransaction(rawData, r -> super.sendEncryptedTransaction(r));
    }

    private void mineTransaction() {
        minerServer.buildBlockToMine(false);
        minerClient.mineBlock();
    }

    /**
     * When insta-mining we can query the transaction status and return an error response immediately like Ganache.
     * This does not apply during regular operation because queued transactions are not immediately executed.
     */
    private String getReturnMessage(String txHash) {
        TransactionInfo transactionInfo = blockchain.getTransactionInfo(HexUtils.stringHexToByteArray(txHash));
        if (transactionInfo == null) {
            throw unknownError("Unknown error when sending transaction: transaction wasn't mined");
        }

        Keccak256 hash = new Keccak256(txHash.substring(2));
        ProgramResult programResult = this.blockExecutor.getProgramResult(hash);

        if (programResult != null && programResult.isRevert()) {
            Optional<String> revertReason = EthModule.decodeRevertReason(programResult);

            if (revertReason.isPresent()) {
                throw RskJsonRpcRequestException.transactionRevertedExecutionError(revertReason.get());
            } else {
                throw RskJsonRpcRequestException.transactionRevertedExecutionError();
            }
        }

        if (!transactionInfo.getReceipt().isSuccessful()) {
            throw transactionRevertedExecutionError();
        }

        return txHash;
    }
}
