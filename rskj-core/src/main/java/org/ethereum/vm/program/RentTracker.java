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

import co.rsk.trie.Trie;
import co.rsk.core.RskAddress;
import co.rsk.core.types.ints.Uint24;

import org.ethereum.vm.GasCost;
import org.ethereum.vm.DataWord;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.core.Repository;
//import org.ethereum.db.MutableRepository;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author s mishra, Dec 2020
 * Class to keep track of storage rent for trie nodes created, accessed or 
 * modified during transaction execution (see Program and TransactionExecutor Classes )
*/
public class RentTracker {
    private static final Logger logger = LoggerFactory.getLogger("rentTracker");

    // current place holder for rent system. Needs to be in the past for computations.     
    public static final long RENT_START_DATE = 48L*365*24*3600; // Jan 2018, approx 48 years since 1970 unix time epoch seconds
    // as per RSKIP113 there are cutoffs/thresholds to avoid collecting very small amount of rent
    public static final long modifiedTh = 1_000L; // threshold if a node is modified (smaller cutoff)
    public static final long notModifiedTh = 10_000L; //threshold if a node is not modified (larger cutoff)
    
    /**logic for rent computation follows RSKIP113 for pre-existing nodes.
     * There are separate thresholds for nodes that are modified (account state, SSTORE) 
     * or not modified (e.g. code) by the transaction  
    */
    public static long computeRent(Uint24 valueLength, long lastRentPaidTime, long currentTime, boolean modified){
        if (valueLength != null){ // null is not possible (node gets deleted, 0 length is fine, empty code)
            /** #mish  what if lrpTime was never set? (node version 1, or orchid)
             * those will be initialized to -1 (trie::put(k,v) -> trie::putWithRent(k,v,-1))
             * but we should not be in that situation with node version 2
             * if version 1 nodes are modified, i.e. putWithRent(k,v,newtimestamp), version will get updated to 2
             */
            long lrpt = Math.max(lastRentPaidTime, RENT_START_DATE);
            long timeDelta = currentTime - lrpt; //time since rent last paid
            long rd = 0; //initialize rent due to 0
            // compute rent due but only for nodes with past due rent
            if (timeDelta > 0) {
                 // formula is 1/2^21 (gas/byte/second) * (node valuelength + 136 bytes overhead) * timeDelta (seconds)
                rd = GasCost.calculateStorageRent(valueLength, timeDelta);
            } // no need for else, rd initialized to 0
            
            // if rent due exceeds high threshold, does not matter if the node is modified or not            
            if (rd > notModifiedTh){
                return rd;
            } else {
                /* rent due is less than high threshold. 
                 * Check if amount due exceeds lower threshold but only if the node is marked modified
                 */ 
                if (modified && rd > modifiedTh){
                return rd;
                } else {
                    // not worth collecting rent for this node at this time
                    return 0L;
                }            
            }
        } else {
            return 0L;
        }
    }

    //For new trie ndodes, compute 6 months rent, for the node's valuelength
    public static long getSixMonthsRent(Uint24 valueLength){
        if (valueLength != null){
            return GasCost.calculateStorageRent(valueLength, GasCost.SIX_MONTHS);
        } else {
            return 0L;
        }        
    }
 
    /**
     * Computing and tracking rent updates for Trie nodes (value-containing nodes only)
     * Arguments to be passed
     * @param addr RSK address that a node is associated with (EOA or contract)
     * @param storageKey only if the node is a storage cell, then the key used for storage (different from trie key)
     * @param repository
     * @param progRes programResult that holds the maps of nodes whose rent will be collected at end of TX
     * The repostitory and programResult will be different at each call depth. The maps from different depths 
     * (program Results) are merged together to avoid collecting rent for a node more than once.
     * @param newNode "true" indicates if the Trie node is just created. Charge 6 months rent in advance with advanced
     * timestamp. "false" indicates the node existed in the trie (prior to TX execution). In this case, outstanding rent
     * is to be collected provided it exceeds the minimum thresholds.
     * @param refTimeStamp the unix timestamp epoch seconds to use for rent paid time stamp
     * @return rent due for this node. This is used to increment "rentGasUsed" in ProgramResult
     * Previous rent implementation uses "accessedNodeAdder()", "createdNodeAdder()" type methods.. lot repetitive 
     * boilerplate code in TransactionExecutor and Program. The following implementation cleans that up and place the logic 
     * here in a single place for all types of node tracking 
     * Todo: remasc TX should skip rent tracking.
     * 
     */     
    public static long nodeRentTracker(RskAddress addr, DataWord storageCellKey, Repository repository, ProgramResult progRes,
             boolean newNode, long refTimeStamp){
        //logger.info("Tracking rent for address {}", addr);   
        // set things up
        long rd = 0; // rent due indiv node
        long comboRent = 0; // rent due account + code + storage root   

        //Tracker map <trieKey, rentCollected>
        Map<ByteArrayWrapper, Long> nodeTrackingMap;
        if (newNode){
            nodeTrackingMap = progRes.getCreatedNodes(); //for newly created trei nodes
        } else{
            nodeTrackingMap = progRes.getAccessedNodes(); //for pre-existing trie nodes
        }
        //logger.info("Rent tracking map size is {}", nodeTrackingMap.size());
        //Start with the case of storage cell
        if (storageCellKey != null){
            // get storage cell trie key which depends on both addr and cell key
            ByteArrayWrapper storageKey = repository.getStorageNodeKey(addr, storageCellKey);
            if (!nodeTrackingMap.containsKey(storageKey)){
                Uint24 vLen = repository.getStorageValueLength(addr, storageCellKey);
                //check for existence
                if(vLen.intValue() == 0){ //does not exist
                    rd = GasCost.calculateStorageRent(new Uint24(0), GasCost.SIX_MONTHS);//penalty
                    comboRent += rd;
                    logger.warn("Storage Penalty for addr: {} ", addr);
                    return comboRent; //collect penalty and exit
                }
                // compute the rent due
                if (newNode){
                    rd = getSixMonthsRent(vLen);
                } else {
                    long storageLrpt = repository.getStorageLRPTime(addr, storageCellKey);
                    rd = computeRent(vLen, storageLrpt, refTimeStamp, true); //treat as modified (any real TX will change something, nonce, balance)
                }
                //if rent is due now, then add it to the map
                if (rd > 0){
                    comboRent += rd;
                    nodeTrackingMap.put(storageKey, rd); //add seperately for each node 
                }
            }
            return comboRent; //exit after updating tracker map with storage cell
        }

        // Accounts, Code, and Storage Root
        ByteArrayWrapper accKey = repository.getAccountNodeKey(addr);
        //logger.info("Tracking rent for account with trie key {}", accKey);
        // if the node is not in the map, compute and add rent owed to map
        if (!nodeTrackingMap.containsKey(accKey)){
            Uint24 vLen = repository.getAccountNodeValueLength(addr);
            //check for existence
            if(vLen.intValue() == 0){ //does not exist
                rd = GasCost.calculateStorageRent(new Uint24(0), GasCost.SIX_MONTHS);//penalty
                comboRent += rd;
                logger.warn("Account Penalty for addr: {} ",addr);
                return comboRent; //collect the rentGas and exit            
            }
            // compute the rent due
            if (newNode){
                //logger.info("Tracking rent for new node");
                rd = getSixMonthsRent(vLen);
            } else {
                //logger.info("Tracking rent for pre-existing node");
                long accLrpt = repository.getAccountNodeLRPTime(addr);
                rd = computeRent(vLen, accLrpt, refTimeStamp, true); //treat as modified (any real TX will change something, nonce, balance)
            }
            //if rent is due now, then add it to the map
            if (rd > 0){
                comboRent += rd;
                nodeTrackingMap.put(accKey, rd); //add only rent for specific nodes 
            }
        }
        // if this is a contract then add info for storage root and code
        if (repository.isContract(addr)) {
            // code containing node
            ByteArrayWrapper cKey = repository.getCodeNodeKey(addr);
            // if the node is not in the map, compute and add rent owed to map
            if (!nodeTrackingMap.containsKey(cKey)){
                Uint24 cLen = new Uint24(repository.getCodeLength(addr)); //WARN: codeLen is int NOT uint24() by default! 
                // code CAN be empty.. so no penalty?
                if(cLen.intValue() == 0){ //does not exist
                    logger.warn("Code penalty warning (not collected) for addr: {} ",addr);
                }
                // compute the rent due
                if (newNode){
                    rd = getSixMonthsRent(cLen);
                } else {
                    long cLrpt = repository.getCodeNodeLRPTime(addr);
                    rd = RentTracker.computeRent(cLen, cLrpt, refTimeStamp, false);
                }
                //if rent is due now, then add it to the map
                if (rd > 0){
                    comboRent += rd;
                    nodeTrackingMap.put(cKey, rd); //add only rent for specific nodes 
                }
            }
            // storage root node
            ByteArrayWrapper srKey = repository.getStorageRootKey(addr);
            // if the node is not in the map, compute and add rent owed to map
            if (!nodeTrackingMap.containsKey(srKey)){
                Uint24 srLen = repository.getStorageRootValueLength(addr);
                // compute the rent due
                // No penalty code: if we reached here, then we know this node exists (isContract()).. 
                if (newNode){
                    rd = getSixMonthsRent(srLen);
                } else {
                    long srLrpt = repository.getStorageRootLRPTime(addr);
                    rd = RentTracker.computeRent(srLen, srLrpt, refTimeStamp, false);
                }
                //if rent is due now, then add it to the map
                if (rd > 0){
                    comboRent += rd;
                    nodeTrackingMap.put(srKey, rd); //add only rent for specific nodes 
                }
            }
        }
        return comboRent;
    }    
    
}
