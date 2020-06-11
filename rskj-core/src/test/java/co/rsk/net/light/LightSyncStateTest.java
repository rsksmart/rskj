package co.rsk.net.light;

import co.rsk.crypto.Keccak256;
import co.rsk.net.light.message.GetBlockHeadersMessage;
import co.rsk.net.light.state.*;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Genesis;
import org.ethereum.db.BlockStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.ethereum.crypto.HashUtil.randomHash;
import static org.junit.Assert.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

public class LightSyncStateTest {

    private LightSyncProcessor lightSyncProcessor;
    private LightPeer lightPeer;
    public static final int MAX_REQUESTED_HEADERS = 192;
    private ProofOfWorkRule powRule;
    private Blockchain blockchain;

    @Before
    public void setUp() {
        powRule = mock(ProofOfWorkRule.class);
        blockchain = mock(Blockchain.class);
        lightSyncProcessor = getLightSyncProcessor(powRule, blockchain);
        lightPeer = mock(LightPeer.class);

    }

    @Test
    public void createLightSyncProcessorAndStateShouldBeIdle() {
        final LightSyncState syncState = lightSyncProcessor.getSyncState();
        assertEquals(syncState.getClass(), IdleSyncState.class);
    }

    @Test
    public void decidingLightSyncStateShouldTransitToSearchCommonAncestorIfBestBlockIsDifferentOfZero() {
        final Block bestBlock = getBlock(1L, new Keccak256(randomHash()));
        final DecidingLightSyncState decidingLightSyncState = new DecidingLightSyncState(lightSyncProcessor, lightPeer, bestBlock);
        decidingLightSyncState.sync();

        assertEquals(lightSyncProcessor.getSyncState().getClass(), CommonAncestorSearchSyncState.class);

    }

    @Test
    public void decidingLightSyncStateShouldNotTransitToSearchCommonAncestorIfBestBlockIsZero() {
        final Block bestBlock = getBlock(0L, new Keccak256(randomHash()));
        final DecidingLightSyncState decidingLightSyncState = new DecidingLightSyncState(lightSyncProcessor, lightPeer, bestBlock);
        decidingLightSyncState.sync();

        assertNotEquals(lightSyncProcessor.getSyncState().getClass(), CommonAncestorSearchSyncState.class);
    }

    @Test
    public void commonAncestorSearchShouldRequestABlockHeaderToSync() {
        final Block bestBlock = getBlock(1L, new Keccak256(randomHash()));
        final CommonAncestorSearchSyncState syncState = new CommonAncestorSearchSyncState(lightSyncProcessor, lightPeer, bestBlock.getHash().getBytes(), bestBlock.getNumber(), blockchain);
        syncState.sync();

        final GetBlockHeadersMessage expectedMsg = new GetBlockHeadersMessage(1, bestBlock.getHash().getBytes(), 1, 0, true);
        ArgumentCaptor<GetBlockHeadersMessage> argument = forClass(GetBlockHeadersMessage.class);
        assertSendSameMessage(expectedMsg, argument);
    }

    @Test
    public void commonAncestorStateReceiveKnownHeadersAndShouldStopProcessing() {
        final Block bestBlock = getBlock(1L, new Keccak256(randomHash()));
        includeBlockInBlockchain(bestBlock);
        final CommonAncestorSearchSyncState syncState = new CommonAncestorSearchSyncState(lightSyncProcessor, lightPeer, bestBlock.getHash().getBytes(), bestBlock.getNumber(), blockchain);
        final List<BlockHeader> bhs = new ArrayList<>();
        bhs.add(bestBlock.getHeader());

        syncState.newBlockHeaders(lightPeer, bhs);
        assertEquals(lightSyncProcessor.getSyncState().getClass(), RoundSyncState.class);
    }

    @Test
    public void commonAncestorStateReceiveNotKnownHeadersAndShouldAskForSuccessors() {
        final Block bestBlock = getBlock(5L, new Keccak256(randomHash()));

        final CommonAncestorSearchSyncState syncState = new CommonAncestorSearchSyncState(lightSyncProcessor, lightPeer, bestBlock.getHash().getBytes(), bestBlock.getNumber(), blockchain);
        final List<BlockHeader> bhs = new ArrayList<>();
        bhs.add(bestBlock.getHeader());

        long newStartNumber = bestBlock.getNumber() - bhs.size();
        final Block newStartBlock = getBlock(newStartNumber, new Keccak256(randomHash()));
        includeBlockInBlockchain(newStartBlock);
        syncState.newBlockHeaders(lightPeer, bhs);

        final GetBlockHeadersMessage expectedMsg = new GetBlockHeadersMessage(1, newStartBlock.getHash().getBytes(), 4, 0, true);
        ArgumentCaptor<GetBlockHeadersMessage> argument = forClass(GetBlockHeadersMessage.class);
        assertSendSameMessage(expectedMsg, argument);
    }

    @Test
    public void startNumberIsOverMaxRequestedHeadersAndShouldRequestMaxQuantity() {
        final Block bestBlock = getBlock(200L, new Keccak256(randomHash()));
        final CommonAncestorSearchSyncState syncState = new CommonAncestorSearchSyncState(lightSyncProcessor, lightPeer, bestBlock.getHash().getBytes(), bestBlock.getNumber(), blockchain);
        syncState.sync();

        final GetBlockHeadersMessage expectedMsg = new GetBlockHeadersMessage(1, bestBlock.getHash().getBytes(), MAX_REQUESTED_HEADERS, 0, true);
        ArgumentCaptor<GetBlockHeadersMessage> argument = forClass(GetBlockHeadersMessage.class);
        assertSendSameMessage(expectedMsg, argument);
    }

    @Test
    public void anEntireProcessSinceLightSyncProcessorStartsUntilItFindsACommonBlock() {
        final int initialNumber = 195;
        final Block bestBlock = getBlock(initialNumber, new Keccak256(randomHash()));
        final int firstRequestId = 1;

        //Send BlockHeadersRequest
        lightSyncProcessor.startSync(lightPeer, bestBlock);
        final GetBlockHeadersMessage expectedMsg1 = new GetBlockHeadersMessage(firstRequestId, bestBlock.getHash().getBytes(), MAX_REQUESTED_HEADERS, 0, true);


        //This should be the peer's response
        final List<BlockHeader> firstBlockHeaderList = getBlockHeaders(initialNumber, MAX_REQUESTED_HEADERS);

        //Process received block's headers and request for the rest
        final int secondRequestId = 2;
        final int newBlockNumber = initialNumber - MAX_REQUESTED_HEADERS;
        final Block newBlock = getBlock(newBlockNumber, new Keccak256(randomHash()));
        includeBlockInBlockchain(newBlock);
        when(powRule.isValid(newBlock.getHeader())).thenReturn(true);

        //Process received block's headers
        lightSyncProcessor.processBlockHeadersMessage(firstRequestId, firstBlockHeaderList, lightPeer);
        GetBlockHeadersMessage expectedMsg2 = new GetBlockHeadersMessage(secondRequestId, newBlock.getHash().getBytes(), newBlockNumber, 0, true);

        final List<BlockHeader> secondBlockHeaderList = getBlockHeaders(newBlockNumber, newBlockNumber-2);
        secondBlockHeaderList.add(newBlock.getHeader());
        lightSyncProcessor.processBlockHeadersMessage(secondRequestId, secondBlockHeaderList, lightPeer);

        assertEquals(lightSyncProcessor.getSyncState().getClass(), RoundSyncState.class);

        assertMessagesWereSent(expectedMsg1, expectedMsg2);
    }

    private void assertMessagesWereSent(GetBlockHeadersMessage expectedMsg1, GetBlockHeadersMessage expectedMsg2) {
        final ArgumentCaptor<GetBlockHeadersMessage> argument = forClass(GetBlockHeadersMessage.class);
        final List<GetBlockHeadersMessage> arguments = argument.getAllValues();
        verify(lightPeer, times(2)).sendMessage(argument.capture());
        assertArrayEquals(expectedMsg1.getEncoded(), arguments.get(0).getEncoded());
        assertArrayEquals(expectedMsg2.getEncoded(), arguments.get(1).getEncoded());
    }

    private List<BlockHeader> getBlockHeaders(int initialNumber, int numberOfHeaders) {
        final List<BlockHeader> firstBlockHeaderList = new ArrayList<>();
        for (int i = 0; i < numberOfHeaders; i++) {
            final BlockHeader header = getBlock(initialNumber - 1 - i, new Keccak256(randomHash())).getHeader();
            when(powRule.isValid(header)).thenReturn(true);
            firstBlockHeaderList.add(header);
        }
        return firstBlockHeaderList;
    }

    private void includeBlockInBlockchain(Block block) {
        when(blockchain.getBlockByNumber(block.getNumber())).thenReturn(block);
        when(blockchain.getBlockByHash(block.getHash().getBytes())).thenReturn(block);
    }

    private void assertSendSameMessage(GetBlockHeadersMessage expectedMsg, ArgumentCaptor<GetBlockHeadersMessage> argument) {
        verify(lightPeer).sendMessage(argument.capture());
        assertArrayEquals(expectedMsg.getEncoded(), argument.getValue().getEncoded());
    }

    private Block getBlock(long number, Keccak256 hash) {
        final BlockHeader blockHeader = mock(BlockHeader.class);
        final Block block = mock(Block.class);
        when(blockHeader.getHash()).thenReturn(hash);
        when(blockHeader.getNumber()).thenReturn(number);
        when(block.getNumber()).thenReturn(number);
        when(block.getHash()).thenReturn(hash);
        when(block.getHeader()).thenReturn(blockHeader);
        return block;
    }

    private LightSyncProcessor getLightSyncProcessor(ProofOfWorkRule powRule, Blockchain blockchain) {
        LightPeersInformation lightPeersInformation = new LightPeersInformation();
        Genesis genesis = mock(Genesis.class);
        return new LightSyncProcessor(mock(SystemProperties.class), genesis, mock(BlockStore.class), blockchain, powRule, lightPeersInformation);
    }

}
