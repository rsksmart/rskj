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

import co.rsk.config.RemascConfig;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.panic.PanicProcessor;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.ReceiptStore;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.Program;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.encoders.Hex;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Remasc contract which manages the distribution of miner fees.
 * This class just extends PrecompiledContract, checks no wrong data was supplied and delegates to
 * Remasc the actual fee distribution logic
 * @author Oscar Guindzberg
 */
public class RemascContract extends PrecompiledContracts.PrecompiledContract {

    private static final Logger logger = LoggerFactory.getLogger(RemascContract.class);
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private static final CallTransaction.Function PROCESS_MINERS_FEES = CallTransaction.Function.fromSignature("processMinersFees", new String[]{}, new String[]{});
    public static final CallTransaction.Function GET_STATE_FOR_DEBUGGING = CallTransaction.Function.fromSignature("getStateForDebugging", new String[]{}, new String[]{"bytes"});

    static final DataWord MINING_FEE_TOPIC = new DataWord(TypeConverter.stringToByteArray("mining_fee_topic"));
    public static final String REMASC_CONFIG = "remasc.json";

    private final RskSystemProperties config;
    private final RemascConfig remascConfig;

    private final Map<ByteArrayWrapper, CallTransaction.Function> functions;
    private Remasc remasc;

    public RemascContract(RskSystemProperties config, RemascConfig remascConfig, RskAddress contractAddress) {
        this.config = config;
        this.remascConfig = remascConfig;
        this.contractAddress = contractAddress;
        this.functions = new HashMap<>();
        this.functions.put(new ByteArrayWrapper(PROCESS_MINERS_FEES.encodeSignature()), PROCESS_MINERS_FEES);
        this.functions.put(new ByteArrayWrapper(GET_STATE_FOR_DEBUGGING.encodeSignature()), GET_STATE_FOR_DEBUGGING);
    }

    @Override
    public long getGasForData(byte[] data) {
        // changes here?
        return 0;
    }

    @Override
    public void init(Transaction executionTx, Block executionBlock, Repository repository, BlockStore blockStore, ReceiptStore receiptStore, List<LogInfo> logs) {
        this.remasc = new Remasc(this.config, repository, blockStore, remascConfig, executionTx, contractAddress, executionBlock, logs);
    }

    @Override
    public byte[] execute(byte[] data) {
        try {
            CallTransaction.Function function = this.getFunction(data);
            Method m = this.getClass().getMethod(function.name);
            Object result = this.invoke(m);
            remasc.save();
            return this.encodeResult(function, result);
        } catch(RemascInvalidInvocationException ex) {
            // Remasc contract was invoked with invalid parameters / out of place, throw OutOfGasException to avoid funds being transferred
            logger.warn(ex.getMessage(), ex);
            throw new Program.OutOfGasException(ex.getMessage());
        } catch(Exception ex) {
            logger.error(ex.getMessage(), ex);
            panicProcessor.panic("remascExecute", ex.getMessage());

            throw new RemascException("Exception executing remasc", ex);
        }
    }

    public void processMinersFees() throws Exception {
        logger.trace("processMinersFees");

        remasc.processMinersFees();
    }

    public byte[] getStateForDebugging() throws Exception {
        logger.trace("getStateForDebugging");

        return remasc.getStateForDebugging().getEncoded();
    }

    private CallTransaction.Function getFunction(byte[] data) {
        CallTransaction.Function function;

        if (data == null || data.length == 0) {
            function = PROCESS_MINERS_FEES;
        } else {
            if (data.length != 4) {
                logger.warn("Invalid function: signature longer than expected {}.", Hex.toHexString(data));
                throw new RemascInvalidInvocationException("Invalid function signature");
            }

            byte[] functionSignature = Arrays.copyOfRange(data, 0, 4);
            function = functions.get(new ByteArrayWrapper(functionSignature));

            if (function == null) {
                logger.warn("Invalid function: signature does not match an existing function {}.", Hex.toHexString(functionSignature));
                throw new RemascInvalidInvocationException("Invalid function signature");
            }
        }
        return function;
    }

    private Object invoke(Method m) throws InvocationTargetException, IllegalAccessException {
        try {
            return m.invoke(this);
        } catch (InvocationTargetException ite) {
            if (ite.getTargetException() instanceof RemascInvalidInvocationException) {
                logger.warn(ite.getTargetException().getMessage(), ite.getTargetException());
                throw (RemascInvalidInvocationException) ite.getTargetException();
            } else {
                throw ite;
            }
        }
    }

    private byte[] encodeResult(CallTransaction.Function function, Object result) {
        if (result == null) {
            return new byte[0];
        }

        return function.encodeOutputs(result);
    }
}