/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.core.transaction.widening;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Differential decode fuzzing for transaction ingress: compares accept/reject verdicts between this
 * build and a baseline build on inputs that never existed on chain. Replaying history can only feed
 * the decoder inputs the old code already accepted, so a decoder that accepts more cannot be caught
 * by any historical block — this target is the only coverage for that direction.
 *
 * <p>Two builds are never loaded into one JVM. This side writes a corpus and a verdict stream; the
 * baseline side runs {@link DecodeVerdicts#main} over the same corpus, and the streams are diffed.
 * See {@code rskj-core/src/fuzzTest/tools/widening-differential.sh}.
 */
class TransactionWideningFuzzTest {

    /** Verdict stream of a baseline build over the same corpus; when unset the differential skips. */
    private static final String BASELINE_PROPERTY = "widening.baseline";

    private static final String OUT_DIR_PROPERTY = "widening.outDir";

    private static Path outDir() {
        return Path.of(System.getProperty(OUT_DIR_PROPERTY, "build/fuzz-widening"));
    }

    @Test
    void emitsVerdictStreamOverTheSeededCorpus() {
        List<WideningCorpus.Entry> corpus = WideningCorpus.build();
        Assertions.assertFalse(corpus.isEmpty(), "corpus is empty");

        Path corpusFile = outDir().resolve("corpus.tsv");
        Path streamFile = outDir().resolve("verdicts-current.jsonl");
        DecodeVerdicts.writeCorpus(corpusFile, corpus);
        List<DecodeVerdicts.Verdict> verdicts = DecodeVerdicts.emit(corpusFile, streamFile);

        Assertions.assertEquals(corpus.size(), verdicts.size(), "every corpus input must get a verdict");
        System.out.println("widening corpus: " + corpus.size() + " inputs, entryPoint="
                + DecodeVerdicts.entryPoint() + ", stream=" + streamFile.toAbsolutePath());
    }

    /**
     * The seeds are real mainnet transactions, so this build must accept every one and re-encode it
     * to the bytes it received. A failure here is a consensus break on history, not a widening.
     */
    @Test
    void acceptsEverySeedAndReproducesItsBytes() {
        List<byte[]> seeds = WideningCorpus.seeds();
        Assumptions.assumeFalse(seeds.isEmpty(), "no seeds available");

        List<String> broken = new ArrayList<>();
        for (int i = 0; i < seeds.size(); i++) {
            DecodeVerdicts.Verdict verdict = DecodeVerdicts.verdict("seed" + i, "seed", seeds.get(i));
            if (!verdict.accepted()) {
                broken.add("seed" + i + " rejected: " + verdict.reason());
            } else if (!verdict.roundTrip()) {
                broken.add("seed" + i + " re-encoded to different bytes");
            }
        }
        Assertions.assertTrue(broken.isEmpty(), "historical transactions must survive decode/encode: " + broken);
    }

    /**
     * Acceptance check for the differ itself: against a deliberately narrowed baseline — one that
     * rejects every legacy scalar carrying a leading zero — the widened side must be reported. A
     * differ that cannot go red is not evidence.
     */
    @Test
    void reportsWideningAgainstADeliberatelyNarrowedBaseline() {
        List<WideningCorpus.Entry> corpus = WideningCorpus.build();
        List<DecodeVerdicts.Verdict> current = new ArrayList<>();
        List<DecodeVerdicts.Verdict> narrowed = new ArrayList<>();
        int expected = 0;
        for (WideningCorpus.Entry entry : corpus) {
            DecodeVerdicts.Verdict verdict = DecodeVerdicts.verdict(entry.id(), entry.category(), entry.input());
            current.add(verdict);
            boolean narrowedRejects = entry.id().contains("-leading-zero");
            narrowed.add(narrowedRejects ? DecodeVerdicts.parseLine(rejectedJson(entry)) : verdict);
            if (narrowedRejects && verdict.accepted()) {
                expected++;
            }
        }

        Assertions.assertTrue(expected > 0, "no leading-zero input is accepted, so the check proves nothing");
        List<DecodeVerdicts.Difference> widenings =
                DecodeVerdicts.of(DecodeVerdicts.diff(narrowed, current), DecodeVerdicts.Divergence.WIDENING);
        Assertions.assertEquals(expected, widenings.size(), "the differ missed a deliberately widened check");
    }

    /**
     * The real differential. Supply the baseline build's stream with
     * {@code -Dwidening.baseline=<file>}; without it there is nothing to compare and the test skips
     * rather than reporting a green it did not earn.
     */
    @Test
    void doesNotAcceptMoreThanTheBaselineBuild() {
        String baselineFile = System.getProperty(BASELINE_PROPERTY);
        Assumptions.assumeTrue(baselineFile != null && !baselineFile.isEmpty(),
                "set -D" + BASELINE_PROPERTY + "=<verdict stream> to run the differential");

        List<WideningCorpus.Entry> corpus = WideningCorpus.build();
        Path corpusFile = outDir().resolve("corpus.tsv");
        DecodeVerdicts.writeCorpus(corpusFile, corpus);
        List<DecodeVerdicts.Verdict> current =
                DecodeVerdicts.emit(corpusFile, outDir().resolve("verdicts-current.jsonl"));
        List<DecodeVerdicts.Difference> differences =
                DecodeVerdicts.diff(DecodeVerdicts.parse(Path.of(baselineFile)), current);

        List<DecodeVerdicts.Difference> narrowings =
                DecodeVerdicts.of(differences, DecodeVerdicts.Divergence.NARROWING);
        System.out.println("widening differential: " + corpus.size() + " inputs, "
                + narrowings.size() + " narrowing (reported, not failed)");
        for (int i = 0; i < narrowings.size() && i < 20; i++) {
            System.out.println("  NARROWING " + narrowings.get(i).id());
        }

        assertNone(differences, DecodeVerdicts.Divergence.CORPUS_MISMATCH);
        assertNone(differences, DecodeVerdicts.Divergence.VALUE);
        assertNone(differences, DecodeVerdicts.Divergence.WIDENING);
    }

    /**
     * Jazzer drives structural mutation beyond the enumerated corpus. Single-jar oracles only: an
     * accepted input must re-encode to bytes this build also accepts, and to the same bytes again.
     */
    @Tag("TransactionWideningFuzzDecode")
    @FuzzTest
    void fuzzMutatedTransactionEnvelopes(FuzzedDataProvider data) {
        List<byte[]> seeds = WideningCorpus.seeds();
        if (seeds.isEmpty()) {
            return;
        }
        byte[] seed = seeds.get(data.consumeInt(0, seeds.size() - 1));
        byte[] input = jazzerMutate(seed, data);

        DecodeVerdicts.Verdict first = DecodeVerdicts.verdict("fuzz", WideningCorpus.categoryOf(input), input);
        if (!first.accepted()) {
            return;
        }
        byte[] encoded = reencode(input);
        DecodeVerdicts.Verdict second = DecodeVerdicts.verdict("fuzz-reencoded", WideningCorpus.categoryOf(encoded), encoded);
        Assertions.assertTrue(second.accepted(),
                "this build emitted an encoding it then rejects: " + second.reason() + " for " + Hex.encode(input));
        Assertions.assertArrayEquals(encoded, reencode(encoded),
                "re-encoding is not idempotent for " + Hex.encode(input));
    }

    private static byte[] reencode(byte[] input) {
        try {
            Object transaction = DecodeVerdicts.class
                    .getClassLoader()
                    .loadClass("org.ethereum.core.Transaction")
                    .getMethod("fromRaw", byte[].class)
                    .invoke(null, (Object) input);
            return (byte[]) transaction.getClass().getMethod("getEncoded").invoke(transaction);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot re-encode", e);
        }
    }

    /** One enumerated mutation chosen by Jazzer, then a byte flip so it can explore past the set. */
    private static byte[] jazzerMutate(byte[] seed, FuzzedDataProvider data) {
        Map<String, byte[]> mutants = WideningCorpus.mutate(seed);
        byte[] input = seed;
        if (!mutants.isEmpty()) {
            List<byte[]> values = new ArrayList<>(mutants.values());
            input = values.get(data.consumeInt(0, values.size() - 1));
        }
        byte[] flipped = input.clone();
        int flips = data.consumeInt(0, 4);
        for (int i = 0; i < flips && flipped.length > 0; i++) {
            flipped[data.consumeInt(0, flipped.length - 1)] ^= (byte) (1 << data.consumeInt(0, 7));
        }
        return flipped;
    }

    private static String rejectedJson(WideningCorpus.Entry entry) {
        return "{\"id\":\"" + entry.id() + "\",\"category\":\"" + entry.category() + "\",\"input\":\""
                + DecodeVerdicts.sha256(entry.input())
                + "\",\"verdict\":\"reject\",\"reason\":\"NarrowedBaseline\",\"encoded\":\"\",\"hash\":\"\",\"roundTrip\":false}";
    }

    private static void assertNone(List<DecodeVerdicts.Difference> differences, DecodeVerdicts.Divergence kind) {
        List<DecodeVerdicts.Difference> selected = DecodeVerdicts.of(differences, kind);
        Assertions.assertTrue(selected.isEmpty(), kind + " divergence against the baseline build: " + selected);
    }
}
