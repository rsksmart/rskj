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

package co.rsk.net.discovery.upnp;

import com.google.common.collect.ImmutableMap;
import org.bitlet.weupnp.GatewayDevice;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;

import static org.mockito.Mockito.*;

public class UpnpGatewayManagerTest {

    // test data
    private static final int PORT_1 = 5000;
    private static final int PORT_2 = 6000;
    private static final String DESCRIPTION = "Unit test port mapping";
    private static final String EXTERNAL_IP_ADDRESS = "255.255.255.200";
    private static final InetAddress localAddress = Inet4Address.getLoopbackAddress();

    // mock objects
    private GatewayDevice mockGatewayBad;
    private GatewayDevice mockGatewayGood;
    private GatewayDevice mockGatewayGetExternalIpException;
    private GatewayDevice mockGatewayAddPortMappingException;

    @Before
    public void initialize() throws Exception {
        // initialize mocks
        mockGatewayBad = mock(GatewayDevice.class);
        mockGatewayBad = mock(GatewayDevice.class);
        mockGatewayGood = mock(GatewayDevice.class);
        mockGatewayGetExternalIpException = mock(GatewayDevice.class);
        mockGatewayAddPortMappingException = mock(GatewayDevice.class);

        // setup mock stubs
        when(mockGatewayBad.getLocalAddress()).thenReturn(localAddress);
        when(mockGatewayBad.getExternalIPAddress()).thenReturn(null);
        when(mockGatewayBad.addPortMapping(anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(false);
        when(mockGatewayBad.deletePortMapping(anyInt(), anyString())).thenReturn(false);

        when(mockGatewayGood.getLocalAddress()).thenReturn(localAddress);
        when(mockGatewayGood.getExternalIPAddress()).thenReturn(EXTERNAL_IP_ADDRESS);
        when(mockGatewayGood.addPortMapping(anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(mockGatewayGood.deletePortMapping(anyInt(), anyString())).thenReturn(true);

        when(mockGatewayGetExternalIpException.getLocalAddress()).thenReturn(localAddress);
        when(mockGatewayGetExternalIpException.getExternalIPAddress()).thenThrow(Exception.class);

        when(mockGatewayAddPortMappingException.getLocalAddress()).thenReturn(localAddress);
        when(mockGatewayAddPortMappingException.addPortMapping(
                anyInt(),
                anyInt(),
                anyString(),
                anyString(),
                anyString()
        )).thenThrow(Exception.class);
    }

    @Test
    public void testGetExternalIpAddress() {
        UpnpGatewayManager testManager = new UpnpGatewayManager(mockGatewayGood);
        Assert.assertTrue(
                "Expected non-empty response when gateway successfully returns the external IP address.",
                testManager.getExternalIPAddress().isPresent()
        );
    }

    @Test
    public void testGetExternalIpAddressEmpty() {
        UpnpGatewayManager testManager = new UpnpGatewayManager(mockGatewayBad);
        Assert.assertFalse(
                "Expected empty response when gateway fails to return the external IP address.",
                testManager.getExternalIPAddress().isPresent()
        );
    }

    @Test
    public void testGetExternalIpAddressExceptional() {
        UpnpGatewayManager testManager = new UpnpGatewayManager(mockGatewayGetExternalIpException);
        Assert.assertFalse(
                "Expected empty response when gateway throws Exception.",
                testManager.getExternalIPAddress().isPresent()
        );
    }

    @Test
    public void testAddPortMapping() {
        UpnpGatewayManager testManager = new UpnpGatewayManager(mockGatewayGood);
        Assert.assertTrue(
                "Failed to return true when gateway successfully added a port mapping.",
                testManager.addPortMapping(PORT_1, PORT_1, UpnpProtocol.UDP, DESCRIPTION)
        );
    }

    @Test
    public void testAddPortMappingFail() {
        UpnpGatewayManager testManager = new UpnpGatewayManager(mockGatewayBad);
        Assert.assertFalse(
                "Failed to return false for an invalid port mapping.",
                testManager.addPortMapping(PORT_1, PORT_1, UpnpProtocol.UDP, DESCRIPTION)
        );
    }

    @Test
    public void testAddPortMappingException() {
        UpnpGatewayManager testManager = new UpnpGatewayManager(mockGatewayAddPortMappingException);
        Assert.assertFalse(
                "Failed to return false when gateway throws Exception",
                testManager.addPortMapping(PORT_1, PORT_1, UpnpProtocol.TCP, DESCRIPTION)
        );
    }

    @Test
    public void testDeleteAllPortMappings() throws IOException, SAXException {
        UpnpGatewayManager testManager = new UpnpGatewayManager(mockGatewayGood);
        // ordered map
        ImmutableMap<Integer, UpnpProtocol> portMappings = ImmutableMap.of(
                PORT_1, UpnpProtocol.UDP,
                PORT_2, UpnpProtocol.TCP
        );

        // add some ports then remove them all
        portMappings.forEach((port, protocol) -> testManager.addPortMapping(port, port, protocol, DESCRIPTION));
        testManager.deleteAllPortMappings();

        // verify that correct ports were deleted
        ArgumentCaptor<Integer> acExternalPorts = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> acProtocols = ArgumentCaptor.forClass(String.class);
        verify(mockGatewayGood, times(portMappings.size())).deletePortMapping(
                acExternalPorts.capture(),
                acProtocols.capture()
        );

        String errorTemplate = "Wrong port %s removed.";
        Assert.assertArrayEquals(
                String.format(errorTemplate, "numbers"),
                portMappings.keySet().toArray(),
                acExternalPorts.getAllValues().toArray()
        );
        Assert.assertArrayEquals(
                String.format(errorTemplate, "protocols"),
                portMappings.values().stream().map(Object::toString).toArray(),
                acProtocols.getAllValues().toArray()
        );
    }

    @Test
    public void testDeleteAllPortMappingsException() throws IOException, SAXException {
        doThrow(Exception.class).when(mockGatewayGood).deletePortMapping(anyInt(), anyString());

        UpnpGatewayManager testManager = new UpnpGatewayManager(mockGatewayGood);
        testManager.addPortMapping(PORT_1, PORT_1, UpnpProtocol.UDP, DESCRIPTION);
        try {
            testManager.deleteAllPortMappings();
        } catch (Exception e) {
            Assert.fail("Deletion failure should not result in a thrown Exception.");
        }
    }
}
