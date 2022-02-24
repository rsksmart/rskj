/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package co.rsk.rpc.modules.eth.subscribe;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.rpc.JacksonBasedRpcSerializer;
import co.rsk.rpc.JsonRpcSerializer;
import org.ethereum.core.Block;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class EthSubscriptionNotificationTest {
    private static final Block TEST_BLOCK = new BlockGenerator().createBlock(12, 0);
    private static final String TEST_BLOCK_RESULT_JSON = "{\"difficulty\":\"0x20000\",\"extraData\":\"0x\",\"gasLimit\":\"0x2fefd8\",\"gasUsed\":\"0x0\",\"logsBloom\":\"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000\",\"miner\":\"0xe94aef644e428941ee0a3741f28d80255fddba7f\",\"number\":\"0xc\",\"parentHash\":\"0xbe5de0c9c661653c979ec457f610444dcd0048007e683b2d04ce05729af56280\",\"receiptsRoot\":\"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421\",\"sha3Uncles\":\"0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347\",\"stateRoot\":\"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421\",\"timestamp\":\"0x1\",\"transactionsRoot\":\"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421\",\"hash\":\"0xae2699897284d434f3139376fbc606aabf2eade4b2cd1e551d37395f0b095dca\"}";
    private static final Block TEST_BLOCK_2 = new BlockGenerator().createBlock(12, 0, 1000000L);
    private static final String TEST_BLOCK_RESULT_JSON_2 = "{\"difficulty\":\"0x20000\",\"extraData\":\"0x\",\"gasLimit\":\"0xf4240\",\"gasUsed\":\"0x0\",\"logsBloom\":\"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000\",\"miner\":\"0xe94aef644e428941ee0a3741f28d80255fddba7f\",\"number\":\"0xc\",\"parentHash\":\"0x82d804adc43b6382427216a764963f77c612694065f19b3b97c804338c6ceeec\",\"receiptsRoot\":\"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421\",\"sha3Uncles\":\"0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347\",\"stateRoot\":\"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421\",\"timestamp\":\"0x1\",\"transactionsRoot\":\"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421\",\"hash\":\"0x644f8371116ee86c675cc1eadaddfd97c49617b01dd0deb47bc978fcdef86dfc\"}";

    private static final String TEST_SYNC_RESULT_JSON = "{\"syncing\":true,\"status\":{\"startingBlock\":0,\"currentBlock\":1,\"highestBlock\":1000}}";

    private final JsonRpcSerializer serializer = new JacksonBasedRpcSerializer();

    @Test
    public void basicRequestBlockHeader() throws IOException {
        SubscriptionId subscription = new SubscriptionId("0x7392");
        EthSubscriptionNotification<BlockHeaderNotification> notification = new EthSubscriptionNotification<>(
                new EthSubscriptionParams<>(
                        subscription,
                        new BlockHeaderNotification(TEST_BLOCK)
                )
        );

        String expected = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_subscription\",\"params\":{\"subscription\":\"0x7392\",\"result\":" + TEST_BLOCK_RESULT_JSON + "}}";
        assertThat(serializer.serializeMessage(notification), is(expected));
    }

    @Test
    public void gasLimitWithoutLeadingZeros() throws IOException {
        SubscriptionId subscription = new SubscriptionId("0x7393");
        EthSubscriptionNotification notification = new EthSubscriptionNotification(
                new EthSubscriptionParams(
                        subscription,
                        new BlockHeaderNotification(TEST_BLOCK_2)
                )
        );

        String expected = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_subscription\",\"params\":{\"subscription\":\"0x7393\",\"result\":" + TEST_BLOCK_RESULT_JSON_2 + "}}";
        assertThat(serializer.serializeMessage(notification), is(expected));
    }

    @Test
    public void basicRequestSync() throws IOException {
        SubscriptionId subscription = new SubscriptionId("0x7392");
        SyncStatusNotification syncStatusNotification = new SyncStatusNotification(0L, 1L, 1000L);
        EthSubscriptionNotification<SyncNotification> notification = new EthSubscriptionNotification<>(
                new EthSubscriptionParams<>(
                        subscription,
                        new SyncNotification(true, syncStatusNotification)
                )
        );

        String expected = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_subscription\",\"params\":{\"subscription\":\"0x7392\",\"result\":" + TEST_SYNC_RESULT_JSON + "}}";
        assertThat(serializer.serializeMessage(notification), is(expected));
    }

    @Test
    public void booleanRequestSync() throws IOException {
        SubscriptionId subscription = new SubscriptionId("0x7392");
        EthSubscriptionNotification<Boolean> notification = new EthSubscriptionNotification<>(
                new EthSubscriptionParams<>(
                        subscription,
                        false
                )
        );

        String expected = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_subscription\",\"params\":{\"subscription\":\"0x7392\",\"result\":false}}";
        assertThat(serializer.serializeMessage(notification), is(expected));
    }
}
