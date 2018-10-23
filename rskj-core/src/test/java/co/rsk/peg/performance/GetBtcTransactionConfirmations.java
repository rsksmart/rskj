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
        getBtcTransactionConfirmations_success(10, stats, 0); //Minimum value
        getBtcTransactionConfirmations_success(300, stats, 120); //One day in BTC Blocks
        getBtcTransactionConfirmations_success(10, stats, RepositoryBlockStore.MAX_SIZE_MAP_STORED_BLOCKS); //Maximum value

        BridgePerformanceTest.addStats(stats);
    }

    private void getBtcTransactionConfirmations_success(int times, ExecutionStats stats, int confirmations) {
        BridgeStorageProviderInitializer storageInitializer = generateBlockChainInitializer(
                1000,
                2000,
                confirmations
        );

        executeAndAverage(String.format("getBtcTransactionConfirmations-success-confirmations-%d",confirmations), times, getABIEncoder(), storageInitializer, Helper.getZeroValueRandomSenderTxBuilder(), Helper.getRandomHeightProvider(10), stats);

    }



    private ABIEncoder getABIEncoder() {
        return (int executionIndex) ->
                Bridge.GET_BTC_TRANSACTION_CONFIRMATION.encode(new Object[]{
                        txToSearch.getHash().toString(),
                        blockWithTx.getHash().toString(),
                        blockWithTxHeight,
                        pmtOfTx.bitcoinSerialize()
                });
    }

    private BridgeStorageProviderInitializer generateBlockChainInitializer(int minBtcBlocks, int maxBtcBlocks, int numberOfConfirmations) {
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

            pmtOfTx = PartialMerkleTree.buildFromLeaves(networkParameters, new byte[]{(byte) 0xff}, Arrays.asList(txToSearch.getHash()));
            List<Sha256Hash> hashes = new ArrayList<>();
            Sha256Hash merkleRoot = pmtOfTx.getTxnHashAndMerkleRoot(hashes);

            blockWithTx = Helper.generateBtcBlock(lastBlock, Arrays.asList(txToSearch), merkleRoot);
            btcBlockChain.add(blockWithTx);
            blockWithTxHeight = btcBlockChain.getBestChainHeight();

            Helper.generateAndAddBlocks(btcBlockChain, numberOfConfirmations);
        };
    }

}
