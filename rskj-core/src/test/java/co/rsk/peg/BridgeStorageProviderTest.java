/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.peg;

import co.rsk.config.BridgeConstants;
import co.rsk.crypto.Sha3Hash;
import org.apache.commons.lang3.tuple.Pair;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.Wallet;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Repository;
import co.rsk.db.RepositoryImpl;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

/**
 * Created by ajlopez on 6/7/2016.
 */
public class BridgeStorageProviderTest {
    private NetworkParameters networkParameters = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();
    private int transactionOffset;

    @Test
    public void createInstance() throws IOException {
        Repository repository = new RepositoryImpl();
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Map<Sha256Hash, Long> processed = provider.getBtcTxHashesAlreadyProcessed();

        Assert.assertNotNull(processed);
        Assert.assertTrue(processed.isEmpty());

        SortedMap<Sha3Hash, Pair<BtcTransaction, Long>> broadcasting = provider.getRskTxsWaitingForBroadcasting();

        Assert.assertNotNull(broadcasting);
        Assert.assertTrue(broadcasting.isEmpty());

        SortedMap<Sha3Hash, BtcTransaction> confirmations = provider.getRskTxsWaitingForConfirmations();

        Assert.assertNotNull(confirmations);
        Assert.assertTrue(confirmations.isEmpty());

        SortedMap<Sha3Hash, BtcTransaction> signatures = provider.getRskTxsWaitingForSignatures();

        Assert.assertNotNull(signatures);
        Assert.assertTrue(signatures.isEmpty());

        List<UTXO> utxos = provider.getBtcUTXOs();

        Assert.assertNotNull(utxos);
        Assert.assertTrue(utxos.isEmpty());

        Wallet wallet = provider.getWallet();

        Assert.assertNotNull(wallet);
        Assert.assertNotNull(wallet.getCoinSelector());
    }

    @Test
    public void createSaveAndRecreateInstance() throws IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        provider0.getBtcTxHashesAlreadyProcessed();
        provider0.getRskTxsWaitingForBroadcasting();
        provider0.getRskTxsWaitingForConfirmations();
        provider0.getRskTxsWaitingForSignatures();
        provider0.getBtcUTXOs();
        provider0.save();
        track.commit();

        track = repository.startTracking();

        byte[] contractAddress = Hex.decode(PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertNotNull(repository.getContractDetails(contractAddress));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("btcTxHashesAP".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("rskTxsWaitingFC".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("rskTxsWaitingFS".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("rskTxsWaitingFB".getBytes())));
        Assert.assertNotNull(repository.getStorageBytes(contractAddress, new DataWord("btcUTXOs".getBytes())));

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        Map<Sha256Hash, Long> processed = provider.getBtcTxHashesAlreadyProcessed();

        Assert.assertNotNull(processed);
        Assert.assertTrue(processed.isEmpty());

        SortedMap<Sha3Hash, Pair<BtcTransaction, Long>> broadcasting = provider.getRskTxsWaitingForBroadcasting();

        Assert.assertNotNull(broadcasting);
        Assert.assertTrue(broadcasting.isEmpty());

        SortedMap<Sha3Hash, BtcTransaction> confirmations = provider.getRskTxsWaitingForConfirmations();

        Assert.assertNotNull(confirmations);
        Assert.assertTrue(confirmations.isEmpty());

        SortedMap<Sha3Hash, BtcTransaction> signatures = provider.getRskTxsWaitingForSignatures();

        Assert.assertNotNull(signatures);
        Assert.assertTrue(signatures.isEmpty());

        List<UTXO> utxos = provider.getBtcUTXOs();

        Assert.assertNotNull(utxos);
        Assert.assertTrue(utxos.isEmpty());

        Wallet wallet = provider.getWallet();

        Assert.assertNotNull(wallet);
        Assert.assertNotNull(wallet.getCoinSelector());
    }

    @Test
    public void createSaveAndRecreateInstanceWithProcessedHashes() throws IOException {
        Sha256Hash hash1 = PegTestUtils.createHash();
        Sha256Hash hash2 = PegTestUtils.createHash();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        provider0.getBtcTxHashesAlreadyProcessed().put(hash1, 1L);
        provider0.getBtcTxHashesAlreadyProcessed().put(hash2, 1L);
        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        Map<Sha256Hash, Long> processed = provider.getBtcTxHashesAlreadyProcessed();
        Set<Sha256Hash> processedHashes = processed.keySet();

        Assert.assertTrue(processedHashes.contains(hash1));
        Assert.assertTrue(processedHashes.contains(hash2));
    }

    @Test
    public void createSaveAndRecreateInstanceWithTxsWaitingForBroadcasting() throws IOException {
        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();
        Sha3Hash hash1 = PegTestUtils.createHash3();
        Sha3Hash hash2 = PegTestUtils.createHash3();
        Sha3Hash hash3 = PegTestUtils.createHash3();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        provider0.getRskTxsWaitingForBroadcasting().put(hash1, Pair.of(tx1, new Long(1)));
        provider0.getRskTxsWaitingForBroadcasting().put(hash2, Pair.of(tx2, new Long(2)));
        provider0.getRskTxsWaitingForBroadcasting().put(hash3, Pair.of(tx3, new Long(3)));

        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        SortedMap<Sha3Hash, Pair<BtcTransaction, Long>> broadcasting = provider.getRskTxsWaitingForBroadcasting();

        Assert.assertNotNull(broadcasting);

        Assert.assertTrue(broadcasting.containsKey(hash1));
        Assert.assertTrue(broadcasting.containsKey(hash2));
        Assert.assertTrue(broadcasting.containsKey(hash3));

        Assert.assertEquals(tx1.getHash(), broadcasting.get(hash1).getLeft().getHash());
        Assert.assertEquals(tx2.getHash(), broadcasting.get(hash2).getLeft().getHash());
        Assert.assertEquals(tx3.getHash(), broadcasting.get(hash3).getLeft().getHash());

        Assert.assertEquals(1, broadcasting.get(hash1).getRight().intValue());
        Assert.assertEquals(2, broadcasting.get(hash2).getRight().intValue());
        Assert.assertEquals(3, broadcasting.get(hash3).getRight().intValue());
    }

    @Test
    public void createSaveAndRecreateInstanceWithTxsWaitingForConfirmations() throws IOException {
        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();
        Sha3Hash hash1 = PegTestUtils.createHash3();
        Sha3Hash hash2 = PegTestUtils.createHash3();
        Sha3Hash hash3 = PegTestUtils.createHash3();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        provider0.getRskTxsWaitingForConfirmations().put(hash1, tx1);
        provider0.getRskTxsWaitingForConfirmations().put(hash2, tx2);
        provider0.getRskTxsWaitingForConfirmations().put(hash3, tx3);

        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        SortedMap<Sha3Hash, BtcTransaction> confirmations = provider.getRskTxsWaitingForConfirmations();

        Assert.assertNotNull(confirmations);

        Assert.assertTrue(confirmations.containsKey(hash1));
        Assert.assertTrue(confirmations.containsKey(hash2));
        Assert.assertTrue(confirmations.containsKey(hash3));

        Assert.assertEquals(tx1.getHash(), confirmations.get(hash1).getHash());
        Assert.assertEquals(tx2.getHash(), confirmations.get(hash2).getHash());
        Assert.assertEquals(tx3.getHash(), confirmations.get(hash3).getHash());
    }

    @Test
    public void createSaveAndRecreateInstanceWithTxsWaitingForSignatures() throws IOException {
        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();
        Sha3Hash hash1 = PegTestUtils.createHash3();
        Sha3Hash hash2 = PegTestUtils.createHash3();
        Sha3Hash hash3 = PegTestUtils.createHash3();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        provider0.getRskTxsWaitingForSignatures().put(hash1, tx1);
        provider0.getRskTxsWaitingForSignatures().put(hash2, tx2);
        provider0.getRskTxsWaitingForSignatures().put(hash3, tx3);

        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        SortedMap<Sha3Hash, BtcTransaction> signatures = provider.getRskTxsWaitingForSignatures();

        Assert.assertNotNull(signatures);

        Assert.assertTrue(signatures.containsKey(hash1));
        Assert.assertTrue(signatures.containsKey(hash2));
        Assert.assertTrue(signatures.containsKey(hash3));

        Assert.assertEquals(tx1.getHash(), signatures.get(hash1).getHash());
        Assert.assertEquals(tx2.getHash(), signatures.get(hash2).getHash());
        Assert.assertEquals(tx3.getHash(), signatures.get(hash3).getHash());
    }

    @Test
    public void createSaveAndRecreateInstanceWithUTXOS() throws IOException {
        Sha256Hash hash1 = PegTestUtils.createHash();
        Sha256Hash hash2 = PegTestUtils.createHash();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeConstants bridgeConstants = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        provider0.getBtcUTXOs().add(new UTXO(hash1, 1, Coin.COIN, 0, false, ScriptBuilder.createOutputScript(bridgeConstants.getFederationAddress())));
        provider0.getBtcUTXOs().add(new UTXO(hash2, 2, Coin.FIFTY_COINS, 0, false, ScriptBuilder.createOutputScript(bridgeConstants.getFederationAddress())));
        provider0.save();
        track.commit();

        track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        List<UTXO> utxos = provider.getBtcUTXOs();

        Assert.assertTrue(utxos.get(0).getHash().equals(hash1));
        Assert.assertTrue(utxos.get(1).getHash().equals(hash2));
    }

    private BtcTransaction createTransaction() {
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addInput(PegTestUtils.createHash(), transactionOffset++, ScriptBuilder.createInputScript(new TransactionSignature(BigInteger.ONE, BigInteger.TEN)));

        return tx;
    }
}
