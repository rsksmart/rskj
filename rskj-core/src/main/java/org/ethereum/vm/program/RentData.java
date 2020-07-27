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

package org.ethereum.vm.program;

import org.ethereum.vm.GasCost;

import co.rsk.core.types.ints.Uint24;

import java.util.*;


/**
 * @author smishra, May 2020
 * object to keep track of storage rent for nodes created, 
 * accessed or modified during transaction execution 
 */
public class RentData {
    private Uint24 valueLength;
    private long lastRentPaidTime; // for nodes created within the last 6 months this may still be in the future
    private long rentDue;
    private final long RSK_START_DATE = 48L*365*24*3600; // Jan 2018, approx 48 years since 1970 unix time epoch seconds
    // as per RSKIP113 there are cutoffs to avoid collecting very small amount of rent
    private final long modifiedTh = 1_000L; // threshold if a node is modified (smaller cutoff)
    private final long notModifiedTh = 10_000L; //threshold if a node is not modified (larger cutoff)

    public RentData(Uint24 valueLength, long lastRentPaidTime){
        this.valueLength = valueLength;
        this.lastRentPaidTime = lastRentPaidTime;
    }

    public Uint24 getValueLength(){
        return this.valueLength;
    }

    public long getLRPTime(){
        return this.lastRentPaidTime;
    }

    public void setLRPTime(long newLRPTime){
        this.lastRentPaidTime = newLRPTime;
    }

    public long getRentDue(){
        return this.rentDue;
    }

    /**logic for rent computation follows RSKIP113 for pre-existing nodes.
     * There are separate thresholds for nodes that are modified (account state, SSTORE) 
     * or not modified (e.g. code) by the transaction  
    */
    public void setRentDue(long currentTime, boolean modified){
        if (this.valueLength != null){ // null is not possible (node gets deleted, 0 length is fine, empty code)
            /** #mish  what if lrpTime was never set? then it would be initialized to 0 (1970 in Unix time)
             * Use RSK start date for now as lower bound (change later to date when rent is adopted
             */
            long lrpt = Math.max(this.lastRentPaidTime, RSK_START_DATE);
            long timeDelta = currentTime - lrpt; //time since rent last paid
            long rd = 0; //initialize rent due to 0
            // compute rent due but only for nodes with past due rent
            if (timeDelta > 0) {
                 // formula is 1/2^21 (gas/byte/second) * (node valuelength + 136 bytes overhead) * timeDelta (seconds)
                rd = GasCost.calculateStorageRent(this.valueLength, timeDelta);
            } // no need for else, rd initialized to 0
            
            // if rent due exceeds high threshold, does not matter if the node is modified or not            
            if (rd > notModifiedTh){
                this.rentDue = rd;
            } else {
                /* rent due is less than high threshold. 
                 * Check if amount due exceeds lower threshold but only if the node is marked modified
                 */ 
                if (modified && rd > modifiedTh){
                this.rentDue = rd;
                } else {
                    // not worth collecting rent for this node at this time
                    this.rentDue = 0L;
                }            
            }
        } else {
            this.rentDue = 0L;
        }    
    }

    // compute and set 6 months advance rent (for new trie nodes)
    public void setSixMonthsRent(){
        if (this.valueLength != null){
            this.rentDue = GasCost.calculateStorageRent(this.valueLength, GasCost.SIX_MONTHS);
        } else {
            this.rentDue = 0L;
        }        
    }
}
