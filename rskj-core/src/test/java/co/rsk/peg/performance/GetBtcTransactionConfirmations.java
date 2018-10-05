package co.rsk.peg.performance;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.config.TestSystemProperties;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.RepositoryBlockStore;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import org.ethereum.core.Repository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Ignore
public class GetBtcTransactionConfirmations extends BridgePerformanceTestCase {
    private BtcBlock blockWithTx;
    private int blockWithTxHeight;
    private BtcTransaction txToSearch;
    private PartialMerkleTree pmtOfTx;

    @Test
    public void getBtcTransactionConfirmations() {
        ExecutionStats stats = new ExecutionStats("getBtcTransactionConfirmations");
        //Minimum value
        getBtcTransactionConfirmations_success(10, stats, 0,1,1);
        //One day in BTC Blocks
        //Avarage Btc block from https://www.blockchain.com/charts/n-transactions-per-block
        getBtcTransactionConfirmations_success(300, stats, 144, 750, 3000);
        //Maximum value
        //6000 transactions in a block. This value comes from considering the smallest transactions  giving an output of 10 tx/s
        getBtcTransactionConfirmations_success(10, stats, RepositoryBlockStore.MAX_SIZE_MAP_STORED_BLOCKS, 6000, 6000);

        BridgePerformanceTest.addStats(stats);
    }

    private void getBtcTransactionConfirmations_success(int times, ExecutionStats stats, int confirmations, int  minTransactions, int maxTransactions) {
        BridgeStorageProviderInitializer storageInitializer = generateBlockChainInitializer(
                1000,
                2000,
                confirmations,
                minTransactions,
                maxTransactions

        );

        executeAndAverage(String.format("getBtcTransactionConfirmations-success-confirmations-%d",confirmations), times, getABIEncoder(), storageInitializer, Helper.getZeroValueRandomSenderTxBuilder(), Helper.getRandomHeightProvider(10), stats);

    }



    private ABIEncoder getABIEncoder() {
        return (int executionIndex) ->
                Bridge.GET_BTC_TRANSACTION_CONFIRMATIONS.encode(new Object[]{
                        txToSearch.getHash().toString(),
                        blockWithTx.getHash().toString(),
                        blockWithTxHeight,
                        pmtOfTx.bitcoinSerialize()
                });
    }

    private BridgeStorageProviderInitializer generateBlockChainInitializer(int minBtcBlocks, int maxBtcBlocks, int numberOfConfirmations, int minNumberOfTransactions, int maxNumberOfTransactions) {
        return (BridgeStorageProvider provider, Repository repository, int executionIndex) -> {
            BtcBlockStore btcBlockStore = new RepositoryBlockStore(new TestSystemProperties(), repository, PrecompiledContracts.BRIDGE_ADDR);
            Context btcContext = new Context(networkParameters);
            BtcBlockChain btcBlockChain;
            try {
                btcBlockChain = new BtcBlockChain(btcContext, btcBlockStore);
            } catch (BlockStoreException e) {
                throw new RuntimeException("Error initializing btc blockchain for tests");
            }

            int blocksToGenerate = Helper.randomInRange(minBtcBlocks, maxBtcBlocks);
            BtcBlock lastBlock = Helper.generateAndAddBlocks(btcBlockChain, blocksToGenerate);

            // Sender and amounts
            BtcECKey from = new BtcECKey();
            Address fromAddress = from.toAddress(networkParameters);
            Coin fromAmount = Coin.CENT.multiply(Helper.randomInRange(10, 100));
            Coin lockAmount = fromAmount.divide(Helper.randomInRange(2, 10));
            Coin changeAmount = fromAmount.subtract(lockAmount).subtract(Coin.MILLICOIN); // 1 millicoin fee simulation

            // Whitelisting sender
            provider.getLockWhitelist().put(fromAddress, new OneOffWhiteListEntry(fromAddress, lockAmount));

            // Input tx
            BtcTransaction inputTx = new BtcTransaction(networkParameters);
            inputTx.addOutput(fromAmount, fromAddress);

            // Lock tx that uses the input tx
            txToSearch = new BtcTransaction(networkParameters);
            txToSearch.addInput(inputTx.getOutput(0));
            txToSearch.addOutput(lockAmount, bridgeConstants.getGenesisFederation().getAddress());
            txToSearch.addOutput(changeAmount, fromAddress);

            // Signing the input of the lock tx
            Sha256Hash hashForSig = txToSearch.hashForSignature(0, inputTx.getOutput(0).getScriptPubKey(), BtcTransaction.SigHash.ALL, false);
            Script scriptSig = new Script(Script.createInputScript(from.sign(hashForSig).encodeToDER(), from.getPubKey()));
            txToSearch.getInput(0).setScriptSig(scriptSig);

            int numberOfTransactions = Helper.randomInRange(minNumberOfTransactions, maxNumberOfTransactions);
            List<Sha256Hash> allLeafHashes = new ArrayList<>();
            allLeafHashes.add(txToSearch.getHash());
            for(int i=1; i < numberOfTransactions ; i++) {
                allLeafHashes.add( Sha256Hash.of(BigInteger.valueOf(i).toByteArray()));
            }

            //Set the leaves that are going to be added, in this case all of them
            byte[] bits = new byte[(int) Math.ceil(allLeafHashes.size() / 8.0)];
            for(int i=0; i < numberOfTransactions ; i++) {
                Utils.setBitLE(bits, i);
            }

            pmtOfTx = PartialMerkleTree.buildFromLeaves(networkParameters, bits, allLeafHashes);
            List<Sha256Hash> hashes = new ArrayList<>();
            Sha256Hash merkleRoot = pmtOfTx.getTxnHashAndMerkleRoot(hashes);

            blockWithTx = Helper.generateBtcBlock(lastBlock, Arrays.asList(txToSearch), merkleRoot);
            btcBlockChain.add(blockWithTx);
            blockWithTxHeight = btcBlockChain.getBestChainHeight();

            Helper.generateAndAddBlocks(btcBlockChain, numberOfConfirmations);
        };
    }

}
