/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package org.ethereum.crypto.signature;

import co.rsk.config.RskSystemProperties;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class in charge of being the access point to implementations of all the Signature related functionality.
 * It returns an instance of {@link Secp256k1Service}
 * Is implemented as a Singleton, so the only way to access an instance is through getInstance().
 */
public abstract class Secp256k1 {

    private static final String NATIVE_LIB = "native";
    private static final Logger logger = LoggerFactory.getLogger(Secp256k1.class);

    private static Secp256k1Service instance;

    /**
     * <p> It should be called only once in Node Startup.</p>
     *
     * <p> It reads "crypto.library" property to decide which impl to initialize.</p>
     *
     * <p> By default it initialize Bouncy Castle impl.</p>
     *
     * @param rskSystemProperties
     */
    public final static synchronized void initialize(RskSystemProperties rskSystemProperties) {
        // Just a warning for duplicate initialization.
        if (instance != null) {
            logger.warn("Instance was already initialized. This could be either for duplicate initialization or calling to getInstance before init.");
        }
        String cryptoLibrary = rskSystemProperties.cryptoLibrary();
        logger.debug("Initializing Signature Service: {}.", cryptoLibrary);
        if (NATIVE_LIB.equals(cryptoLibrary)) {//TODO: Check if Native library is loaded.
            instance = new Secp256K1ServiceNative();
        } else {
            instance = new Secp256k1ServiceBC();
        }
    }

    /**
     * As a singleton this should be the only entry point for creating instances of SignatureService classes.
     *
     * @return either {@link Secp256k1ServiceBC} or Native Signature (future) implementation.
     */
    public static final Secp256k1Service getInstance() {
        if (instance == null) {
            setDefault();
        }
        return instance;
    }

    private static synchronized void setDefault() {
        instance = new Secp256k1ServiceBC();
    }

    @VisibleForTesting
    static final void reset() {
        instance = null;
    }
}