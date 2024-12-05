/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package co.rsk.rpc.modules.debug.trace.call;

import co.rsk.rpc.ExecutionBlockRetriever;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.rpc.modules.debug.TraceOptions;
import co.rsk.test.World;
import co.rsk.test.dsl.DslParser;
import co.rsk.test.dsl.WorldDslProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.core.Account;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.ReceiptStoreImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CallTracerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Test
    void retrieveSimpleStorageContractCreationTrace() throws Exception {
        DslParser parser = DslParser.fromResource("dsl/simple_storage.txt");
        ReceiptStore receiptStore = new ReceiptStoreImpl(new HashMapDB());
        World world = new World(receiptStore);
        ExecutionBlockRetriever executionBlockRetriever = Mockito.mock(ExecutionBlockRetriever.class);
        Web3InformationRetriever web3InformationRetriever = new Web3InformationRetriever(world.getTransactionPool(), world.getBlockChain(), world.getRepositoryLocator(), executionBlockRetriever);


        WorldDslProcessor processor = new WorldDslProcessor(world);
        processor.processCommands(parser);

        TransactionReceipt contractTransactionReceipt = world.getTransactionReceiptByName("tx01");

        CallTracer callTracer = new CallTracer(world.getBlockStore(), world.getBlockExecutor(), web3InformationRetriever, receiptStore, world.getBlockChain());

        JsonNode result = callTracer.traceTransaction(contractTransactionReceipt.getTransaction().getHash().toJsonString(), new TraceOptions());

        assertNotNull(result);

        TxTraceResult traceResult = objectMapper.treeToValue(result, TxTraceResult.class);

        Account account = world.getAccountByName("acc1");

        assertNotNull(traceResult);
        assertEquals("CREATE", traceResult.getType());
        assertEquals("0x608060405234801561000f575f80fd5b506101438061001d5f395ff3fe608060405234801561000f575f80fd5b5060043610610034575f3560e01c80636057361d146100385780636d4ce63c14610054575b5f80fd5b610052600480360381019061004d91906100ba565b610072565b005b61005c61007b565b60405161006991906100f4565b60405180910390f35b805f8190555050565b5f8054905090565b5f80fd5b5f819050919050565b61009981610087565b81146100a3575f80fd5b50565b5f813590506100b481610090565b92915050565b5f602082840312156100cf576100ce610083565b5b5f6100dc848285016100a6565b91505092915050565b6100ee81610087565b82525050565b5f6020820190506101075f8301846100e5565b9291505056fea2646970667358221220271ba6597ab51821beed677d25c76e319892db25c1f66b3dc76e547fdc1fd0e164736f6c63430008140033", traceResult.getInput());
        assertEquals(account.getAddress().toJsonString(), traceResult.getFrom());

    }

}