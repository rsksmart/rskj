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

package co.rsk.pcc.bto;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.panic.PanicProcessor;
import co.rsk.peg.*;
import co.rsk.peg.bitcoin.MerkleBranch;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.peg.utils.BtcTransactionFormatUtils;
import co.rsk.peg.whitelist.LockWhitelistEntry;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import com.google.common.annotations.VisibleForTesting;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.Program;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Precompiled contract that provides certain BTO (Bitcoin Token Offering)
 * related utility functions.
 * @author Ariel Mendelzon
 */
public class BTOUtils extends PrecompiledContracts.PrecompiledContract {
    private static final Logger logger = LoggerFactory.getLogger("bridge");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    // Returns the public key of the federator at the specified index
    public static final CallTransaction.Function GET_FEDERATOR_PUBLIC_KEY = BridgeMethods.GET_FEDERATOR_PUBLIC_KEY.getFunction();

    private final RskSystemProperties config;

    private BridgeSupport bridgeSupport;

    public BTOUtils(RskSystemProperties config, RskAddress contractAddress) {
        this.contractAddress = contractAddress;
        this.config = config;
    }

    @Override
    public long getGasForData(byte[] data) {
        if (!blockchainConfig.isRskip88() && BridgeUtils.isContractTx(rskTx)) {
            logger.warn("Call from contract before Orchid");
            throw new NullPointerException();
        }

        if (BridgeUtils.isFreeBridgeTx(rskTx, rskExecutionBlock.getNumber(), config.getBlockchainConfig())) {
            return 0;
        }

        BridgeParsedData bridgeParsedData = parseData(data);

        Long functionCost;
        Long totalCost;
        if (bridgeParsedData == null) {
            functionCost = BridgeMethods.RELEASE_BTC.getCost();
            totalCost = functionCost;
        } else {
            functionCost = bridgeParsedData.bridgeMethod.getCost();
            int dataCost = data == null ? 0 : data.length * 2;

            totalCost = functionCost + dataCost;
        }

        return totalCost;
    }

    @VisibleForTesting
    BridgeParsedData parseData(byte[] data) {
        BridgeParsedData bridgeParsedData = new BridgeParsedData();

        if (data != null && (data.length >= 1 && data.length <= 3)) {
            logger.warn("Invalid function signature {}.", Hex.toHexString(data));
            return null;
        }

        if (data == null || data.length == 0) {
            bridgeParsedData.bridgeMethod = BridgeMethods.RELEASE_BTC;
            bridgeParsedData.args = new Object[]{};
        } else {
            byte[] functionSignature = Arrays.copyOfRange(data, 0, 4);
            Optional<BridgeMethods> invokedMethod = BridgeMethods.findBySignature(functionSignature);
            if (!invokedMethod.isPresent()) {
                logger.warn("Invalid function signature {}.", Hex.toHexString(functionSignature));
                return null;
            }
            bridgeParsedData.bridgeMethod = invokedMethod.get();
            try {
                bridgeParsedData.args = bridgeParsedData.bridgeMethod.getFunction().decode(data);
            } catch (Exception e) {
                logger.warn("Invalid function arguments {} for function {}.", Hex.toHexString(data), Hex.toHexString(functionSignature));
                return null;
            }
        }

        if (!bridgeParsedData.bridgeMethod.isEnabled(this.blockchainConfig)) {
            logger.warn("'{}' is not enabled to run",bridgeParsedData.bridgeMethod.name());
            return null;
        }

        return bridgeParsedData;
    }

    // Parsed rsk transaction data field
    private static class BridgeParsedData {
        public BridgeMethods bridgeMethod;
        public Object[] args;
    }

    @Override
    public void init(Transaction rskTx, Block rskExecutionBlock, Repository repository, BlockStore rskBlockStore, ReceiptStore rskReceiptStore, List<LogInfo> logs) {
        this.rskTx = rskTx;
        this.rskExecutionBlock = rskExecutionBlock;
        this.repository = repository;
        this.logs = logs;
        this.blockchainConfig = blockchainNetConfig.getConfigForBlock(rskExecutionBlock.getNumber());
    }

    @Override
    public byte[] execute(byte[] data) {
        try
        {
            // Preliminary validation: the transaction on which we execute cannot be null
            if (rskTx == null) {
                throw new RuntimeException("Rsk Transaction is null");
            }

            BridgeParsedData bridgeParsedData = parseData(data);

            // Function parsing from data returned null => invalid function selected, halt!
            if (bridgeParsedData == null) {
                String errorMessage = String.format("Invalid data given: %s.", Hex.toHexString(data));
                logger.info(errorMessage);
                if (blockchainConfig.isRskip88()) {
                    throw new BridgeIllegalArgumentException(errorMessage);
                }

                return null;
            }

            // If this is not a local call, then first check whether the function
            // allows for non-local calls
            if (blockchainConfig.isRskip88() && !isLocalCall() && bridgeParsedData.bridgeMethod.onlyAllowsLocalCalls()) {
                String errorMessage = String.format("Non-local-call to %s. Returning without execution.", bridgeParsedData.bridgeMethod.getFunction().name);
                logger.info(errorMessage);
                throw new BridgeIllegalArgumentException(errorMessage);
            }

            this.bridgeSupport = setup();

            Optional<?> result;
            try {
                // bridgeParsedData.function should be one of the CallTransaction.Function declared above.
                // If the user tries to call an non-existent function, parseData() will return null.
                result = bridgeParsedData.bridgeMethod.getExecutor().execute(this, bridgeParsedData.args);
            } catch (BridgeIllegalArgumentException ex) {
                String errorMessage = String.format("Error executing: %s", bridgeParsedData.bridgeMethod);
                logger.warn(errorMessage, ex);
                if (blockchainConfig.isRskip88()) {
                    throw new BridgeIllegalArgumentException(errorMessage);
                }

                return null;
            }

            teardown();

            return result.map(bridgeParsedData.bridgeMethod.getFunction()::encodeOutputs).orElse(null);
        }
        catch(Exception ex) {
            logger.error(ex.getMessage(), ex);
            panicProcessor.panic("bridgeexecute", ex.getMessage());
            throw new RuntimeException(String.format("Exception executing bridge: %s", ex.getMessage()), ex);
        }
    }

    private BridgeSupport setup() {
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(this.bridgeConstants, this.logs);
        return new BridgeSupport(this.config, repository, eventLogger, contractAddress, rskExecutionBlock);
    }

    private void teardown() throws IOException {
        bridgeSupport.save();
    }
}
