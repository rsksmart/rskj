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

package org.ethereum.cli;

import co.rsk.config.RskSystemProperties;
import org.ethereum.config.SystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static java.lang.System.exit;
import static java.lang.System.out;

/**
 * @author Roman Mandeleil
 * @since 13.11.2014
 */
@Component
public class CLIInterface {

    private static final Logger logger = LoggerFactory.getLogger("cli");

    public static Map<String, String> call(RskSystemProperties config, String[] args) {

        Map<String, String> cliOptions = new HashMap<>();

        try {
            for (int i = 0; i < args.length; ++i) {

                // override the db directory
                if ("--help".equals(args[i])) {

                    printHelp();
                    exit(1);
                }

                // override the db directory
                if (i + 1 < args.length && "-db".equals(args[i])) {
                    String db = args[i + 1];
                    logger.info("DB directory set to [{}]", db);
                    cliOptions.put(SystemProperties.PROPERTY_DB_DIR, db);
                }

                // override the listen port directory
                if (i + 1 < args.length && "-listen".equals(args[i])) {
                    String port = args[i + 1];
                    logger.info("Listen port set to [{}]", port);
                    cliOptions.put(SystemProperties.PROPERTY_LISTEN_PORT, port);
                }

                // override the connect host:port directory
                if (i + 1 < args.length && "-connect".equals(args[i])) {
                    String connectStr = args[i + 1];
                    logger.info("Connect URI set to [{}]", connectStr);
                    URI uri = new URI(connectStr);
                    if (!"enode".equals(uri.getScheme()))
                        throw new RuntimeException("expecting URL in the format enode://PUBKEY@HOST:PORT");
                    cliOptions.put(SystemProperties.PROPERTY_PEER_ACTIVE, "[{url='" + connectStr + "'}]");
                }

                // override the listen port directory
                if (i + 1 < args.length && "-reset".equals(args[i])) {
                    Boolean resetStr = interpret(args[i + 1]);
                    logger.info("Resetting db set to [{}]", resetStr);
                    cliOptions.put(SystemProperties.PROPERTY_DB_RESET, resetStr.toString());
                }

                // TODO added parameter
                // override the rpc parameter
                if ("-rpc".equals(args[i])) {
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        String portStr =  args[i + 1];
                        cliOptions.put(SystemProperties.PROPERTY_RPC_PORT, portStr);
                        logger.info("RPC port set to [{}]", portStr);
                    }

                    cliOptions.put(SystemProperties.PROPERTY_RPC_ENABLED, "true");
                }

                // override the rpc cors parameter
                if ("-rpccors".equals(args[i])) {
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        String headerStr =  args[i + 1];

                        if (headerStr.contains("\r") || headerStr.contains("\n"))
                            throw new IllegalArgumentException("rpccors");

                        cliOptions.put(SystemProperties.PROPERTY_RPC_CORS, headerStr);
                        logger.info("RPC CORS header set to [{}]", headerStr);
                    }

                    cliOptions.put(SystemProperties.PROPERTY_RPC_ENABLED, "true");
                }
            }

            logger.info("Overriding config file with CLI options: " + cliOptions);
            config.overrideParams(cliOptions);

        } catch (Exception e) {
            logger.error("Error parsing command line", e);
            exit(1);
        }

        return cliOptions;
    }

    private static Boolean interpret(String arg) {

        if ("on".equals(arg) || "true".equals(arg) || "yes".equals(arg))
            return Boolean.TRUE;
        if ("off".equals(arg) || "false".equals(arg) || "no".equals(arg))
            return Boolean.FALSE;

        throw new Error("Can't interpret the answer: " + arg);
    }

    public static void printHelp() {

        out.println("--help                -- this help message ");
        out.println("-reset <yes/no>       -- reset yes/no the all database ");
        out.println("-db <db>              -- to setup the path for the database directory ");
        out.println("-listen  <port>       -- port to listen on for incoming connections ");
        out.println("-connect <host:port>  -- address actively connect to  ");
        out.println("");
        out.println("e.g: cli -reset no -db db-1 -listen 20202 -connect poc-7.ethdev.com:30300 ");
        out.println("");
    }
}
