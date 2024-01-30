/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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
package co.rsk.pcc.environment;

import co.rsk.core.RskAddress;
import co.rsk.pcc.NativeContract;
import co.rsk.pcc.NativeMethod;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.vm.PrecompiledContractArgs;
import org.ethereum.vm.program.invoke.ProgramInvoke;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * This precompiled contract contains a method called GetCallStackDepth which uses the getCallDeep method
 * from ProgramInvoke in order to obtain the current call stack depth, this information can be request by
 * contracts that need to verify the space left in the call stack before forwarding commands to other contracts
 * further down their execution flow.
 *
 * Currently, within the project this contract may be called from 2 places the callToPrecompiledAddress() method in
 * the Program class or the call() method in TransactionExecutor, for the later the ProgramInvoke instance is
 * obtained from a Program instance.
 *
 * In a normal execution of this contract the GetCallStackDepth gets called and in its execution it uses the
 * programInvoke instance sent to get the current call stack, adds 1 to it since calling this contract consumes
 * 1 call stack position and returns the resulting value.
 *
 * There may be cases when programInvoke gets set to null, for example if Environment gets called directly from
 * a transaction instead of being called from another contract's logic. In those cases the GetCallStackDepth' execution
 * should assume a call stack depth of 0 and add the call stack position it consumes in order to return a minimum
 * value of 1.
 */
public class Environment extends NativeContract {
    @Nullable
    private ProgramInvoke programInvoke;

    public Environment(ActivationConfig activationConfig, RskAddress contractAddress) {
        super(activationConfig, contractAddress);
    }

    @Override
    public void init(PrecompiledContractArgs args) {
        super.init(args);
        this.programInvoke = args.getProgramInvoke();
    }

    @Override
    public List<NativeMethod> getMethods() {
        return Arrays.asList(
                new GetCallStackDepth(getExecutionEnvironment(), programInvoke)
        );
    }

    @Override
    public Optional<NativeMethod> getDefaultMethod() {
        return Optional.empty();
    }
}
