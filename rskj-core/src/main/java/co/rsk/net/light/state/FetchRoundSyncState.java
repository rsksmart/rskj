package co.rsk.net.light.state;

import co.rsk.crypto.Keccak256;
import co.rsk.net.light.LightPeer;
import co.rsk.net.light.LightSyncProcessor;
import org.ethereum.core.BlockHeader;

import java.util.*;

public class FetchRoundSyncState implements LightSyncState {
    private final LightPeer lightPeer;
    private final LightSyncProcessor lightSyncProcessor;
    public final List<BlockHeader> downloadedHeaders;
    private final Deque<SubchainRequest> pendingRequests;
    private SubchainRequest subchainRequest;

    public FetchRoundSyncState(LightPeer lightPeer, List<BlockHeader> sparseHeaders, long targetNumber, LightSyncProcessor lightSyncProcessor) {
        this.lightPeer = lightPeer;
        this.lightSyncProcessor = lightSyncProcessor;
        this.downloadedHeaders = new ArrayList<>();
        this.pendingRequests = new ArrayDeque<>();
        createAllSubchainRequests(sparseHeaders);
    }

    @Override
    public void sync() {
        tryToSendNextSubchainRequest(lightPeer);
    }

    private void tryToSendNextSubchainRequest(LightPeer lightPeer) {
        if (pendingRequests.isEmpty()) {
            return;
        }
        this.subchainRequest = pendingRequests.pop();
        lightSyncProcessor.sendBlockHeadersByHashMessage(lightPeer, subchainRequest.getStartBlockHash().getBytes(), subchainRequest.getMaxAmountOfHeaders(), 0, true);
    }

    @Override
    public void newBlockHeaders(LightPeer lightPeer, List<BlockHeader> blockHeaders) {
        int maxAmountOfHeaders = subchainRequest.getMaxAmountOfHeaders();
        Keccak256 startBlockHash = subchainRequest.getStartBlockHash();

        if (!lightSyncProcessor.isCorrect(blockHeaders, maxAmountOfHeaders, startBlockHash.getBytes(), 0, true)) {
            return;
        }

        Keccak256 parentHash = null;

        for (BlockHeader bh : blockHeaders) {
            if (parentHash != null) {
                final Keccak256 hash = bh.getHash();
                if (!parentHash.equals(hash)) {
                    lightSyncProcessor.badConnected();
                    return;
                }
            }
            parentHash = bh.getParentHash();
            maxAmountOfHeaders--;
            startBlockHash = parentHash;
            downloadedHeaders.add(bh);
        }

        if (maxAmountOfHeaders == 0) {
            if (parentHash != null && !parentHash.equals(subchainRequest.getSubchainParent())) {
                lightSyncProcessor.badSubchain();
                return;
            }
            lightSyncProcessor.addDownloadedHeaders(downloadedHeaders);
        } else {
            pendingRequests.push(new SubchainRequest(maxAmountOfHeaders, startBlockHash, parentHash));
        }

        tryToSendNextSubchainRequest(lightPeer);

        //TODO: Check what to do whenever the sync ends.
        lightSyncProcessor.endSync();
    }

    private void createAllSubchainRequests(List<BlockHeader> sparseHeaders) {
        for (int i = 0; i < sparseHeaders.size()-1; i++) {
            BlockHeader high = sparseHeaders.get(i+1);
            BlockHeader low = sparseHeaders.get(i);
            final Keccak256 startBlockHash = high.getParentHash();
            pendingRequests.push(new SubchainRequest(Math.toIntExact(high.getNumber() - low.getNumber() - 1),
                    startBlockHash, low.getHash()));
        }
    }

    private static class SubchainRequest {
        private final int maxAmountOfHeaders;
        private final Keccak256 startBlockHash;
        private final Keccak256 subchainParent;

        public SubchainRequest(int maxAmountOfHeaders, Keccak256 startBlockHash, Keccak256 subchainParent) {
            this.maxAmountOfHeaders = maxAmountOfHeaders;
            this.startBlockHash = startBlockHash;
            this.subchainParent = subchainParent;
        }

        public int getMaxAmountOfHeaders() {
            return maxAmountOfHeaders;
        }

        public Keccak256 getStartBlockHash() {
            return startBlockHash;
        }

        public Keccak256 getSubchainParent() {
            return subchainParent;
        }
    }
}
