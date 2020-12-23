package org.ethereum.core;

import co.rsk.trie.TrieNodeData;
import org.ethereum.vm.GasCost;

public class RentTracker {
    private final long RSK_START_DATE = 48L*365*24*3600; // Jan 2018, approx 48 years since 1970 unix time epoch seconds
    // as per RSKIP113 there are cutoffs to avoid collecting very small amount of rent
    private final long modifiedTh = 1_000L; // threshold if a node is modified (smaller cutoff)
    private final long notModifiedTh = 10_000L; //threshold if a node is not modified (larger cutoff)
    private final long penalization = 5000; // TODO: Move to GasCost
    long currentTime;
    long rentDue;

    public void trackWriteRent(byte[] key,int valueLength) {
        // we do nothing now.
    }

    public long getRentDue() {
        return rentDue;
    }

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
        long timeDelta = currentTime - time;
        long rd = GasCost.calculateStorageRent(valueLength, timeDelta);
        if (valueLength == 0) { // created
            if (rd > notModifiedTh) {
                rentDue += rd;
            }
        }
    }

}
