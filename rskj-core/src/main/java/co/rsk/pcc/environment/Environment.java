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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Environment extends NativeContract {
    private ProgramInvoke programInvoke;

    public Environment(ActivationConfig activationConfig, RskAddress contractAddress) {
        super(activationConfig, contractAddress);
    }

    @Override
    public void init(PrecompiledContractArgs args) {
        this.programInvoke = args.getProgramInvoke();
    }

    @Override
    public List<NativeMethod> getMethods() {
        return Collections.emptyList();
    }

    @Override
    public Optional<NativeMethod> getDefaultMethod() {
        return Optional.empty();
    }
}