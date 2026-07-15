/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package co.rsk.core.bc;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutPoint;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.Utils;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.blockchain.utils.BlockMiner;
import co.rsk.config.RskMiningConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.mine.ForkBalanceParentCoinbaseProof;
import co.rsk.mine.ForkBalanceProofUtils;
import co.rsk.mine.MinerServerImpl;
import co.rsk.mine.MinerUtils;
import co.rsk.validators.ForkBalanceValidationRule;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Fork-balance proof types 0 / 1 / 2: classification, RLP build/decode, and {@link ForkBalanceValidationRule}.
 */
class ForkBalanceProofTypesTest {

    private static final NetworkParameters BTC = RegTestParams.get();
    private static final ActivationConfig ACTIVATION = ActivationConfigsForTest.allBut(ConsensusRule.RSKIP144);

    /** Distinct 32-byte merged-mining hash referenced in RSK-tagged coinbases. */
    private static final Keccak256 CANONICAL_MM_HASH =
            new Keccak256(Hex.decode("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

    private static final Keccak256 HIDDEN_FORK_MM_HASH =
            new Keccak256(Hex.decode("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));

    private static final TestSystemProperties PROPS = new TestSystemProperties() {
        @Override
        public ActivationConfig getActivationConfig() {
            return ACTIVATION;
        }
    };

    // --- proofTypeIdentification (coinbase + hash list) ---

    @Test
    void proofTypeIdentification_noRskTag_returnsType2() {
        BtcTransaction coinbase = coinbaseWithoutRskTag((byte) 0x01);
        Assertions.assertEquals((byte) 2, ForkBalanceProofUtils.proofTypeIdentification(coinbase, List.of(CANONICAL_MM_HASH)));
    }

    @Test
    void proofTypeIdentification_tagWithHashInList_returnsType0() {
        BtcTransaction coinbase = coinbaseWithRskTag(CANONICAL_MM_HASH);
        Assertions.assertEquals(
                (byte) 0,
                ForkBalanceProofUtils.proofTypeIdentification(coinbase, List.of(CANONICAL_MM_HASH, HIDDEN_FORK_MM_HASH)));
    }

    @Test
    void proofTypeIdentification_tagWithHashNotInList_returnsType1() {
        BtcTransaction coinbase = coinbaseWithRskTag(HIDDEN_FORK_MM_HASH);
        Assertions.assertEquals(
                (byte) 1,
                ForkBalanceProofUtils.proofTypeIdentification(coinbase, List.of(CANONICAL_MM_HASH)));
    }

    @Test
    void proofTypeIdentification_tagWithEmptyHashList_returnsType1() {
        BtcTransaction coinbase = coinbaseWithRskTag(CANONICAL_MM_HASH);
        Assertions.assertEquals((byte) 1, ForkBalanceProofUtils.proofTypeIdentification(coinbase, Collections.emptyList()));
        Assertions.assertEquals((byte) 1, ForkBalanceProofUtils.proofTypeIdentification(coinbase, null));
    }

    @Test
    void extractRskMergedMiningHashFromCoinbase_withTag_returnsHashAfterTag() {
        BtcTransaction coinbase = coinbaseWithRskTag(CANONICAL_MM_HASH);
        byte[] serialized = coinbase.bitcoinSerialize();
        int tagPos = lastIndexOfSubList(serialized, RskMiningConstants.RSK_TAG);
        Assertions.assertTrue(tagPos >= 0);

        Optional<Keccak256> fromCompressed =
                ForkBalanceProofUtils.extractRskMergedMiningHashFromCompressedCoinbase(
                        MinerServerImpl.compressCoinbase(serialized));
        Assertions.assertTrue(fromCompressed.isPresent());
        Assertions.assertEquals(CANONICAL_MM_HASH, fromCompressed.get());
    }

    @Test
    void extractRskMergedMiningHashFromCoinbase_withoutTag_isEmpty() {
        byte[] serialized = coinbaseWithoutRskTag((byte) 0x02).bitcoinSerialize();
        Assertions.assertFalse(
                ForkBalanceProofUtils.mergedMiningStoredCoinbaseContainsRskTag(
                        MinerServerImpl.compressCoinbase(serialized)));
    }

    // --- buildForkBalanceProofSkeleton (no proofType on wire) ---

    @Test
    void buildSkeleton_roundTripsParentCoinbaseWithoutEmbeddingProofType() throws Exception {
        MergedMiningFixture fx = buildMergedMiningFixture(coinbaseWithoutRskTag((byte) 0x10), coinbaseWithoutRskTag((byte) 0x11));
        byte[] proof = ForkBalanceProofUtils.buildForkBalanceProofSkeleton(
                fx.mergedChild(),
                fx.btcParent(),
                fx.parentMerkleProof());

        ForkBalanceProofUtils.ForkBalanceProofDecoded d = ForkBalanceProofUtils.decodeForkBalanceProof(proof);
        Assertions.assertEquals(80, d.getParentBtcHeader().length);
        Assertions.assertTrue(d.getCoinbaseLastBytes().length > 0);
        Assertions.assertEquals(
                (byte) 2,
                ForkBalanceProofUtils.proofTypeIdentificationFromCoinbaseSuffix(
                        d.getCoinbaseLastBytes(), List.of(CANONICAL_MM_HASH)));
    }

    @Test
    void buildSkeleton_suffixClassifiesType0_whenParentTagHashInList() throws Exception {
        MergedMiningFixture fx = buildMergedMiningFixture(
                coinbaseWithRskTag(CANONICAL_MM_HASH), coinbaseWithoutRskTag((byte) 0x12));
        byte[] proof = ForkBalanceProofUtils.buildForkBalanceProofSkeleton(
                fx.mergedChild(),
                fx.btcParent(),
                fx.parentMerkleProof());

        Assertions.assertEquals(
                (byte) 0,
                ForkBalanceProofUtils.proofTypeIdentificationFromCoinbaseSuffix(
                        ForkBalanceProofUtils.decodeForkBalanceProof(proof).getCoinbaseLastBytes(),
                        List.of(CANONICAL_MM_HASH)));
    }

    @Test
    void buildSkeleton_suffixClassifiesType1_whenParentTagHashNotInList() throws Exception {
        MergedMiningFixture fx = buildMergedMiningFixture(
                coinbaseWithRskTag(HIDDEN_FORK_MM_HASH), coinbaseWithoutRskTag((byte) 0x13));
        byte[] proof = ForkBalanceProofUtils.buildForkBalanceProofSkeleton(
                fx.mergedChild(),
                fx.btcParent(),
                fx.parentMerkleProof());

        Assertions.assertEquals(
                (byte) 1,
                ForkBalanceProofUtils.proofTypeIdentificationFromCoinbaseSuffix(
                        ForkBalanceProofUtils.decodeForkBalanceProof(proof).getCoinbaseLastBytes(),
                        List.of(CANONICAL_MM_HASH)));
    }

    @Test
    void encodeDecode_roundTrip_preservesCoinbaseFields() throws Exception {
        MergedMiningFixture fx = buildMergedMiningFixture(
                coinbaseWithRskTag(CANONICAL_MM_HASH),
                coinbaseWithoutRskTag((byte) 0x21));
        byte[] proof = encodeProofWithParentCoinbase(
                fx.btcParent().cloneAsHeader().bitcoinSerialize(),
                fx.btcParent().getTransactions().get(0).bitcoinSerialize(),
                fx.parentMerkleProof());

        ForkBalanceProofUtils.ForkBalanceProofDecoded decoded = ForkBalanceProofUtils.decodeForkBalanceProof(proof);
        Assertions.assertEquals(80, decoded.getParentBtcHeader().length);
        Assertions.assertTrue(decoded.getCoinbaseLastBytes().length > 0);
        Assertions.assertEquals(32, decoded.getCoinbaseHash().length);
        Assertions.assertTrue(ForkBalanceParentCoinbaseProof.isValidMidstateWireBytes(decoded.getMidStateProof()));
        Assertions.assertTrue(decoded.getCoinbaseProof().length > 0);
    }

    @Test
    void parentCoinbaseProof_roundTrip_recomputesHash() {
        BtcTransaction parentCoinbase = coinbaseWithoutRskTag((byte) 0x70);
        byte[] serialized = parentCoinbase.bitcoinSerialize();
        ForkBalanceParentCoinbaseProof.CompactFields compact =
                ForkBalanceParentCoinbaseProof.fromSerialized(serialized);
        Assertions.assertTrue(ForkBalanceParentCoinbaseProof.verifyCoinbaseHash(
                compact.getMidStateProof(),
                compact.getCoinbaseLastBytes(),
                compact.getCoinbaseHash()));
        Assertions.assertArrayEquals(parentCoinbase.getHash().getBytes(), compact.getCoinbaseHash());
    }

    @Test
    void parentCoinbaseProof_midstateFirstRoundMatchesFullSha256ForLongTx() {
        BtcTransaction tx = new BtcTransaction(BTC);
        byte[] padding = new byte[200];
        java.util.Arrays.fill(padding, (byte) 0x5a);
        tx.addInput(new TransactionInput(BTC, tx, padding));
        tx.addOutput(new TransactionOutput(
                BTC, tx, Coin.valueOf(0, 0), new byte[]{ScriptOpCodes.OP_RETURN, 0x01}));
        byte[] serialized = tx.bitcoinSerialize();
        int suffixLen = Math.min(ForkBalanceFacProtocolConstants.PARENT_COINBASE_SUFFIX_MAX_BYTES, serialized.length);
        int prefixLen = serialized.length - suffixLen;

        SHA256Digest prefixDigest = new SHA256Digest();
        if (prefixLen > 0) {
            prefixDigest.update(serialized, 0, prefixLen);
        }
        SHA256Digest fromMid = new SHA256Digest(prefixDigest.getEncodedState());
        fromMid.update(serialized, prefixLen, suffixLen);
        byte[] fromMidFirst = new byte[32];
        fromMid.doFinal(fromMidFirst, 0);

        SHA256Digest full = new SHA256Digest();
        full.update(serialized, 0, serialized.length);
        byte[] fullFirst = new byte[32];
        full.doFinal(fullFirst, 0);

        Assertions.assertArrayEquals(fullFirst, fromMidFirst);
        ForkBalanceParentCoinbaseProof.CompactFields compact =
                ForkBalanceParentCoinbaseProof.fromSerialized(serialized);
        Assertions.assertTrue(ForkBalanceParentCoinbaseProof.verifyCoinbaseHash(
                compact.getMidStateProof(),
                compact.getCoinbaseLastBytes(),
                compact.getCoinbaseHash()));
    }

    @Test
    void parentCoinbaseProof_worksForLongSerializedCoinbase() {
        BtcTransaction tx = new BtcTransaction(BTC);
        byte[] padding = new byte[200];
        java.util.Arrays.fill(padding, (byte) 0x5a);
        tx.addInput(new TransactionInput(BTC, tx, padding));
        tx.addOutput(new TransactionOutput(
                BTC, tx, Coin.valueOf(0, 0), new byte[]{ScriptOpCodes.OP_RETURN, 0x01}));
        byte[] serialized = tx.bitcoinSerialize();
        Assertions.assertTrue(serialized.length > ForkBalanceFacProtocolConstants.PARENT_COINBASE_SUFFIX_MAX_BYTES);
        ForkBalanceParentCoinbaseProof.CompactFields compact =
                ForkBalanceParentCoinbaseProof.fromSerialized(serialized);
        Assertions.assertTrue(ForkBalanceParentCoinbaseProof.verifyCoinbaseHash(
                compact.getMidStateProof(),
                compact.getCoinbaseLastBytes(),
                compact.getCoinbaseHash()));
    }

    @Test
    void parentCoinbaseProof_worksForMinerUtilsCoinbase() {
        byte[] hash = Hex.decode("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        BtcTransaction coinbase = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(BTC, hash);
        ForkBalanceParentCoinbaseProof.CompactFields compact =
                ForkBalanceParentCoinbaseProof.fromSerialized(coinbase.bitcoinSerialize());
        Assertions.assertTrue(ForkBalanceParentCoinbaseProof.verifyCoinbaseHash(
                compact.getMidStateProof(),
                compact.getCoinbaseLastBytes(),
                compact.getCoinbaseHash()));
    }

    @Test
    void parentCoinbaseProof_rejectsTamperedSuffix() {
        ForkBalanceParentCoinbaseProof.CompactFields compact =
                ForkBalanceParentCoinbaseProof.fromSerialized(coinbaseWithoutRskTag((byte) 0x71).bitcoinSerialize());
        byte[] tampered = compact.getCoinbaseLastBytes().clone();
        tampered[tampered.length - 1] ^= 0x01;
        Assertions.assertFalse(ForkBalanceParentCoinbaseProof.verifyCoinbaseHash(
                compact.getMidStateProof(), tampered, compact.getCoinbaseHash()));
    }

    // --- ForkBalanceValidationRule (crypto only; proofType is local metadata) ---

    @Test
    void validation_acceptsCryptographicallyValidProofs() throws Exception {
        Assertions.assertTrue(buildAndValidate(coinbaseWithRskTag(CANONICAL_MM_HASH)));
        Assertions.assertTrue(buildAndValidate(coinbaseWithRskTag(HIDDEN_FORK_MM_HASH)));
        Assertions.assertTrue(buildAndValidate(coinbaseWithoutRskTag((byte) 0x30)));
    }

    @Test
    void facEvidenceValue_mapsProofTypesFromCoinbaseSuffixAndCache() throws Exception {
        Block type0 = blockWithParentCoinbase(coinbaseWithRskTag(CANONICAL_MM_HASH));
        Block type1 = blockWithParentCoinbase(coinbaseWithRskTag(HIDDEN_FORK_MM_HASH));
        Block type2 = blockWithParentCoinbase(coinbaseWithoutRskTag((byte) 0x60));

        Assertions.assertEquals(
                1,
                FacEvidenceCalculator.facEvidenceValueFromProofType(
                        FacEvidenceCalculator.proofTypeFromBlock(type0, List.of(CANONICAL_MM_HASH))));
        Assertions.assertEquals(
                -1,
                FacEvidenceCalculator.facEvidenceValueFromProofType(
                        FacEvidenceCalculator.proofTypeFromBlock(type1, List.of(CANONICAL_MM_HASH))));
        Assertions.assertEquals(
                0,
                FacEvidenceCalculator.facEvidenceValueFromProofType(
                        FacEvidenceCalculator.proofTypeFromBlock(type2, List.of(CANONICAL_MM_HASH))));
    }

    // --- helpers ---

    private static boolean buildAndValidate(BtcTransaction parentCoinbase) throws Exception {
        MergedMiningFixture fx = buildMergedMiningFixture(
                parentCoinbase, coinbaseWithoutRskTag((byte) 0x50));
        byte[] proof = encodeProofWithParentCoinbase(
                fx.btcParent().cloneAsHeader().bitcoinSerialize(),
                fx.btcParent().getTransactions().get(0).bitcoinSerialize(),
                fx.parentMerkleProof());
        Block block = v3BlockWithProof(fx, proof);
        return forkRule().isValid(block);
    }

    private static Block v3BlockWithProof(MergedMiningFixture fx, byte[] proof) {
        Block block = new BlockGenerator(Constants.regtest(), ACTIVATION).getBlock(1);
        Block modified = new BlockFactory(ACTIVATION).cloneBlockForModification(block);
        Assertions.assertEquals((byte) 0x03, modified.getHeader().getVersion());
        modified.getHeader().setForkBalanceProof(proof);
        modified.setBitcoinMergedMiningHeader(fx.mergedChild().cloneAsHeader().bitcoinSerialize());
        modified.setBitcoinMergedMiningCoinbaseTransaction(
                MinerServerImpl.compressCoinbase(fx.childCoinbase().bitcoinSerialize()));
        modified.setBitcoinMergedMiningMerkleProof(
                MinerUtils.buildMerkleProof(ACTIVATION, pb -> pb.buildFromBlock(fx.mergedChild()), modified.getNumber()));
        return modified;
    }

    private static Block blockWithParentCoinbase(BtcTransaction parentCoinbase) throws Exception {
        MergedMiningFixture fx = buildMergedMiningFixture(
                parentCoinbase,
                coinbaseWithoutRskTag((byte) 0x61));
        byte[] proof = encodeProofWithParentCoinbase(
                fx.btcParent().cloneAsHeader().bitcoinSerialize(),
                fx.btcParent().getTransactions().get(0).bitcoinSerialize(),
                fx.parentMerkleProof());
        return v3BlockWithProof(fx, proof);
    }

    private static ForkBalanceValidationRule forkRule() {
        return new ForkBalanceValidationRule(
                ACTIVATION,
                PROPS.getNetworkConstants().getBridgeConstants());
    }

    /**
     * Coinbase whose {@link BtcTransaction#bitcoinSerialize()} contains {@link RskMiningConstants#RSK_TAG}
     * followed by {@code hashAfterTag} (same layout miners use in the extra output).
     */
    private static BtcTransaction coinbaseWithRskTag(Keccak256 hashAfterTag) {
        BtcTransaction tx = new BtcTransaction(BTC);
        tx.addInput(new TransactionInput(BTC, tx, new byte[]{0x42}));
        byte[] taggedPayload = org.bouncycastle.util.Arrays.concatenate(
                RskMiningConstants.RSK_TAG, hashAfterTag.getBytes());
        tx.addOutput(new TransactionOutput(BTC, tx, Coin.valueOf(50, 0), new byte[]{ScriptOpCodes.OP_RETURN}));
        tx.addOutput(new TransactionOutput(BTC, tx, Coin.valueOf(0, 0), taggedPayload));
        return tx;
    }

    /** Serialized coinbase with no {@code RSKBLOCK:} subsequence. */
    private static BtcTransaction coinbaseWithoutRskTag(byte marker) {
        BtcTransaction tx = new BtcTransaction(BTC);
        tx.addInput(new TransactionInput(BTC, tx, new byte[]{marker}));
        tx.addOutput(new TransactionOutput(
                BTC, tx, Coin.valueOf(0, 0), new byte[]{ScriptOpCodes.OP_RETURN, marker, 0x01, 0x02}));
        return tx;
    }

    private static byte[] encodeProofWithParentCoinbase(
            byte[] parentBtcHeader,
            byte[] parentCoinbaseSerialized,
            byte[] parentMerkleProof) {
        ForkBalanceParentCoinbaseProof.CompactFields compact =
                ForkBalanceParentCoinbaseProof.fromSerialized(parentCoinbaseSerialized);
        return ForkBalanceProofUtils.encodeForkBalanceProofSkeleton(
                parentBtcHeader,
                compact.getCoinbaseHash(),
                parentMerkleProof,
                compact.getCoinbaseLastBytes(),
                compact.getMidStateProof());
    }

    private static MergedMiningFixture buildMergedMiningFixture(
            BtcTransaction parentCoinbase,
            BtcTransaction childCoinbase) {
        BtcBlock btcParent = mineParentWithTwoTxs(parentCoinbase);
        BtcBlock mergedChild = new BtcBlock(
                BTC,
                BTC.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT),
                btcParent.getHash(),
                null,
                btcParent.getTimeSeconds() + 600,
                btcParent.getDifficultyTarget(),
                0,
                Collections.singletonList(childCoinbase));
        new BlockMiner(ACTIVATION).findNonce(mergedChild, BTC.getMaxTarget());
        byte[] parentMerkleProof = MinerUtils.buildMerkleProof(
                ACTIVATION,
                pb -> pb.buildFromBlock(btcParent),
                1L);
        Assertions.assertTrue(parentMerkleProof.length > 0, "parent merkle proof required for validation");
        return new MergedMiningFixture(btcParent, mergedChild, childCoinbase, parentMerkleProof);
    }

    private static BtcBlock mineParentWithTwoTxs(BtcTransaction parentCoinbase) {
        BtcTransaction tx2 = new BtcTransaction(BTC);
        tx2.addInput(new TransactionInput(
                BTC,
                tx2,
                new byte[0],
                new TransactionOutPoint(BTC, 0, parentCoinbase.getHash())));
        tx2.addOutput(new TransactionOutput(
                BTC, tx2, Coin.valueOf(0, 0), new byte[]{ScriptOpCodes.OP_RETURN, (byte) 0x7e}));
        BtcBlock block = new BtcBlock(
                BTC,
                BTC.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT),
                Sha256Hash.ZERO_HASH,
                null,
                (System.currentTimeMillis() / 1000) - 2_000_000,
                Utils.encodeCompactBits(BTC.getMaxTarget()),
                0,
                Arrays.asList(parentCoinbase, tx2));
        new BlockMiner(ACTIVATION).findNonce(block, BTC.getMaxTarget());
        return block;
    }

    private static int lastIndexOfSubList(byte[] haystack, byte[] needle) {
        List<Byte> h = bytesAsList(haystack);
        List<Byte> n = bytesAsList(needle);
        return java.util.Collections.lastIndexOfSubList(h, n);
    }

    private static List<Byte> bytesAsList(byte[] bytes) {
        List<Byte> list = new java.util.ArrayList<>(bytes.length);
        for (byte b : bytes) {
            list.add(b);
        }
        return list;
    }

    private record MergedMiningFixture(
            BtcBlock btcParent,
            BtcBlock mergedChild,
            BtcTransaction childCoinbase,
            byte[] parentMerkleProof) {
    }
}
