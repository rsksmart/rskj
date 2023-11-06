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

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

import co.rsk.pcc.VotingMocks;
import co.rsk.util.RLPException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.config.Constants;
import org.ethereum.core.*;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.FhStore;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.TransactionArgumentsUtil;
import org.rsksmart.BFV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import co.rsk.net.TransactionGateway;
import co.rsk.util.HexUtils;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class EthModuleTransactionBase implements EthModuleTransaction {

    protected static final Logger LOGGER = LoggerFactory.getLogger("web3");

    private final Wallet wallet;
    private final TransactionPool transactionPool;
    private final Constants constants;
    private final TransactionGateway transactionGateway;

    public EthModuleTransactionBase(Constants constants, Wallet wallet, TransactionPool transactionPool, TransactionGateway transactionGateway) {
        this.wallet = wallet;
        this.transactionPool = transactionPool;
        this.constants = constants;
        this.transactionGateway = transactionGateway;
    }

    @Override
    public synchronized String sendTransaction(CallArguments args) {

        Account senderAccount = this.wallet.getAccount(new RskAddress(args.getFrom()));

        if (senderAccount == null) {
            throw RskJsonRpcRequestException.invalidParamError(TransactionArgumentsUtil.ERR_COULD_NOT_FIND_ACCOUNT + args.getFrom());
        }

        String txHash = null;

        try {

            synchronized (transactionPool) {

                TransactionArguments txArgs = TransactionArgumentsUtil.processArguments(args, transactionPool, senderAccount, constants.getChainId());

                Transaction tx = Transaction.builder().withTransactionArguments(txArgs).build();

                tx.sign(senderAccount.getEcKey().getPrivKeyBytes());

                if (!tx.acceptTransactionSignature(constants.getChainId())) {
                    throw RskJsonRpcRequestException.invalidParamError(TransactionArgumentsUtil.ERR_INVALID_CHAIN_ID + args.getChainId());
                }

                TransactionPoolAddResult result = transactionGateway.receiveTransaction(tx.toImmutableTransaction());

                if (!result.transactionsWereAdded()) {
                    throw RskJsonRpcRequestException.transactionError(result.getErrorMessage());
                }

                txHash = tx.getHash().toJsonString();
            }

            return txHash;

        } finally {
            LOGGER.debug("eth_sendTransaction({}): {}", args, txHash);
        }
    }

    @Override
    public String sendRawTransaction(String rawData) {
        String s = null;
        try {
            Transaction tx = new ImmutableTransaction(HexUtils.stringHexToByteArray(rawData));

            if (null == tx.getGasLimit() || null == tx.getGasPrice() || null == tx.getValue()) {
                throw invalidParamError("Missing parameter, gasPrice, gas or value");
            }

            if (!tx.acceptTransactionSignature(constants.getChainId())) {
                throw RskJsonRpcRequestException.invalidParamError(TransactionArgumentsUtil.ERR_INVALID_CHAIN_ID + tx.getChainId());
            }

            TransactionPoolAddResult result = transactionGateway.receiveTransaction(tx);
            if (!result.transactionsWereAdded()) {
                throw RskJsonRpcRequestException.transactionError(result.getErrorMessage());
            }

            return s = tx.getHash().toJsonString();
        } catch (RLPException e) {
            throw invalidParamError("Invalid input: " + e.getMessage(), e);
        } finally {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("eth_sendRawTransaction({}): {}", rawData, s);
            }
        }
    }

    @Override
    public String sendEncryptedTransaction(String rawData) {
        try {
            EncryptedTransaction encryptedTransaction = new EncryptedTransaction(HexUtils.stringHexToByteArray(rawData));
            BFV bfv = new BFV();
            Transaction tx = encryptedTransaction.getTransaction();

            // transcipher encrypted params
            VotingMocks vm = new ObjectMapper().readValue(new File(
                            "/Users/fedejinich/Projects/rskj/rskj-core/src/test/java/org/ethereum/vm/bfv/votes.json"),
                    VotingMocks.class);
            byte[] data = encryptedTransaction.getEncryptedParams();
            byte[] pastaSK = vm.getPastaSK();
            byte[] rks = vm.getRk();
            byte[] bfvSK = vm.getBfvSK();
            byte[] fhData = bfv.transcipher(data, data.length, pastaSK, pastaSK.length, rks, rks.length,
                    bfvSK, bfvSK.length);
            byte[] hash = Keccak256Helper.keccak256(fhData);
            FhStore.getInstance().put(hash, fhData); // store encrypted params, so they can be accessed within tx execution

            tx.getSender(); // todo(fedejinich) signature cache is bringing problems, i had to do this ugly thing :(, research about it!
            tx = addEncryptedParams(tx, hash); // add encrypted params to tx.data

            // for the future, add logic to set keys to fetch encrypted data from storage

            if (null == tx.getGasLimit() || null == tx.getGasPrice() || null == tx.getValue()) {
                throw invalidParamError("Missing parameter, gasPrice, gas or value");
            }

            // todo(fedejinich) i think i don't need this
//            if (!tx.acceptTransactionSignature(constants.getChainId())) {
//                throw RskJsonRpcRequestException.invalidParamError(TransactionArgumentsUtil.ERR_INVALID_CHAIN_ID + tx.getChainId());
//            }

            TransactionPoolAddResult result = transactionGateway.receiveTransaction(tx);
            if (!result.transactionsWereAdded()) {
                throw RskJsonRpcRequestException.transactionError(result.getErrorMessage());
            }

            return tx.getHash().toJsonString();
        } catch (RLPException e) {
            throw invalidParamError("Invalid input: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // todo(fedejinich) support this
//            if (LOGGER.isDebugEnabled()) {
//                LOGGER.debug("rsk_sendEncryptedTransaction({}): {}", rawData, s);
//            }
        }
    }

    private Transaction addEncryptedParams(Transaction tx, byte[] encryptedParamsHash) {
        byte[] data = tx.getData();

//        CallTransaction.Function fun = CallTransaction.Function.fromSignature(
//                "vote2",
//                new String[]{"bytes"}
//        );
//        CallTransaction.Function fun = CallTransaction.Function.fromSignature("vote2", new String[]{"bytes32"});
//        byte[] encodedData = fun.encode(encryptedParamsHash);
        CallTransaction.Function fun = CallTransaction.Function.fromSignature("vote2");
        byte[] encodedData = fun.encode();

//        ByteBuffer buff = ByteBuffer.allocate(data.length + 32);
//        buff.put(data);
//        buff.put(encryptedParamsHash);

//        tx.setData(buff.array());
        tx.setData(encodedData);

        return tx;
    }
}
