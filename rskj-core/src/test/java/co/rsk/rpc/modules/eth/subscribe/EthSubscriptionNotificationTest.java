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
    private static final String TEST_BLOCK_RESULT_JSON = "{\"difficulty\":\"0x020000\",\"extraData\":\"0x00\",\"gasLimit\":\"0x2fefd8\",\"gasUsed\":\"0x0\",\"logsBloom\":\"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000\",\"miner\":\"0xe94aef644e428941ee0a3741f28d80255fddba7f\",\"number\":\"0xc\",\"parentHash\":\"0x043fd5522a031c31280ea9389a74b88b66b9c0f4379cfa4e9e9c4eb7108a5c00\",\"receiptsRoot\":\"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421\",\"sha3Uncles\":\"0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347\",\"stateRoot\":\"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421\",\"timestamp\":\"0x1\",\"transactionsRoot\":\"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421\",\"hash\":\"0x4adcaec74b33e3a4f46a7bf06dab1af37fefc855c7cc66ffaa47362bb5039f5f\"}";

    private JsonRpcSerializer serializer = new JacksonBasedRpcSerializer();

    @Test
    public void basicRequest() throws IOException {
        SubscriptionId subscription = new SubscriptionId("0x7392");
        EthSubscriptionNotification notification = new EthSubscriptionNotification(
                new EthSubscriptionParams(
                        subscription,
                        new BlockHeaderNotification(TEST_BLOCK)
                )
        );

        String expected = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_subscription\",\"params\":{\"subscription\":\"0x7392\",\"result\":" + TEST_BLOCK_RESULT_JSON + "}}";
        assertThat(serializer.serializeMessage(notification), is(expected));
    }
}