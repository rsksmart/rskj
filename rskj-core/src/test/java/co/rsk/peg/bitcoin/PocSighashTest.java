package co.rsk.peg.bitcoin;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.peg.ErpFederation;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationMember;
import co.rsk.peg.P2shErpFederation;
import co.rsk.peg.PegTestUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static co.rsk.peg.FederationMember.BTC_RSK_MST_PUBKEYS_COMPARATOR;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PocSighashTest {
    private static Map<String, String> memo;

    @BeforeEach
    void setup(){
        memo = new HashMap<>();
    }

    private static Stream<Arguments> provideTestArguments() {
        return Stream.of(
            Arguments.of(BridgeMainNetConstants.getInstance().getBtcParams()),
            Arguments.of(BridgeTestNetConstants.getInstance().getBtcParams())
        );
    }

    @ParameterizedTest
    @MethodSource("provideTestArguments")
    void test_each_input_sighash_is_unique(NetworkParameters networkParameters) {
        // Arrange
        int erpFedActivationDelay = 720;

        List<FedSigner> fedMembers = FedSigner.listOf("fed1", "fed2", "fed3", "fed4", "fed5", "fed6", "fed7");
        List<FedSigner> erpFedMembers = FedSigner.listOf("erp-fed-01", "erp-fed-02", "erp-fed-03");

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        P2shErpFederation fed = new P2shErpFederation(
            fedMembers.stream().map(FedSigner::getFed).collect(Collectors.toList()),
            Instant.now(),
            0L,
            networkParameters,
            erpFedMembers.stream().map(FedSigner::getFed).map(FederationMember::getBtcPublicKey).collect(Collectors.toList()),
            erpFedActivationDelay,
            activations
        );

        List<FedUtxo> utxos = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            BtcTransaction peginTx = new BtcTransaction(networkParameters);
            peginTx.addOutput(Coin.valueOf(100_000*(i+1)), fed.getAddress());
            utxos.add(FedUtxo.of(peginTx, 0));
        }

        BtcTransaction txWithOutputToRandomAddress = new BtcTransaction(networkParameters);
        txWithOutputToRandomAddress.addOutput(Coin.valueOf(500_001), PegTestUtils.createRandomP2PKHBtcAddress(networkParameters));
        txWithOutputToRandomAddress.addOutput(Coin.valueOf(500_002), fed.getAddress());
        utxos.add(FedUtxo.of(txWithOutputToRandomAddress, 1));

        BtcTransaction txWithOutputToMultisig = new BtcTransaction(networkParameters);
        txWithOutputToMultisig.addOutput(Coin.valueOf(700_001), PegTestUtils.createRandomP2SHMultisigAddress(networkParameters, 3));
        txWithOutputToMultisig.addOutput(Coin.valueOf(700_002), fed.getAddress());
        utxos.add(FedUtxo.of(txWithOutputToMultisig, 1));

        Address destinationAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Coin totalAmount = utxos.stream().map(fedUtxo -> fedUtxo.btcTransaction.getOutput(fedUtxo.getOutputIdx()).getValue()).reduce(Coin.ZERO, Coin::add);

        // Act
        Set<Sha256Hash> sighashes = new HashSet<>();
        BtcTransaction pegOutTx = spendFromFed(
            networkParameters,
            erpFedActivationDelay,
            fed,
            fedMembers,
            false,
            utxos,
            totalAmount.minus(Coin.valueOf(15_000)),
            destinationAddress,
            sighashes
        );

        // Assert each utxo has a unique sighash by checking the sighashes set has the same size of the utxo list
        Assertions.assertEquals(utxos.size(), sighashes.size());

        // Assert sighashes got before signing the input is the same after the tx is signed
        for (int i = 0; i < pegOutTx.getInputs().size(); i++) {
            Sha256Hash sighashFromSignedTx = pegOutTx.hashForSignature(
                i,
                fed.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            );
            assertTrue(sighashes.contains(sighashFromSignedTx));
        }
    }

    @ParameterizedTest
    @MethodSource("provideTestArguments")
    void test_sighash_is_different_when_tx_is_altered(NetworkParameters networkParameters) {
        // Arrange
        int erpFedActivationDelay = 720;

        List<FedSigner> fedMembers = FedSigner.listOf("fed1", "fed2", "fed3", "fed4", "fed5", "fed6", "fed7");
        List<FedSigner> erpFedMembers = FedSigner.listOf("erp-fed-01", "erp-fed-02", "erp-fed-03");

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        P2shErpFederation fed = new P2shErpFederation(
            fedMembers.stream().map(FedSigner::getFed).collect(Collectors.toList()),
            Instant.now(),
            0L,
            networkParameters,
            erpFedMembers.stream().map(FedSigner::getFed).map(FederationMember::getBtcPublicKey).collect(Collectors.toList()),
            erpFedActivationDelay,
            activations
        );

        List<FedUtxo> utxos = new ArrayList<>();
        BtcTransaction peginTx = new BtcTransaction(networkParameters);
        peginTx.addOutput(Coin.valueOf(100_000), fed.getAddress());
        utxos.add(FedUtxo.of(peginTx, 0));

        Address destinationAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Coin totalAmount = utxos.stream().map(fedUtxo -> fedUtxo.btcTransaction.getOutput(fedUtxo.getOutputIdx()).getValue()).reduce(Coin.ZERO, Coin::add);

        // Act
        Set<Sha256Hash> sighashes = new HashSet<>();
        BtcTransaction pegOutTx = spendFromFed(
            networkParameters,
            erpFedActivationDelay,
            fed,
            fedMembers,
            false,
            utxos,
            totalAmount.minus(Coin.valueOf(15_000)),
            destinationAddress,
            sighashes
        );

        // Assert each utxo has a unique sighash by checking the sighashes set has the same size of the utxo list
        Assertions.assertEquals(utxos.size(), sighashes.size());

        // Assert sighashes got before signing the input is the same after the tx is signed
        for (int i = 0; i < pegOutTx.getInputs().size(); i++) {
            Sha256Hash sighashFromSignedTx = pegOutTx.hashForSignature(
                i,
                fed.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            );
            assertTrue(sighashes.contains(sighashFromSignedTx));
        }


        BtcTransaction peginTx2 = new BtcTransaction(networkParameters);
        peginTx2.addOutput(Coin.valueOf(100_000), fed.getAddress());
        utxos.add(FedUtxo.of(peginTx2, 0));

        Coin alteredTxTotalAmount = utxos.stream().map(fedUtxo -> fedUtxo.btcTransaction.getOutput(fedUtxo.getOutputIdx()).getValue()).reduce(Coin.ZERO, Coin::add);

        Set<Sha256Hash> alteredTxSighashes = new HashSet<>();
        spendFromFed(
            networkParameters,
            erpFedActivationDelay,
            fed,
            fedMembers,
            false,
            utxos,
            alteredTxTotalAmount.minus(Coin.valueOf(15_000)),
            destinationAddress,
            alteredTxSighashes
        );

        // Assert sighashes set size is equal to number of tx's inputs(utxos)
        Assertions.assertEquals(utxos.size(), alteredTxSighashes.size());
        // Assert altered tx sighashes size is equal to previous tx sighashes + 1(new utxo added)
        Assertions.assertEquals(alteredTxSighashes.size(), sighashes.size() + 1);


        // Assert altered tx sighashes are different than sighashes from previous tx
        Iterator<Sha256Hash> sighashesIterator = sighashes.iterator();
        Iterator<Sha256Hash> alteredTxSighashesIterator = alteredTxSighashes.iterator();
        while (sighashesIterator.hasNext()) {
            Sha256Hash alteredTxSighash = alteredTxSighashesIterator.next();
            Sha256Hash sighash = sighashesIterator.next();

            assertNotEquals(alteredTxSighash, sighash);
            assertFalse(alteredTxSighashes.contains(sighash));
        }
    }

    @ParameterizedTest
    @MethodSource("provideTestArguments")
    void test_sighash_is_equal_for_signed_input_and_unsigned_input(NetworkParameters networkParameters) {
        // Arrange
        int erpFedActivationDelay = 720;

        List<FedSigner> fedMembers = FedSigner.listOf("fed1", "fed2", "fed3");
        List<FedSigner> erpFedMembers = FedSigner.listOf("erp-fed-01", "erp-fed-02", "erp-fed-03");

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        P2shErpFederation fed = new P2shErpFederation(
            fedMembers.stream().map(FedSigner::getFed).collect(Collectors.toList()),
            Instant.now(),
            0L,
            networkParameters,
            erpFedMembers.stream().map(FedSigner::getFed).map(FederationMember::getBtcPublicKey).collect(Collectors.toList()),
            erpFedActivationDelay,
            activations
        );

        List<FedUtxo> utxos = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            BtcTransaction peginTx = new BtcTransaction(networkParameters);
            peginTx.addOutput(Coin.valueOf(100_000*(i+1)), fed.getAddress());
            utxos.add(FedUtxo.of(peginTx, 0));
        }

        Coin totalAmount = utxos.stream().map(fedUtxo -> fedUtxo.btcTransaction.getOutput(fedUtxo.getOutputIdx()).getValue()).reduce(Coin.ZERO, Coin::add);
        Address destinationAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);

        // Act
        Set<Sha256Hash> sighashes = new HashSet<>();
        BtcTransaction pegOutTx = spendFromFed(
            networkParameters,
            erpFedActivationDelay,
            fed,
            fedMembers,
            false,
            utxos,
            totalAmount.minus(Coin.valueOf(15_000)),
            destinationAddress,
            sighashes
        );

        // Assert each utxo has a unique sighash by checking the sighashes set has the same size of the utxo list
        Assertions.assertEquals(utxos.size(), sighashes.size());

        removeSignaturesFromTransaction(pegOutTx, fed);
        for (int i = 0; i < pegOutTx.getInputs().size(); i++) {
            Sha256Hash sighashFromUnsignedTx = pegOutTx.hashForSignature(
                i,
                fed.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            );
            assertTrue(sighashes.contains(sighashFromUnsignedTx));
        }
    }

    @Test
    void test_each_input_sighash_is_unique_using_real_tx_testnet() {
        // Arrange
        NetworkParameters networkParameters = BridgeTestNetConstants.getInstance().getBtcParams();
        int erpFedActivationDelay = 720;

        List<FedSigner> fedMembers = FedSigner.listOf("federator1", "federator2", "federator6");
        List<FedSigner> erpFedMembers = FedSigner.listOf("erp-fed-01", "erp-fed-02", "erp-fed-03");

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        P2shErpFederation fed = new P2shErpFederation(
            fedMembers.stream().map(FedSigner::getFed).collect(Collectors.toList()),
            Instant.now(),
            0L,
            networkParameters,
            erpFedMembers.stream().map(FedSigner::getFed).map(FederationMember::getBtcPublicKey).collect(Collectors.toList()),
            erpFedActivationDelay,
            activations
        );

        Address expectedAddress = Address.fromBase58(
            networkParameters,
            "2NEfaGq4tGe6bJUxLEzGFoVyZrSrZtXRzJ7"
        );
        assertEquals(expectedAddress, fed.getAddress());

        String rawFundTx1 = "0200000000010134ed7734da14ecde305347153f70be45021e8e29137205a9899630f56c68d3890100000000fdffffff02891300000000000017a914eaf58ece160a383630667cfc1ccff519ab07c472873c3e020000000000160014f885b26136ad4d61247132271795cc29ae9cec0302473044022063986423838cdd2abc51b150f11a1c399dce53f68f1bd02e4ec793db2fa3485c022007d8909a83b01c57dcee7e55f3989af949756c6b5068948b888622d2e6673ed70121028f117bfbc90d934b73d0d55afe54ee33a288cdde572bd3d006cede67f4c797a2e7262500";
        FedUtxo utxo1 = FedUtxo.of(new BtcTransaction(networkParameters, Hex.decode(rawFundTx1)), 0);

        String rawFundTx2 = "020000000001013a43f0d972f5045b443c9c071bd2254f91d5b14d0b5ab5cdeab014c0ff2cc17f0200000000fdffffff027d6408000000000017a914eaf58ece160a383630667cfc1ccff519ab07c472870555dc0500000000220020617a81e635a62aa61d20fd4be369d285da2750c871016282c05a29181552d52b0400473044022048386d4e0270b68a17d3ea10d8c569c2150cb46416c82b3289f2e0ec1fe0272a02204f077bdc8f71d824a4a8a447708cb974e21c015021b94f8226a3da955024371e0147304402204fda66a5cc3503acd325671216f5dbfe4d329499321af508c4959e89072dc47802204531cb9a52c80ede1cab8ca40324ef18ddfb589a0b76f741764334f6f1e9570f014752210203307a637032952b21371f966838e03a768220f8bb663cbc2aa6d41dc71f6bbf2103fdadd0c267fc7a4890c37c6730ca124d53ad7001ee6d9ad00ed50b52a46d784552aee7262500";
        FedUtxo utxo2 = FedUtxo.of(new BtcTransaction(networkParameters, Hex.decode(rawFundTx2)), 0);

        String rawFundTx3 = "020000000001016a040fa1ec01bac865d4802083a435fef32052091d6d7bd02cc784d00d75e8400100000000fdffffff02916408000000000017a914eaf58ece160a383630667cfc1ccff519ab07c47287beefd305000000002200201ca17d3f37320ac4469faa1119824d98bbec3b1a586174901834d2b842715e7c0400473044022044033baafc0c15c8e2c3128d01f29a7c7c5c6ec790ead8c5be2610efb0e9d581022014b605cb59a5254a189a9fcd35ec94e6f4faae18f7011a41ac7663df33fbfdc50147304402206fc734a5db737ecb37056491881da15c2f8f2ca2bb9e47db7396029b9a0ad5e102206cae6f8766679c666cfa256e886e2816882623167e92fd9e272fd9712aa49d9a0147522102b9bb9ea5c5c0a56421bc7720e81e7f3e5fb9dfcbca946f01d868940efaa5da18210309eb318fe76a6b139d70e4037dd87733ced9e3ed6f851301a88938c7033d473b52ae05272500";
        FedUtxo utxo3 = FedUtxo.of(new BtcTransaction(networkParameters, Hex.decode(rawFundTx3)), 0);

        String rawFundTx4 = "0200000001a223ad7e9d4f5067a8fdddf9e4137af49d5a6dad7ea952533f701061fefd010f020000006a473044022003d999e14be1e2ea15cb16df81e14b30f26c684bcbf6fcbd07975d8648e5f04402207e99a414a0ebf1c3617944610de5ea002439a1ac56e667a60c095184c2419559012102c6bf1e099ec95510a8da3d1c67026cb65a019bad57dc2255dd56a426c629327bfdffffff0300000000000000001b6a1952534b540162db6c4b118d7259c23692b162829e6bd5e4d5b0891300000000000017a914eaf58ece160a383630667cfc1ccff519ab07c4728791200000000000001976a9145952b24450e80668e069b8152a3a38ea7f6ad44c88ace7262500";
        FedUtxo utxo4 = FedUtxo.of(new BtcTransaction(networkParameters, Hex.decode(rawFundTx4)), 1);

        List<FedUtxo> utxos = Arrays.asList(utxo1, utxo2, utxo3, utxo4);

        Address destinationAddress = Address.fromBase58(networkParameters, "2MtYSUzFWEQV62r92bsGX8ewE5Mgpv4xn9M");

        // Act
        Set<Sha256Hash> sighashes = new HashSet<>();
        BtcTransaction pegOutTx = spendFromFed(
            networkParameters,
            erpFedActivationDelay,
            fed,
            fedMembers,
            false,
            utxos,
            Coin.valueOf(1_107_748L),
            destinationAddress,
            sighashes
        );

        // Assert each utxo has a unique sighash by checking the sighashes set has the same size of the utxo list
        Assertions.assertEquals(utxos.size(), sighashes.size());

        String rawTxBroadcastedAndConfirmed = "02000000044928841a8bcded3af365da1b57b98b96e4593893862599c504f883b0c956751600000000fd700100483045022100d05a33bd8d0032ed9ccd5f3ba1fb085166c9155f03d95a79e65ef9cf60b9e4b602201287eedce988adf75d8daadc978eca43f47bc33ec999652008e85a3d3dd47f8001483045022100eb5f917967f772328dfd82bd27a096cac453ca336a51d1782ca6684304dd34d5022055bd25e56ed2702974fb58224481e393fca12c4ff34771dab5f456c8fa76568401004cda64522103462ab7041341dadd996dc12ef0c118ca8ccb546498cbf304f7ffe0f1b12f9a9e210362634ab57dae9cb373a5d536e66a8c4f67468bbcfb063809bab643072d78a1242103c5946b3fbae03a654237da863c9ed534e0878657175b132b8ca630f245df04db53ae6702d002b2755221029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e433012103284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd2103b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b2453ae68ffffffff6a040fa1ec01bac865d4802083a435fef32052091d6d7bd02cc784d00d75e84000000000fd6e010047304402206a00bb7a876ecbacd13c06a3e8ca3c55d022e9f35ad6211c65bc4ab3e2a12f1b022051a1d0861a5e812c6c2ffa1be0467fe953ceff6ecb9fc3eb68843441d5c3d7a501473044022044744cceb33a776928c61c8d01cf3acf58c7159ccd5a69914cb78be2a59d5b2d022020ff71156583ba563e9a4d6a8f0bd463d53a6612e831082e0ac9c1c2d1a6d79101004cda64522103462ab7041341dadd996dc12ef0c118ca8ccb546498cbf304f7ffe0f1b12f9a9e210362634ab57dae9cb373a5d536e66a8c4f67468bbcfb063809bab643072d78a1242103c5946b3fbae03a654237da863c9ed534e0878657175b132b8ca630f245df04db53ae6702d002b2755221029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e433012103284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd2103b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b2453ae68ffffffff114c09191b5b1fe685640c23a2ab95bfec521d49ddde4bb96032471762b441e400000000fd6f0100483045022100979a3c231a8ca8c1aa081dffe6cbe0d7ad5987ce4329757cfd8e0c3bfbc5ba8c02202357deaf74162fefc1a05ddf4c2bb2b7054fa105ceddf6ed4e061dab88ae056f0147304402204ebb79cbc3fd9260e28b1f9c4564f3352ce3adba6dc390279808d42961152c9a022034c215c5e9fc88b1622d0636950b5dd1de7b1322e9857736f9a3a63e9bb2cdad01004cda64522103462ab7041341dadd996dc12ef0c118ca8ccb546498cbf304f7ffe0f1b12f9a9e210362634ab57dae9cb373a5d536e66a8c4f67468bbcfb063809bab643072d78a1242103c5946b3fbae03a654237da863c9ed534e0878657175b132b8ca630f245df04db53ae6702d002b2755221029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e433012103284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd2103b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b2453ae68ffffffffefd39f04aa64aec104c68bbecef06dc8ffa916cec5c536b8e03a19e40b2f9af101000000fd700100483045022100a668d635ca8528be0b0affc22c64636f2128b1a1a0891a5f3d2c2579eb9ab4ea0220450088cb20042ce52ebd32c61f2d3b5085c6cd725c36ffae1a751878091a81b601483045022100e25e9ee15ac340063a27258ca905c273cd6fa907271c3d5ddcf1daea2969ddc6022059f190175e98636708bbc4c9cb9809216ee09a0f0d2794e9bf87220567e43c7401004cda64522103462ab7041341dadd996dc12ef0c118ca8ccb546498cbf304f7ffe0f1b12f9a9e210362634ab57dae9cb373a5d536e66a8c4f67468bbcfb063809bab643072d78a1242103c5946b3fbae03a654237da863c9ed534e0878657175b132b8ca630f245df04db53ae6702d002b2755221029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e433012103284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd2103b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b2453ae68ffffffff0124e710000000000017a9140e39a099dabb03863ae5484943896a7c9eb9f9dd8700000000";
        BtcTransaction btcTxBroadcastedAndConfirmed = new BtcTransaction(networkParameters, Hex.decode(rawTxBroadcastedAndConfirmed));

        Set<Sha256Hash> sighashesFromSignedTx = new HashSet<>();
        Set<Sha256Hash> sighashesFromBroadcastedTx = new HashSet<>();
        for (int i = 0; i < pegOutTx.getInputs().size(); i++) {
            sighashesFromSignedTx.add(pegOutTx.hashForSignature(
                i,
                fed.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            ));
            sighashesFromBroadcastedTx.add(btcTxBroadcastedAndConfirmed.hashForSignature(
                i,
                fed.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            ));
        }

        assertArrayEquals(sighashesFromSignedTx.toArray(), sighashesFromBroadcastedTx.toArray());
        assertArrayEquals(sighashes.toArray(), sighashesFromSignedTx.toArray());
    }

    @Test
    void test_each_input_sighash_is_unique_for_a_signed_erp_tx_testnet() {
        // Arrange
        NetworkParameters networkParameters = BridgeTestNetConstants.getInstance().getBtcParams();
        int erpFedActivationDelay = 720;

        List<FedSigner> fedMembers = FedSigner.listOf("federator1", "federator2", "federator6");
        List<FedSigner> erpFedMembers = FedSigner.listOf("erp-fed-01", "erp-fed-02", "erp-fed-03");

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        P2shErpFederation fed = new P2shErpFederation(
            fedMembers.stream().map(FedSigner::getFed).collect(Collectors.toList()),
            Instant.now(),
            0L,
            networkParameters,
            erpFedMembers.stream().map(FedSigner::getFed).map(FederationMember::getBtcPublicKey).collect(Collectors.toList()),
            erpFedActivationDelay,
            activations
        );

        Address expectedAddress = Address.fromBase58(
            networkParameters,
            "2NEfaGq4tGe6bJUxLEzGFoVyZrSrZtXRzJ7"
        );
        assertEquals(expectedAddress, fed.getAddress());

        String RAW_FUND_TX = "0200000000010134ed7734da14ecde305347153f70be45021e8e29137205a9899630f56c68d3890100000000fdffffff02891300000000000017a914eaf58ece160a383630667cfc1ccff519ab07c472873c3e020000000000160014f885b26136ad4d61247132271795cc29ae9cec0302473044022063986423838cdd2abc51b150f11a1c399dce53f68f1bd02e4ec793db2fa3485c022007d8909a83b01c57dcee7e55f3989af949756c6b5068948b888622d2e6673ed70121028f117bfbc90d934b73d0d55afe54ee33a288cdde572bd3d006cede67f4c797a2e7262500";
        FedUtxo utxo1 = FedUtxo.of(new BtcTransaction(networkParameters, Hex.decode(RAW_FUND_TX)), 0);

        String RAW_FUND_TX2 = "020000000001013a43f0d972f5045b443c9c071bd2254f91d5b14d0b5ab5cdeab014c0ff2cc17f0200000000fdffffff027d6408000000000017a914eaf58ece160a383630667cfc1ccff519ab07c472870555dc0500000000220020617a81e635a62aa61d20fd4be369d285da2750c871016282c05a29181552d52b0400473044022048386d4e0270b68a17d3ea10d8c569c2150cb46416c82b3289f2e0ec1fe0272a02204f077bdc8f71d824a4a8a447708cb974e21c015021b94f8226a3da955024371e0147304402204fda66a5cc3503acd325671216f5dbfe4d329499321af508c4959e89072dc47802204531cb9a52c80ede1cab8ca40324ef18ddfb589a0b76f741764334f6f1e9570f014752210203307a637032952b21371f966838e03a768220f8bb663cbc2aa6d41dc71f6bbf2103fdadd0c267fc7a4890c37c6730ca124d53ad7001ee6d9ad00ed50b52a46d784552aee7262500";
        FedUtxo utxo2 = FedUtxo.of(new BtcTransaction(networkParameters, Hex.decode(RAW_FUND_TX2)), 0);

        String RAW_FUND_TX3 = "020000000001016a040fa1ec01bac865d4802083a435fef32052091d6d7bd02cc784d00d75e8400100000000fdffffff02916408000000000017a914eaf58ece160a383630667cfc1ccff519ab07c47287beefd305000000002200201ca17d3f37320ac4469faa1119824d98bbec3b1a586174901834d2b842715e7c0400473044022044033baafc0c15c8e2c3128d01f29a7c7c5c6ec790ead8c5be2610efb0e9d581022014b605cb59a5254a189a9fcd35ec94e6f4faae18f7011a41ac7663df33fbfdc50147304402206fc734a5db737ecb37056491881da15c2f8f2ca2bb9e47db7396029b9a0ad5e102206cae6f8766679c666cfa256e886e2816882623167e92fd9e272fd9712aa49d9a0147522102b9bb9ea5c5c0a56421bc7720e81e7f3e5fb9dfcbca946f01d868940efaa5da18210309eb318fe76a6b139d70e4037dd87733ced9e3ed6f851301a88938c7033d473b52ae05272500";
        FedUtxo utxo3 = FedUtxo.of(new BtcTransaction(networkParameters, Hex.decode(RAW_FUND_TX3)), 0);

        String RAW_FUND_TX4 = "0200000001a223ad7e9d4f5067a8fdddf9e4137af49d5a6dad7ea952533f701061fefd010f020000006a473044022003d999e14be1e2ea15cb16df81e14b30f26c684bcbf6fcbd07975d8648e5f04402207e99a414a0ebf1c3617944610de5ea002439a1ac56e667a60c095184c2419559012102c6bf1e099ec95510a8da3d1c67026cb65a019bad57dc2255dd56a426c629327bfdffffff0300000000000000001b6a1952534b540162db6c4b118d7259c23692b162829e6bd5e4d5b0891300000000000017a914eaf58ece160a383630667cfc1ccff519ab07c4728791200000000000001976a9145952b24450e80668e069b8152a3a38ea7f6ad44c88ace7262500";
        FedUtxo utxo4 = FedUtxo.of(new BtcTransaction(networkParameters, Hex.decode(RAW_FUND_TX4)), 1);

        List<FedUtxo> utxos = Arrays.asList(utxo1, utxo2, utxo3, utxo4);

        Address destinationAddress = Address.fromBase58(networkParameters, "2MtYSUzFWEQV62r92bsGX8ewE5Mgpv4xn9M");

        // Act
        Set<Sha256Hash> sighashes = new HashSet<>();
        BtcTransaction pegOutTx = spendFromFed(
            networkParameters,
            erpFedActivationDelay,
            fed,
            erpFedMembers,
            true,
            utxos,
            Coin.valueOf(1_107_748L),
            destinationAddress,
            sighashes
        );

        // Assert each utxo has a unique sighash by checking the sighashes set has the same size of the utxo list
        Assertions.assertEquals(utxos.size(), sighashes.size());


        // Assert sighash is different for inputs signed by erp vs inputs signed fed
        String rawTxBroadcastedAndConfirmed = "02000000044928841a8bcded3af365da1b57b98b96e4593893862599c504f883b0c956751600000000fd700100483045022100d05a33bd8d0032ed9ccd5f3ba1fb085166c9155f03d95a79e65ef9cf60b9e4b602201287eedce988adf75d8daadc978eca43f47bc33ec999652008e85a3d3dd47f8001483045022100eb5f917967f772328dfd82bd27a096cac453ca336a51d1782ca6684304dd34d5022055bd25e56ed2702974fb58224481e393fca12c4ff34771dab5f456c8fa76568401004cda64522103462ab7041341dadd996dc12ef0c118ca8ccb546498cbf304f7ffe0f1b12f9a9e210362634ab57dae9cb373a5d536e66a8c4f67468bbcfb063809bab643072d78a1242103c5946b3fbae03a654237da863c9ed534e0878657175b132b8ca630f245df04db53ae6702d002b2755221029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e433012103284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd2103b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b2453ae68ffffffff6a040fa1ec01bac865d4802083a435fef32052091d6d7bd02cc784d00d75e84000000000fd6e010047304402206a00bb7a876ecbacd13c06a3e8ca3c55d022e9f35ad6211c65bc4ab3e2a12f1b022051a1d0861a5e812c6c2ffa1be0467fe953ceff6ecb9fc3eb68843441d5c3d7a501473044022044744cceb33a776928c61c8d01cf3acf58c7159ccd5a69914cb78be2a59d5b2d022020ff71156583ba563e9a4d6a8f0bd463d53a6612e831082e0ac9c1c2d1a6d79101004cda64522103462ab7041341dadd996dc12ef0c118ca8ccb546498cbf304f7ffe0f1b12f9a9e210362634ab57dae9cb373a5d536e66a8c4f67468bbcfb063809bab643072d78a1242103c5946b3fbae03a654237da863c9ed534e0878657175b132b8ca630f245df04db53ae6702d002b2755221029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e433012103284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd2103b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b2453ae68ffffffff114c09191b5b1fe685640c23a2ab95bfec521d49ddde4bb96032471762b441e400000000fd6f0100483045022100979a3c231a8ca8c1aa081dffe6cbe0d7ad5987ce4329757cfd8e0c3bfbc5ba8c02202357deaf74162fefc1a05ddf4c2bb2b7054fa105ceddf6ed4e061dab88ae056f0147304402204ebb79cbc3fd9260e28b1f9c4564f3352ce3adba6dc390279808d42961152c9a022034c215c5e9fc88b1622d0636950b5dd1de7b1322e9857736f9a3a63e9bb2cdad01004cda64522103462ab7041341dadd996dc12ef0c118ca8ccb546498cbf304f7ffe0f1b12f9a9e210362634ab57dae9cb373a5d536e66a8c4f67468bbcfb063809bab643072d78a1242103c5946b3fbae03a654237da863c9ed534e0878657175b132b8ca630f245df04db53ae6702d002b2755221029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e433012103284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd2103b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b2453ae68ffffffffefd39f04aa64aec104c68bbecef06dc8ffa916cec5c536b8e03a19e40b2f9af101000000fd700100483045022100a668d635ca8528be0b0affc22c64636f2128b1a1a0891a5f3d2c2579eb9ab4ea0220450088cb20042ce52ebd32c61f2d3b5085c6cd725c36ffae1a751878091a81b601483045022100e25e9ee15ac340063a27258ca905c273cd6fa907271c3d5ddcf1daea2969ddc6022059f190175e98636708bbc4c9cb9809216ee09a0f0d2794e9bf87220567e43c7401004cda64522103462ab7041341dadd996dc12ef0c118ca8ccb546498cbf304f7ffe0f1b12f9a9e210362634ab57dae9cb373a5d536e66a8c4f67468bbcfb063809bab643072d78a1242103c5946b3fbae03a654237da863c9ed534e0878657175b132b8ca630f245df04db53ae6702d002b2755221029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e433012103284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd2103b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b2453ae68ffffffff0124e710000000000017a9140e39a099dabb03863ae5484943896a7c9eb9f9dd8700000000";
        BtcTransaction btcTxBroadcastedAndConfirmed = new BtcTransaction(networkParameters, Hex.decode(rawTxBroadcastedAndConfirmed));

        for (int i = 0; i < pegOutTx.getInputs().size(); i++) {
            Sha256Hash sighashFromSignedTx = pegOutTx.hashForSignature(
                i,
                fed.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            );
            Sha256Hash sighashFromBroadcastedTx = btcTxBroadcastedAndConfirmed.hashForSignature(
                i,
                fed.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            );

            assertNotEquals(sighashFromSignedTx, sighashFromBroadcastedTx);
        }
    }

    private BtcTransaction spendFromFed(
        NetworkParameters networkParameters,
        long activationDelay,
        Federation fed,
        List<FedSigner> signers,
        boolean signWithEmergencyMultisig,
        List<FedUtxo> utxos,
        Coin amount,
        Address destinationAddress,
        Set<Sha256Hash> sighashes
    ) {
        BtcTransaction pegoutTx = new BtcTransaction(networkParameters);
        for (FedUtxo utxo: utxos) {
            TransactionInput input = pegoutTx.addInput(utxo.btcTransaction.getOutput(utxo.outputIdx));
            if (signWithEmergencyMultisig){
                input.setSequenceNumber(activationDelay);
            }
        }
        pegoutTx.addOutput(amount, destinationAddress);
        pegoutTx.setVersion(2);

        for (int i = 0; i < utxos.size(); i++) {
            // Create signatures
            Sha256Hash sigHash = pegoutTx.hashForSignature(
                i,
                fed.getRedeemScript(),
                BtcTransaction.SigHash.ALL,
                false
            );

            sighashes.add(sigHash);

            List<BtcECKey.ECDSASignature> signatures = signers.stream().map(FedSigner::getPrivateKey).map(privateKey -> privateKey.sign(sigHash)).collect(Collectors.toList());

            // Generate an array with the N combinations of N+1 signatures based on Fed composition
            String[] permutations = getSignaturesPermutations(0, 1, signatures.size() / 2 + 1, signatures.size()).split("\n");
            for (String permutation: permutations) {
                String[] idxs = permutation.split(",");
                List<BtcECKey.ECDSASignature> neededSignatures = Arrays.stream(idxs).mapToInt(Integer::parseInt).mapToObj(signatures::get).collect(Collectors.toList());

                Script inputScript = createInputScriptWithSignature(fed, neededSignatures, signWithEmergencyMultisig);
                FedUtxo utxo = utxos.get(i);
                pegoutTx.getInput(i).setScriptSig(inputScript);
                inputScript.correctlySpends(pegoutTx, i, utxo.getBtcTransaction().getOutput(utxo.outputIdx).getScriptPubKey());
            }
        }
        return pegoutTx;
    }
    
    // Utils
    protected Script getRedeemScriptFromInput(TransactionInput txInput) {
        Script inputScript = txInput.getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        // Last chunk of the scriptSig contains the redeem script
        byte[] program = chunks.get(chunks.size() - 1).data;
        return new Script(program);
    }

    protected void removeSignaturesFromTransaction(BtcTransaction tx, Federation spendingFed) {
        for (int inputIndex = 0; inputIndex < tx.getInputs().size(); inputIndex++) {
            TransactionInput txInput = tx.getInput(inputIndex);
            Script inputRedeemScript = getRedeemScriptFromInput(txInput);
            txInput.setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(spendingFed, inputRedeemScript));
        }
    }

    private static Script createBaseInputScriptThatSpendsFromTheFederation(Federation federation, Script customRedeemScript) {
        Script scriptPubKey = federation.getP2SHScript();
        Script redeemScript = federation.getRedeemScript();
        RedeemData redeemData = RedeemData.of(federation.getBtcPublicKeys(), redeemScript);

        // customRedeemScript might not be actually custom, but just in case, use the provided redeemScript
        return scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), customRedeemScript);
    }
    private Script createInputScriptWithSignature(
        Federation federation,
        List<BtcECKey.ECDSASignature> signatures,
        boolean signWithTheEmergencyMultisig) {

        List<byte[]> signaturesEncoded = signatures.stream().map(signature -> {
            TransactionSignature txSignature = new TransactionSignature(
                signature,
                BtcTransaction.SigHash.ALL,
                false
            );
            return txSignature.encodeToBitcoin();
        }).collect(Collectors.toList());

        ScriptBuilder scriptBuilder = new ScriptBuilder();
        scriptBuilder = scriptBuilder.number(0);
        signaturesEncoded.forEach(scriptBuilder::data);

        if (federation instanceof P2shErpFederation || federation instanceof ErpFederation){
            int flowOpCode = signWithTheEmergencyMultisig ? 1 : 0;
            scriptBuilder.number(flowOpCode);
        }
        return scriptBuilder.data(federation.getRedeemScript().getProgram()).build();
    }

    private String getSignaturesPermutations(int start, int from, int until, int size) {
        String key = start + ":" + from + ":" + until;
        if(memo.containsValue(key))
            return memo.get(key);
        else {
            if (start > size / 2 + 1 || until > size){
                return "";
            } else {
                StringBuilder sb = new StringBuilder(size / 2 + 1);
                sb.append(start);
                for (int i = from; i < until; i++) {
                    sb.append(",");
                    sb.append(i);
                }
                sb.append("\n");
                memo.put(key, sb.toString());
                if (until < size){
                    return sb + getSignaturesPermutations(start, ++from, ++until, size);
                } else {
                    int new_until = size - (size / 2 - start) + 1;
                    return sb + getSignaturesPermutations(++start, ++start, new_until, size);
                }
            }
        }
    }

    private static class FedMember implements Comparable<FedSigner> {
        protected final FederationMember fed;

        public static FedMember of(String publicKey) {
            return new FedMember(publicKey);
        }
        public static FedMember of(String btcPk, String rskPk, String mstPk) {
            byte[] btcPkBytes = Hex.decode(btcPk);
            BtcECKey btcKey = BtcECKey.fromPublicOnly(btcPkBytes);
            byte[] rskPkBytes = Hex.decode(rskPk);
            ECKey rskKey = ECKey.fromPublicOnly(rskPkBytes);
            byte[] mstPkBytes = Hex.decode(mstPk);
            ECKey mstKey = ECKey.fromPublicOnly(mstPkBytes);
            return new FedMember(new FederationMember(btcKey, rskKey, mstKey));
        }
        public static FedMember of(FederationMember fed) {
            return new FedMember(fed);
        }


        private FedMember(String publicKey) {
            byte[] publicKeyBytes = Hex.decode(publicKey);
            BtcECKey btcKey = BtcECKey.fromPublicOnly(publicKeyBytes);
            ECKey rskKey = ECKey.fromPublicOnly(publicKeyBytes);
            fed = new FederationMember(btcKey, rskKey, rskKey);
        }

        private FedMember(FederationMember fed) {
            this.fed = fed;
        }

        public FederationMember getFed() {
            return fed;
        }

        @Override
        public int compareTo(FedSigner other) {
            return BTC_RSK_MST_PUBKEYS_COMPARATOR.compare(this.fed, other.fed);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FedSigner)) return false;

            FedSigner that = (FedSigner) o;

            return getFed().equals(that.getFed());
        }

        @Override
        public int hashCode() {
            return getFed().hashCode();
        }

        @Override
        public String toString() {
            return "FedMemberWithSK{" +
                       "fed=" + fed +
                       '}';
        }
    }

    private static class FedSigner extends FedMember {
        BtcECKey privateKey;

        public static FedSigner of(String seed) {
            ECKey key = ECKey.fromPrivate(HashUtil.keccak256(seed.getBytes(StandardCharsets.UTF_8)));
            return new FedSigner(
                ByteUtil.toHexString(key.getPubKey()),
                ByteUtil.toHexString(key.getPrivKeyBytes())
            );
        }

        public static List<FedSigner> listOf(String ... seeds){
            return Arrays.stream(seeds).map(FedSigner::of).sorted().collect(Collectors.toList());
        }

        public FedSigner(String publicKeyHex, String secretKeyHex) {
            super(publicKeyHex);
            privateKey = BtcECKey.fromPrivate(Hex.decode(secretKeyHex));
        }

        public BtcECKey getPrivateKey() {
            return privateKey;
        }
    }

    private static class FedUtxo {
        private final BtcTransaction btcTransaction;
        private final int outputIdx;

        public static FedUtxo of(NetworkParameters networkParameters, String rawTxHex, int outputIdx){
            BtcTransaction utxo = new BtcTransaction(networkParameters, Hex.decode(rawTxHex));
            return new FedUtxo(utxo, outputIdx);
        }

        public static FedUtxo of(BtcTransaction utxo, int outputIdx){
            return new FedUtxo(utxo, outputIdx);
        }

        private FedUtxo(BtcTransaction btcTransaction, int outputIdx) {
            this.btcTransaction = btcTransaction;
            this.outputIdx = outputIdx;
        }

        public BtcTransaction getBtcTransaction() {
            return btcTransaction;
        }

        public int getOutputIdx() {
            return outputIdx;
        }
    }
}
