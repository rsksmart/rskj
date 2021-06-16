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

import co.rsk.pcc.exception.NativeContractIllegalArgumentException;
import org.ethereum.core.CallTransaction;

/**
 * This class contains common behavior for a single RSK native contract's method.
 *
 * @author Ariel Mendelzon
 */
public abstract class NativeMethod {
    public final class WithArguments {
        private final Object[] arguments;
        private final byte[] originalData;

        public WithArguments(Object[] arguments, byte[] originalData) {
            this.arguments = arguments;
            this.originalData = originalData;
        }

        public NativeMethod getMethod() {
            return NativeMethod.this;
        }

        public long getGas() {
            return NativeMethod.this.getGas(arguments, originalData);
        }

        public Object[] getArguments() {
            return arguments;
        }

        public byte[] getOriginalData() {
            return originalData;
        }

        public Object execute() throws NativeContractIllegalArgumentException {
            return NativeMethod.this.execute(arguments);
        }
    }

    private ExecutionEnvironment executionEnvironment;

    public NativeMethod(ExecutionEnvironment executionEnvironment) {
        this.executionEnvironment = executionEnvironment;
    }

    public ExecutionEnvironment getExecutionEnvironment() {
        return executionEnvironment;
    }

    public abstract CallTransaction.Function getFunction();

    public abstract Object execute(Object[] arguments) throws NativeContractIllegalArgumentException;

    public long getGas(Object[] parsedArguments, byte[] originalData) {
        // By default, gas is twice the length of the original data passed in
        // This can (AND SHOULD) be overriden in implementing methods, although
        // the default can always be used on top.
        // (e.g., "return 23000L + super(parsedArguments, originalData);")

        return originalData == null ? 0 : originalData.length * 2L;
    }

    public abstract boolean isEnabled();

    public abstract boolean onlyAllowsLocalCalls();

    public String getName() {
        // Can be overriden to provide a more specific name
        return getFunction().name;
    }
}
