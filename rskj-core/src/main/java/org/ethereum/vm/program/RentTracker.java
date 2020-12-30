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

    // Place holder for rent activation date. Needs to be in the past (long enough) for rent > 0.     
    public static final long RENT_START_DATE = 48L*365*24*3600; // Jan 2018, approx 48 years since 1970 unix time epoch seconds
    // as per RSKIP113 there are cutoffs/thresholds to avoid collecting very small amount of rent
    public static final long modifiedTh = 1_000L; // threshold if a node is modified (smaller cutoff)
    public static final long notModifiedTh = 10_000L; //threshold if a node is not modified (larger cutoff)
    
    //Rent computation for pre-existing nodes
    public static long computeRent(Uint24 valueLength, long lastRentPaidTime, long currentTime, boolean modified){   
        long lrpt = Math.max(lastRentPaidTime, RENT_START_DATE);
        long timeDelta = currentTime - lrpt; //time since rent last paid in seconds
        // compute rent only for nodes with rent timestamps more than 6 months old
        if (timeDelta < GasCost.SIX_MONTHS) {
            return 0L; // don't bother computing    
        }    
        // formula is 1/2^21 (gas/byte/second) * (node valuelength + 136 bytes overhead) * timeDelta (seconds)
        long rd = GasCost.calculateStorageRent(valueLength, timeDelta);        
        // if rent exceeds UN-modifed (high) threshold, does not matter if node was modified or not            
        if (rd > notModifiedTh){
            return rd;
        } else { 
            // IF node was modified, check lower threshold  
            if (modified && rd > modifiedTh){
                return rd;
            } else {
                return 0L; // not worth collecting rent for this node at this time
            }            
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
             //trie nodes created (so far) in this transaction (6 months advace rent)
            nodeTrackingMap = progRes.getCreatedNodes();
        } else{
             //pre-existing trie nodes for which rent already "collected" in this transaction
            nodeTrackingMap = progRes.getAccessedNodes();
        }

        // This documentation may be repeated in RentTracker Class and ProgramResult Class
        //Set of ALL trie keys seen so far in this transaction.
        // - this includes keys for which no rent was collected (amount too small)
        // - it obviously includes keys in the created and accessed nodes Maps 
        // - it does NOT include trie misses! We want those misses to PAY penalty **for each** trie miss!!
        // - this set is passed to child CALLS through new Program()
        // - The set from child CALLs is merged with that of the parents (just like with `createdNodes` and `AccessedNodes` Maps)
        // - thus, we can avoid computing rent for the same node more than once in a transaction (irrespective of call depth) 
        Set<ByteArrayWrapper> keysSeenBefore = progRes.getKeysSeenBefore();//
    
        //logger.info("Trie keys evaluated for rent tracking so far: {}", keysSeenBefore.size());
        //Start with the case of storage cell
        if (storageCellKey != null){
            // get storage cell trie key which depends on both addr and cell key
            ByteArrayWrapper storageKey = repository.getStorageNodeKey(addr, storageCellKey);
            if (!keysSeenBefore.contains(storageKey)){
                Uint24 vLen = repository.getStorageValueLength(addr, storageCellKey);
                //check for existence
                if(!newNode && vLen.intValue() <= 0){ //does not exist
                    rd = GasCost.calculateStorageRent(new Uint24(0), GasCost.SIX_MONTHS);//penalty
                    comboRent += rd;
                    logger.warn("Storage Penalty: {} for addr: {} and key: {}", rd, addr, storageCellKey.longValue());
                    return comboRent; //collect penalty and exit
                }
                // compute the rent due
                if (newNode){
                    rd = getSixMonthsRent(vLen);
                } else {
                    logger.info("Check rent for storage key {}", storageKey);
                    long storageLrpt = repository.getStorageLRPTime(addr, storageCellKey);
                    rd = computeRent(vLen, storageLrpt, refTimeStamp, true); //treat as modified (any real TX will change something, nonce, balance)
                }
                keysSeenBefore.add(storageKey); //add this to seen list (but only if node exists)
                //if rent is due now, then add it to the map
                if (rd > 0){
                    comboRent += rd;
                    nodeTrackingMap.put(storageKey, rd); //add seperately for each node 
                }
            } else {logger.info("node already checked");}
            // Don't return.. storage cell implies contract account
            // So, continue and Check rent status for contract account node, code and storage root
        }

        // Accounts, Code, and Storage Root
        ByteArrayWrapper accKey = repository.getAccountNodeKey(addr);
        //logger.info("Tracking rent for account with trie key {}", accKey);
        // if the node is not in seen set, compute and add rent owed to map
        if (!keysSeenBefore.contains(accKey)){
            Uint24 vLen = repository.getAccountNodeValueLength(addr);
            //check for existence
            if(!newNode && vLen.intValue() <= 0){ //does not exist
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
                logger.info("Check rent for account key {}", accKey);
                long accLrpt = repository.getAccountNodeLRPTime(addr);
                rd = computeRent(vLen, accLrpt, refTimeStamp, true); //treat as modified (any real TX will change something, nonce, balance)
            }
            keysSeenBefore.add(accKey); //node exists in trie, add to seen set
            //if rent is due now, then add it to the map
            if (rd > 0){
                comboRent += rd;
                nodeTrackingMap.put(accKey, rd); //add only rent for specific nodes 
            }
        }
        // if this is a contract then add info for storage root and code
        if (repository.isContract(addr)) {//isContract() returns false in some VM tests (cos setupcontract() skipped)     
            // storage root node
            ByteArrayWrapper srKey = repository.getStorageRootKey(addr);
            // if the node is not in seen set, compute and add rent owed to map
            if (!keysSeenBefore.contains(srKey)){
                logger.info("Check rent for storage root key {}", srKey);
                Uint24 srLen = new Uint24(1); // always 1. repository.getStorageRootValueLength(addr);
                // compute the rent due
                // No penalty code: if we reached here, then we know this node exists (isContract()).. 
                if (newNode){
                    rd = getSixMonthsRent(srLen);
                } else {
                    long srLrpt = repository.getStorageRootLRPTime(addr);
                    rd = RentTracker.computeRent(srLen, srLrpt, refTimeStamp, false);
                }
                keysSeenBefore.add(srKey);
                //if rent is due now, then add it to the map
                if (rd > 0){
                    comboRent += rd;
                    nodeTrackingMap.put(srKey, rd); //add only rent for specific nodes 
                }
            }
            // now the code containing node
            ByteArrayWrapper cKey = repository.getCodeNodeKey(addr);
            // if the node is not in seen set, compute and add rent owed to map
            if (!keysSeenBefore.contains(cKey)){
                Uint24 cLen = new Uint24(repository.getCodeLength(addr)); //WARN: codeLen is int NOT uint24() by default! 
                if(cLen.intValue() <= 0){ // code CAN be empty.. so no penalty?
                    logger.warn("Empty code penalty for addr: {} ",addr);
                    rd = GasCost.calculateStorageRent(new Uint24(0), GasCost.SIX_MONTHS);//penalty
                    comboRent += rd;
                    return comboRent; 
                }
                // compute the rent due
                if (newNode){
                    rd = getSixMonthsRent(cLen);
                } else {
                    logger.info("Check rent for code key {}", cKey);
                    long cLrpt = repository.getCodeNodeLRPTime(addr);
                    rd = RentTracker.computeRent(cLen, cLrpt, refTimeStamp, false);
                }
                keysSeenBefore.add(cKey);
                //if rent is due now, then add it to the map
                if (rd > 0){
                    comboRent += rd;
                    nodeTrackingMap.put(cKey, rd); //add only rent for specific nodes 
                }
            }        
        }
        return comboRent;
    }
}
