/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.pcc;

import co.rsk.core.RskAddress;
import co.rsk.panic.PanicProcessor;
import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContractArgs;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * This class contains common behavior for a RSK native contract.
 *
 * @author Ariel Mendelzon
 */
public abstract class NativeContract extends PrecompiledContracts.PrecompiledContract {
    private static final Logger logger = LoggerFactory.getLogger(NativeContract.class);
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private final ActivationConfig activationConfig;

    private ExecutionEnvironment executionEnvironment;

    public NativeContract(ActivationConfig activationConfig, RskAddress contractAddress) {
        this.contractAddress = contractAddress;
        this.activationConfig = activationConfig;
    }

    public ExecutionEnvironment getExecutionEnvironment() {
        return executionEnvironment;
    }

    public abstract List<NativeMethod> getMethods();

    public abstract Optional<NativeMethod> getDefaultMethod();

    public void before() {
        // Can be overriden to provide logic that executes right before each method execution
    }

    public void after() {
        // Can be overriden to provide logic that executes right after each method execution
    }

    @Override
    public void init(PrecompiledContractArgs args) {
        super.init(args);

        executionEnvironment = new ExecutionEnvironment(
                activationConfig,
                args.getTransaction(),
                args.getExecutionBlock(),
                args.getRepository(),
                args.getBlockStore(),
                args.getReceiptStore(),
                args.getLogs()
        );
    }

    @Override
    public long getGasForData(byte[] data) {
        // Preliminary validation: we need an execution environment
        if (executionEnvironment == null) {
            throw new RuntimeException("Execution environment is null");
        }

        Optional<NativeMethod.WithArguments> methodWithArguments = parseData(data);

        if (!methodWithArguments.isPresent()) {
            // TODO: Define whether we should log or even do something different in this case.
            // TODO: I reckon this is fine since execution doesn't actually go ahead and
            // TODO: inexistent method logging happens within parseData anyway.
            return 0L;
        }

        return methodWithArguments.get().getGas();
    }

    @Override
    public byte[] execute(byte[] data) throws VMException {
        try
        {
            // Preliminary validation: we need an execution environment
            if (executionEnvironment == null) {
                throw new VMException("Execution environment is null");
            }

            // Preliminary validation: the transaction on which we execute cannot be null
            if (executionEnvironment.getTransaction() == null) {
                throw new VMException("RSK Transaction is null");
            }

            Optional<NativeMethod.WithArguments> methodWithArguments = parseData(data);

            // No function found with the given data? => halt!
            if (!methodWithArguments.isPresent()) {
                String errorMessage = String.format("Invalid data given: %s.", ByteUtil.toHexString(data));
                logger.info(errorMessage);
                throw new NativeContractIllegalArgumentException(errorMessage);
            }

            NativeMethod method = methodWithArguments.get().getMethod();

            // If this is not a local call, then first check whether the function
            // allows for non-local calls

            if (!executionEnvironment.isLocalCall() && method.onlyAllowsLocalCalls()) {
                String errorMessage = String.format("Non-local-call to %s. Returning without execution.", method.getName());
                logger.info(errorMessage);
                throw new NativeContractIllegalArgumentException(errorMessage);
            }

            before();

            Object result;
            try {
                result = methodWithArguments.get().execute();
            } catch (NativeContractIllegalArgumentException ex) {
                String errorMessage = String.format("Error executing: %s", method.getName());
                logger.warn(errorMessage, ex);
                throw new NativeContractIllegalArgumentException(errorMessage, ex);
            }

            after();

            // Special cases:
            // - null => null
            // - empty Optional<?> => null
            // - nonempty Optional<?> => encoded ?
            // Note: this is hacky, but ultimately very short and to the point.
            if (result == null) {
                return null;
            } else if (result.getClass() == Optional.class) {
                Optional<?> optionalResult = (Optional<?>) result;
                if (!optionalResult.isPresent()) {
                    return null;
                } else {
                    result = optionalResult.get();
                }
            }

            return method.getFunction().encodeOutputs(result);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            panicProcessor.panic("nativecontractexecute", ex.getMessage());
            throw new VMException(String.format("Exception executing native contract: %s", ex.getMessage()), ex);
        }
    }

    private Optional<NativeMethod.WithArguments> parseData(byte[] data) {
        if (data != null && (data.length >= 1 && data.length <= 3)) {
            logger.warn("Invalid function signature {}.", ByteUtil.toHexString(data));
            return Optional.empty();
        }

        if (data == null || data.length == 0) {
            if (getDefaultMethod().isPresent()) {
                return Optional.of(this.getDefaultMethod().get().new WithArguments(new Object[]{}, data));
            }
            return Optional.empty();
        } else {
            byte[] encodedSignature = Arrays.copyOfRange(data, 0, 4);
            Optional<NativeMethod> maybeMethod = getMethods().stream()
                    .filter(m ->
                            Arrays.equals(
                                    encodedSignature,
                                    m.getFunction().encodeSignature()
                            )
                    ).findFirst();

            if (!maybeMethod.isPresent()) {
                logger.warn("Invalid function signature {}.", ByteUtil.toHexString(encodedSignature));
                return Optional.empty();
            }

            NativeMethod method = maybeMethod.get();

            if (!method.isEnabled()) {
                logger.warn("'{}' is not enabled", method.getName());
                return Optional.empty();
            }

            try {
                Object[] arguments = method.getFunction().decode(data);
                return Optional.of(method.new WithArguments(arguments, data));
            } catch (Exception e) {
                logger.warn(String.format("Invalid arguments %s for function %s.", ByteUtil.toHexString(data), ByteUtil.toHexString(encodedSignature)), e);
                return Optional.empty();
            }
        }
    }
}
