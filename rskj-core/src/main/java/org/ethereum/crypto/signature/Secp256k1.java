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
import org.bitcoin.NativeSecp256k1;
import org.bitcoin.NativeSecp256k1Util;
import org.bitcoin.Secp256k1Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Class in charge of being the access point to implementations of all the Signature related functionality.
 * It returns an instance of {@link Secp256k1Service}
 * Is implemented as a Singleton, so the only way to access an instance is through getInstance().
 */
public final class Secp256k1 {

    private static final String NATIVE_LIB = "native";
    private static final Logger logger = LoggerFactory.getLogger(Secp256k1.class);

    private static Secp256k1Service instance = new Secp256k1ServiceBC();
    private static boolean initialized = false;

    private Secp256k1() {
    }

    /**
     * <p> It should be called only once in Node Startup.</p>
     *
     * <p> It reads "crypto.library" property to decide which impl to initialize.</p>
     *
     * <p> By default it initialize Bouncy Castle impl.</p>
     *
     * @param @{@link Nullable} rskSystemProperties = Could be null in tests.
     */
    public static synchronized void initialize(@Nullable RskSystemProperties rskSystemProperties) {
        // Just a warning for duplicate initialization.
        if (initialized) {
            logger.warn("Instance was already initialized. This could be either for duplicate initialization or calling to getInstance before init.");
        } else {
            initialized = true;
        }
        if (rskSystemProperties != null) {
            String cryptoLibrary = rskSystemProperties.cryptoLibrary();
            logger.debug("Trying to initialize Signature Service: {}.", cryptoLibrary);
            if (NATIVE_LIB.equals(cryptoLibrary)) {
                if(Secp256k1Context.isEnabled()){
                    instance = new Secp256k1ServiceNative();
                } else {
                    logger.debug("Signature Service {} not available, initializing Bouncy Castle.", cryptoLibrary);
                    instance = new Secp256k1ServiceBC();
                }
            } else {
                instance = new Secp256k1ServiceBC();
            }
        } else {
            logger.warn("Empty system properties.");
        }
    }

    /**
     * As a singleton this should be the only entry point for creating instances of SignatureService classes.
     *
     * @return either {@link Secp256k1ServiceBC} or Native Signature (future) implementation.
     */
    public static Secp256k1Service getInstance() {
        return instance;
    }

    @VisibleForTesting
    static void reset() {
        instance = new Secp256k1ServiceBC();
        initialized = false;
    }
}