package org.ethereum.rpc;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.logfilter.BlocksBloomStore;
import co.rsk.metrics.HashRateCalculator;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.net.BlockProcessor;
import co.rsk.rpc.Web3InformationRetriever;
import co.rsk.rpc.modules.debug.DebugModule;
import co.rsk.rpc.modules.eth.AccountOverride;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.rpc.modules.evm.EvmModule;
import co.rsk.rpc.modules.mnr.MnrModule;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.rsk.RskModule;
import co.rsk.rpc.modules.txpool.TxPoolModule;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.util.HexUtils;
import org.ethereum.TestUtils;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.facade.Ethereum;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.net.server.PeerServer;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.parameters.*;
import org.ethereum.util.BuildInfo;
import org.ethereum.util.TransactionFactoryHelper;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class Web3ImplUnitTest {

    private Blockchain blockchain;

    private Web3Impl target;
    private Web3InformationRetriever retriever;
    private EthModule ethModule;

    @BeforeEach
    void setup() {
        blockchain = mock(Blockchain.class);
        Block firstBlock = mock(Block.class);
        when(blockchain.getBestBlock()).thenReturn(firstBlock);
        ethModule = mock(EthModule.class);
        retriever = mock(Web3InformationRetriever.class);
        target = new Web3Impl(
                mock(Ethereum.class),
                blockchain,
                mock(BlockStore.class),
                mock(ReceiptStore.class),
                mock(RskSystemProperties.class),
                mock(MinerClient.class),
                mock(MinerServer.class),
                mock(PersonalModule.class),
                ethModule,
                mock(EvmModule.class),
                mock(TxPoolModule.class),
                mock(MnrModule.class),
                mock(DebugModule.class),
                null,
                mock(RskModule.class),
                mock(ChannelManager.class),
                mock(PeerScoringManager.class),
                mock(PeerServer.class),
                mock(BlockProcessor.class),
                mock(HashRateCalculator.class),
                mock(ConfigCapabilities.class),
                mock(BuildInfo.class),
                mock(BlocksBloomStore.class),
                retriever,
                null,
                new BlockTxSignatureCache(new ReceivedTxSignatureCache()));
    }

    @Test
    void eth_getBalance_stateCannotBeRetrieved() {
        String id = "id";
        String addr = "0x0011223344556677880011223344556677889900";

        when(retriever.getInformationProvider(id))
                .thenThrow(RskJsonRpcRequestException.blockNotFound("Block not found"));
        TestUtils.assertThrows(RskJsonRpcRequestException.class,
                () -> target.eth_getBalance(new HexAddressParam(addr), new BlockRefParam(id)));
    }

    @Test
    void eth_getBalance() {
        String id = "0x00";
        String addr = "0x0011223344556677880011223344556677889900";
        RskAddress expectedAddress = new RskAddress(addr);

        AccountInformationProvider aip = mock(AccountInformationProvider.class);
        when(retriever.getInformationProvider(id)).thenReturn(aip);
        when(aip.getBalance(expectedAddress))
                .thenReturn(new Coin(BigInteger.ONE));

        String result = target.eth_getBalance(new HexAddressParam(addr), new BlockRefParam(id));
        assertEquals("0x1", result);
    }

    @Test
        //validates invokeByBlockRef call
    void eth_getBalanceByBlockRef() {
        String addr = "0x0011223344556677880011223344556677889900";
        Map<String, String> blockRef = new HashMap<String, String>() {
            {
                put("blockHash", "0xc2b835124172db5bd051bb94fa123721eacac43b5cba2499b22c7583a35689b8");
            }
        };
        final Web3Impl spyTarget = spy(target);
        doReturn("0x1").when(spyTarget).invokeByBlockRef(eq(blockRef), any());
        String result = spyTarget.eth_getBalance(new HexAddressParam(addr), new BlockRefParam(blockRef));
        assertEquals("0x1", result);
        verify(spyTarget).invokeByBlockRef(eq(blockRef), any());
    }

    @Test
    void eth_getStorageAt_stateCannotBeRetrieved() {
        String id = "0x00";
        String addr = "0x0011223344556677880011223344556677889900";
        String storageIdx = "0x01";

        HexAddressParam hexAddressParam = new HexAddressParam(addr);
        HexNumberParam hexNumberParam = new HexNumberParam(storageIdx);
        BlockRefParam blockRefParam = new BlockRefParam(id);

        when(retriever.getInformationProvider(id))
                .thenThrow(RskJsonRpcRequestException.blockNotFound("Block not found"));

        TestUtils.assertThrows(RskJsonRpcRequestException.class,
                () -> target.eth_getStorageAt(hexAddressParam, hexNumberParam, blockRefParam));
    }

    @Test
    void eth_getStorageAt() {
        String id = "0x00";
        String addr = "0x0011223344556677880011223344556677889900";
        RskAddress expectedAddress = new RskAddress(addr);
        String storageIdx = "0x01";
        DataWord expectedIdx = DataWord.valueOf(HexUtils.stringHexToByteArray(storageIdx));

        HexAddressParam hexAddressParam = new HexAddressParam(addr);
        HexNumberParam hexNumberParam = new HexNumberParam(storageIdx);
        BlockRefParam blockRefParam = new BlockRefParam(id);

        AccountInformationProvider aip = mock(AccountInformationProvider.class);
        when(retriever.getInformationProvider(id)).thenReturn(aip);
        when(aip.getStorageValue(expectedAddress, expectedIdx))
                .thenReturn(DataWord.ONE);

        String result = target.eth_getStorageAt(hexAddressParam, hexNumberParam, blockRefParam);
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000001",
                result);
    }

    @Test
        //validates invokeByBlockRef call
    void eth_getStorageAtByBlockRef() {
        final String addr = "0x0011223344556677880011223344556677889900";
        final String storageIdx = "0x01";
        Map<String, String> blockRef = new HashMap<String, String>() {
            {
                put("blockHash", "0xc2b835124172db5bd051bb94fa123721eacac43b5cba2499b22c7583a35689b8");
            }
        };
        final Web3Impl spyTarget = spy(target);
        final String expectedData = "0x0000000000000000000000000000000000000000000000000000000000000001";

        HexAddressParam hexAddressParam = new HexAddressParam(addr);
        HexNumberParam hexNumberParam = new HexNumberParam(storageIdx);
        BlockRefParam blockRefParam = new BlockRefParam(blockRef);

        doReturn(expectedData).when(spyTarget).invokeByBlockRef(eq(blockRef), any());
        String result = spyTarget.eth_getStorageAt(hexAddressParam, hexNumberParam, blockRefParam);
        assertEquals(expectedData, result);
        verify(spyTarget).invokeByBlockRef(eq(blockRef), any());
    }


    @Test
    void eth_getStorageAtEmptyCell() {
        String id = "0x00";
        String addr = "0x0011223344556677880011223344556677889900";
        RskAddress expectedAddress = new RskAddress(addr);
        String storageIdx = "0x01";
        DataWord expectedIdx = DataWord.valueOf(HexUtils.stringHexToByteArray(storageIdx));

        HexAddressParam hexAddressParam = new HexAddressParam(addr);
        HexNumberParam hexNumberParam = new HexNumberParam(storageIdx);
        BlockRefParam blockRefParam = new BlockRefParam(id);

        AccountInformationProvider aip = mock(AccountInformationProvider.class);
        when(retriever.getInformationProvider(id)).thenReturn(aip);
        when(aip.getStorageValue(expectedAddress, expectedIdx))
                .thenReturn(null);

        String result = target.eth_getStorageAt(hexAddressParam, hexNumberParam, blockRefParam);
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000000", result);
    }

    @Test
    void rsk_getStorageBytesAt() {
        String id = "0x00";
        String addr = "0x0011223344556677880011223344556677889900";
        RskAddress expectedAddress = new RskAddress(addr);
        String storageIdx = "0x01";
        DataWord expectedIdx = DataWord.valueOf(HexUtils.stringHexToByteArray(storageIdx));

        HexAddressParam hexAddressParam = new HexAddressParam(addr);
        HexNumberParam hexNumberParam = new HexNumberParam(storageIdx);
        BlockRefParam blockRefParam = new BlockRefParam(id);
        byte[] resultBytes = TestUtils.generateBytes("result", 64);

        AccountInformationProvider aip = mock(AccountInformationProvider.class);
        when(retriever.getInformationProvider(id)).thenReturn(aip);
        when(aip.getStorageBytes(expectedAddress, expectedIdx))
                .thenReturn(resultBytes);

        String expectedResult = HexUtils.toUnformattedJsonHex(resultBytes);

        String result = target.rsk_getStorageBytesAt(hexAddressParam, hexNumberParam, blockRefParam);
        assertEquals(expectedResult,
                result);
    }

    @Test
    void rsk_getStorageBytesAtEmptyCell() {
        String id = "0x00";
        String addr = "0x0011223344556677880011223344556677889900";
        RskAddress expectedAddress = new RskAddress(addr);
        String storageIdx = "0x01";
        DataWord expectedIdx = DataWord.valueOf(HexUtils.stringHexToByteArray(storageIdx));

        HexAddressParam hexAddressParam = new HexAddressParam(addr);
        HexNumberParam hexNumberParam = new HexNumberParam(storageIdx);
        BlockRefParam blockRefParam = new BlockRefParam(id);

        AccountInformationProvider aip = mock(AccountInformationProvider.class);
        when(retriever.getInformationProvider(id)).thenReturn(aip);
        when(aip.getStorageValue(expectedAddress, expectedIdx))
                .thenReturn(null);

        String result = target.rsk_getStorageBytesAt(hexAddressParam, hexNumberParam, blockRefParam);
        assertEquals("0x0",
                result);
    }

    @Test
    void eth_getBlockTransactionCountByNumber_blockNotFound() {
        String id = "0x00";

        when(retriever.getTransactions(id)).thenThrow(RskJsonRpcRequestException.blockNotFound("Block not found"));

        TestUtils.assertThrows(RskJsonRpcRequestException.class,
                () -> target.eth_getBlockTransactionCountByNumber(new BlockIdentifierParam(id)));
    }

    @Test
    void eth_getBlockTransactionCountByNumber() {
        String id = "0x00";
        List<Transaction> txs = new LinkedList<>();
        txs.add(mock(Transaction.class));
        txs.add(mock(Transaction.class));

        when(retriever.getTransactions(id)).thenReturn(txs);

        String result = target.eth_getBlockTransactionCountByNumber(new BlockIdentifierParam(id));

        assertEquals("0x2", result);
    }

    @Test
        //validates invokeByBlockRef call
    void eth_getCode() {
        final String addr = "0x0011223344556677880011223344556677889900";
        Map<String, String> blockRef = new HashMap<String, String>() {
            {
                put("blockHash", "0xc2b835124172db5bd051bb94fa123721eacac43b5cba2499b22c7583a35689b8");
            }
        };
        final Web3Impl spyTarget = spy(target);
        final String expectedData = "0x010203";
        doReturn(expectedData).when(spyTarget).invokeByBlockRef(eq(blockRef), any());
        String result = spyTarget.eth_getCode(new HexAddressParam(addr), new BlockRefParam(blockRef));
        assertEquals(expectedData, result);
        verify(spyTarget).invokeByBlockRef(eq(blockRef), any());
    }

    @Test
        //validates invokeByBlockRef call
    void eth_callAtByBlockRef() {

        CallArguments argsForCall = new CallArguments();
        argsForCall.setTo("0x0011223344556677880011223344556677889900");
        argsForCall.setData("ead710c40000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000568656c6c6f000000000000000000000000000000000000000000000000000000");
        Map<String, String> blockRef = new HashMap<String, String>() {
            {
                put("blockHash", "0xc2b835124172db5bd051bb94fa123721eacac43b5cba2499b22c7583a35689b8");
            }
        };
        final Web3Impl spyTarget = spy(target);
        final String expectedData = "0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000568656c6c6f000000000000000000000000000000000000000000000000000000";

        doReturn(expectedData).when(spyTarget).invokeByBlockRef(eq(blockRef), any());
        String result = spyTarget.eth_call(TransactionFactoryHelper.toCallArgumentsParam(argsForCall), new BlockRefParam(blockRef));
        assertEquals(expectedData, result);
        verify(spyTarget).invokeByBlockRef(eq(blockRef), any());
    }

    @Test
        //validates invokeByBlockRef call
    void eth_getBlockTransactionCountByBlockRef() {
        String addr = "0x0011223344556677880011223344556677889900";
        Map<String, String> blockRef = new HashMap<String, String>() {
            {
                put("blockHash", "0xc2b835124172db5bd051bb94fa123721eacac43b5cba2499b22c7583a35689b8");
            }
        };
        final Web3Impl spyTarget = spy(target);
        doReturn("0x1").when(spyTarget).invokeByBlockRef(eq(blockRef), any());
        String result = spyTarget.eth_getTransactionCount(new HexAddressParam(addr), new BlockRefParam(blockRef));
        assertEquals("0x1", result);
        verify(spyTarget).invokeByBlockRef(eq(blockRef), any());
    }

    @Test
    void eth_getUncleCountByBlockHash_blockNotFound() {
        String hash = "0x4A54";
        byte[] bytesHash = HexUtils.stringHexToByteArray(hash);

        when(blockchain.getBlockByHash(bytesHash)).thenReturn(null);

        RskJsonRpcRequestException exception = TestUtils
                .assertThrows(RskJsonRpcRequestException.class, () -> target.eth_getUncleCountByBlockHash(new BlockHashParam(hash)));

        assertEquals(-32602, (int) exception.getCode());
    }

    @Test
    void eth_getUncleCountByBlockHash() {

        String hash = "0x0000000000000000000000000000000000000000000000000000000000004A54";
        byte[] bytesHash = HexUtils.stringHexToByteArray(hash);

        List<BlockHeader> uncles = new LinkedList<>();
        uncles.add(mock(BlockHeader.class));
        uncles.add(mock(BlockHeader.class));

        Block block = mock(Block.class);
        when(block.getUncleList()).thenReturn(uncles);
        when(blockchain.getBlockByHash(bytesHash)).thenReturn(block);

        String result = target.eth_getUncleCountByBlockHash(new BlockHashParam(hash));
        assertEquals("0x2", result);
    }

    @Test
    void eth_getUncleCountByBlockNumber_notFound() {
        String identifier = "0x00";
        when(retriever.getBlock(identifier)).thenReturn(Optional.empty());

        TestUtils.assertThrows(RskJsonRpcRequestException.class,
                () -> target.eth_getUncleCountByBlockNumber(new BlockIdentifierParam(identifier)));
    }

    @Test
    void eth_getUncleCountByBlockNumber() {
        String identifier = "0x00";
        Block block = mock(Block.class);
        List<BlockHeader> uncles = new LinkedList<>();
        uncles.add(mock(BlockHeader.class));
        uncles.add(mock(BlockHeader.class));

        when(block.getUncleList()).thenReturn(uncles);
        when(retriever.getBlock(identifier)).thenReturn(Optional.of(block));

        String result = target.eth_getUncleCountByBlockNumber(new BlockIdentifierParam(identifier));

        assertEquals("0x2", result);
    }

    @Test
    void when_eth_CallWithAccountOverride_ethModule_receives_correct_params() {
        // Given
        CallArgumentsParam callArgumentsParam = getValidCallArgumentsParam();
        BlockRefParam blockRefParam = new BlockRefParam("latest");
        AccountOverrideParam overrideParam = getValidAccountOverrideParam();
        HexAddressParam hexAddressParam = new HexAddressParam("0xaaa4567890123456789012345678901234567890");
        when(ethModule.call(any(), any(), any())).thenReturn("OK");

        ArgumentCaptor<CallArgumentsParam> callArgumentsParamArgumentCaptor = ArgumentCaptor.forClass(CallArgumentsParam.class);
        ArgumentCaptor<BlockIdentifierParam> blockIdentifierParamArgumentCaptor = ArgumentCaptor.forClass(BlockIdentifierParam.class);
        ArgumentCaptor<List<AccountOverride>> accountOverrideParamArgumentCaptor = ArgumentCaptor.forClass(List.class);

        // When
        target.eth_call(callArgumentsParam, blockRefParam, Map.of(hexAddressParam, overrideParam));

        // Then
        verify(ethModule, times(1)).call(callArgumentsParamArgumentCaptor.capture(), blockIdentifierParamArgumentCaptor.capture(), accountOverrideParamArgumentCaptor.capture());

        CallArgumentsParam receivedCallArgs = callArgumentsParamArgumentCaptor.getValue();
        assertEquals(callArgumentsParam, receivedCallArgs);

        BlockIdentifierParam blockIdentifierParam = blockIdentifierParamArgumentCaptor.getValue();
        assertEquals("latest", blockIdentifierParam.getIdentifier());

        List<AccountOverride> receivedOverrides = accountOverrideParamArgumentCaptor.getValue();
        assertEquals(1, receivedOverrides.size());
        AccountOverride receivedOverride = receivedOverrides.get(0);
        assertEquals(hexAddressParam.getAddress(), receivedOverride.getAddress());
        BigInteger expectedBalance = BigInteger.valueOf(HexUtils.jsonHexToLong(overrideParam.getBalance().getHexNumber()));
        assertEquals(expectedBalance, receivedOverride.getBalance());
        assertEquals(HexUtils.jsonHexToLong(overrideParam.getNonce().getHexNumber()), receivedOverride.getNonce());
        assertEquals(overrideParam.getCode().getRawDataBytes(), receivedOverride.getCode());
    }

    private AccountOverrideParam getValidAccountOverrideParam() {
        HexNumberParam balance = new HexNumberParam("0x02");
        HexNumberParam nonce = new HexNumberParam("0x01");
        HexDataParam code = new HexDataParam("0x010203");
        Map<HexDataParam, HexDataParam> state = Map.of(
                new HexDataParam("0x01"), new HexDataParam("0x02")
        );
        return new AccountOverrideParam(balance, nonce, code, state, null, null);
    }

    private CallArgumentsParam getValidCallArgumentsParam() {
        HexAddressParam from = new HexAddressParam("0x0011223344556677880011223344556677889900");
        HexAddressParam to = new HexAddressParam("0x0011223344556677880011223344556677889900");
        HexDataParam data = new HexDataParam("0x010203");
        return new CallArgumentsParam(from, to, null, null, null, null, null, null, data, null, null, null);
    }

}
