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

package co.rsk.pcc.bto;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.panic.PanicProcessor;
import co.rsk.pcc.NativeContract;
import co.rsk.pcc.NativeMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Precompiled contract that provides certain BTO (Bitcoin Token Offering)
 * related utility functions.
 *
 * @author Ariel Mendelzon
 */
public class BTOUtils extends NativeContract {
    private final BTOUtilsHelper helper;

    public BTOUtils(RskSystemProperties config, RskAddress contractAddress) {
        super(config, contractAddress);
        this.helper = new BTOUtilsHelper();
    }

    @Override
    public List<NativeMethod> getMethods() {
        return Arrays.asList(
            new ToBase58Check(getExecutionEnvironment()),
            new DeriveExtendedPublicKey(getExecutionEnvironment(), helper),
            new ExtractPublicKeyFromExtendedPublicKey(getExecutionEnvironment(), helper),
            new GetMultisigScriptHash(getExecutionEnvironment())
        );
    }

    @Override
    public Optional<NativeMethod> getDefaultMethod() {
        return Optional.empty();
    }
}
