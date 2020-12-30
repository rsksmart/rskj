package org.ethereum.core;

import co.rsk.trie.TrieNodeData;
import org.ethereum.vm.GasCost;

public class RentTracker {
    // For testing purposes we can't use the real RSK_START_DATE
    // Blocks are generated in tests having timestamps of 1,2,3, etc.
    public static final long RSK_START_DATE = 48L*365*24*3600; // Jan 2018, approx 48 years since 1970 unix time epoch seconds
    // as per RSKIP113 there are cutoffs to avoid collecting very small amount of rent
    private final long modifiedTh = 1_000L; // threshold if a node is modified (smaller cutoff)
    private final long notModifiedTh = 10_000L; //threshold if a node is not modified (larger cutoff)
    private final long penalization = 5000; // TODO: Move to GasCost
    private long currentTime;
    private long rentDue;

    public RentTracker(long currentTime) {
        this.currentTime = currentTime;
    }
    public void trackCreateRent(byte[] key,int valueLength) {
        // we do nothing now.
    }

    public void trackRewriteRent(TrieNodeData oldNodedata,TrieNodeData newNodedata) {
        // if the nodedata is null, then we must penalize
            trackRewriteRent(
                    oldNodedata.getValueLength(),
                    oldNodedata.getLastRentPaidTime(),
                    newNodedata.getValueLength());
    }
    public void trackRewriteRent(TrieNodeData oldNodedata,int  newValueLength) {
        // if the nodedata is null, then we must penalize
        if (oldNodedata==null)
            trackCreateRent(null,newValueLength); // what to do here?
        else
        trackRewriteRent(
                oldNodedata.getValueLength(),
                oldNodedata.getLastRentPaidTime(),
                newValueLength);
    }

    public void trackRewriteRent(byte[] key,int valueLength) {
        // if the value exists, then we charge depending on a threshold

    }

    public void clearRentDue() {
        rentDue = 0;
    }
    public long getRentDue() {
        return rentDue;
    }

    // Penalization only occurs if there is a trie lookup miss BUT there are
    // no further reads or writes to the same location.
    //
    public void penalizeReadMiss() {
        rentDue +=penalization;
    }

    public void trackReadRent(TrieNodeData nodedata) {
        // if the nodedata is null, then we must penalize
        if (nodedata==null)
            penalizeReadMiss();
        else
            trackReadRent(nodedata.getValueLength(),nodedata.getLastRentPaidTime());
    }
    // The method is called for each READ operation
    // the received values are the values read.
    public void trackReadRent(int valueLength,long time) {
        if (time==0)
            time = RSK_START_DATE;
        if (time==-1) // old node time -1 means new node
            return; // nothing to do
        long timeDelta = currentTime - time;
        long rd = GasCost.calculateStorageRent(valueLength, timeDelta);
        if (valueLength == 0) { // created
            if (rd > notModifiedTh) {
                rentDue += rd;
            }
        }
    }

    public void trackRewriteRent(int oldValueLength,long oldTime,
                                 int newValueLength) {
        if (oldTime==0)
            oldTime = RSK_START_DATE;
        if (oldTime==-1) // new node
            return; // nothing to do

        long timeDelta = currentTime - oldTime;
        long rd = GasCost.calculateStorageRent(oldValueLength, timeDelta);
        if (oldValueLength == 0) { // created
            if (rd > modifiedTh) {
                rentDue += rd;
            }
        }
        // Here we could charge an amount for newValueLength-oldValueLength
    }

}
