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

import co.rsk.rpc.CorsConfigurationTest;
import org.ethereum.config.SystemProperties;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

/**
 * Created by martin.medina on 03/02/2017.
 */
public class ClIInterfaceTest {
    @Test
    public void callPrintHelp() {
        // TODO add test output
        CLIInterface.printHelp();
    }

    @Test
    public void callTestWithCorrectInputParams() {

        String[] inputParams = { "-listen", "3332", "-db", "java", "-reset", "on", "-rpc", "4444" };
        Map<String, String> result = CLIInterface.call(inputParams);

        Assert.assertEquals(result.get(SystemProperties.PROPERTY_DB_DIR), "java");
        Assert.assertEquals(result.get(SystemProperties.PROPERTY_LISTEN_PORT), "3332");
        Assert.assertEquals(result.get(SystemProperties.PROPERTY_DB_RESET), "true");
        Assert.assertEquals(result.get(SystemProperties.PROPERTY_RPC_PORT), "4444");
        Assert.assertEquals(result.get(SystemProperties.PROPERTY_RPC_ENABLED), "true");
    }

    @Test
    public void changeAndRestoreRpcCorsConfig() {
        String[] inputParams = { "-rpccors", "*.ethereum.io" };
        Map<String, String> result = CLIInterface.call(inputParams);
        Assert.assertEquals(result.get(SystemProperties.PROPERTY_RPC_CORS), "*.ethereum.io");
        String[] restoreParams = { "-rpccors", CorsConfigurationTest.EXPECTED_CORS_CONFIG };
        result = CLIInterface.call(restoreParams);
        Assert.assertEquals(result.get(SystemProperties.PROPERTY_RPC_CORS), CorsConfigurationTest.EXPECTED_CORS_CONFIG  );
    }

    @Test
    public void callTestWrongPortFormat() {

        String[] inputParams = { "-rpc", "-4444" };
        Map<String, String> result = CLIInterface.call(inputParams);

        Assert.assertEquals(result.get(SystemProperties.PROPERTY_RPC_ENABLED), "true");
    }

    @Test
    public void callTestResetOff() {

        String[] inputParams = { "-reset", "off" };
        Map<String, String> result = CLIInterface.call(inputParams);

        Assert.assertEquals(result.get(SystemProperties.PROPERTY_DB_RESET), "false");
    }

    @Test
    public void callTestResetFalse() {

        String[] inputParams = { "-reset", "false" };
        Map<String, String> result = CLIInterface.call(inputParams);

        Assert.assertEquals(result.get(SystemProperties.PROPERTY_DB_RESET), "false");
    }

    @Test
    public void callTestTestResetNo() {

        String[] inputParams = { "-reset", "no" };
        Map<String, String> result = CLIInterface.call(inputParams);

        Assert.assertEquals(result.get(SystemProperties.PROPERTY_DB_RESET), "false");
    }

    @Test
    public void callTestTestResetYes() {

        String[] inputParams = { "-reset", "yes" };
        Map<String, String> result = CLIInterface.call(inputParams);

        Assert.assertEquals(result.get(SystemProperties.PROPERTY_DB_RESET), "true");
    }

    @Test
    public void callTestTestResetTrue(){

        String[] inputParams = { "-reset", "true" };
        Map<String, String> result = CLIInterface.call(inputParams);

        Assert.assertEquals(result.get(SystemProperties.PROPERTY_DB_RESET), "true");
    }
}
