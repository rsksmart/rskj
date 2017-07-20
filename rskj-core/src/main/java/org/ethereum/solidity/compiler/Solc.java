/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.solidity.compiler;

import org.ethereum.config.SystemProperties;

import java.io.File;
import java.io.IOException;

/**
 * Created by Anton Nashatyrev on 03.03.2016.
 */
public class Solc {

    private File solc = null;

    Solc(SystemProperties config) {
        try {
            init(config);
        } catch (IOException e) {
            throw new RuntimeException("Can't init solc compiler: ", e);
        }
    }

    private void init(SystemProperties config) throws IOException {
        if (config != null && config.customSolcPath() != null) {
            solc = new File(config.customSolcPath());
            if (!solc.canExecute()) {
                throw new RuntimeException(String.format(
                        "Solidity compiler from config solc.path: %s is not a valid executable",
                        config.customSolcPath()
                ));
            }
        } else {
            throw new RuntimeException("No Solc version provided. Check if 'solc.path' config is set.");
        }
    }



    public File getExecutable() {
        return solc;
    }

}
