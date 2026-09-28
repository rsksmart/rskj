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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structurally mutated transaction encodings, seeded from real mainnet transactions. Generation is
 * deterministic so the same corpus can be replayed under a second build and the verdict streams
 * diffed.
 */
public final class WideningCorpus {

    /** Built-in seeds: real mainnet transactions from blocks 1,700-3,500. */
    static final String SEED_RESOURCE = "/widening/seeds-mainnet.txt";

    /** Extra seed file of raw transaction hex, one per line; composes with ticket 03's extraction. */
    static final String SEED_FILE_PROPERTY = "widening.seedFile";

    static final String MAX_SEEDS_PROPERTY = "widening.maxSeeds";

    private static final int[] LEGACY_SCALAR_INDICES = {0, 1, 2, 4, 6, 7, 8};
    private static final byte[] TYPE_PREFIXES = {0x01, 0x02, 0x04};

    private WideningCorpus() {
    }

    /** One corpus input. {@code id} is stable across builds and is the diff key. */
    public static final class Entry {
        private final String id;
        private final String category;
        private final byte[] input;

        Entry(String id, String category, byte[] input) {
            this.id = id;
            this.category = category;
            this.input = input;
        }

        public String id() {
            return id;
        }

        public String category() {
            return category;
        }

        public byte[] input() {
            return input.clone();
        }
    }

    public static List<byte[]> seeds() {
        List<byte[]> seeds = new ArrayList<>(readHexLines(openSeedResource()));
        String seedFile = System.getProperty(SEED_FILE_PROPERTY);
        if (seedFile != null && !seedFile.isEmpty()) {
            try (InputStream in = Files.newInputStream(Path.of(seedFile))) {
                seeds.addAll(readHexLines(in));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + SEED_FILE_PROPERTY + "=" + seedFile, e);
            }
        }
        int max = Integer.getInteger(MAX_SEEDS_PROPERTY, seeds.size());
        return seeds.size() <= max ? seeds : seeds.subList(0, max);
    }

    /** The full deterministic corpus: every seed, plus every structural mutation of it. */
    public static List<Entry> build() {
        List<byte[]> seeds = seeds();
        Map<String, Entry> byId = new LinkedHashMap<>();
        for (int i = 0; i < seeds.size(); i++) {
            String seedId = String.format("seed%03d", i);
            byte[] seed = seeds.get(i);
            put(byId, seedId, "seed", seed);
            for (Map.Entry<String, byte[]> mutant : mutate(seed).entrySet()) {
                put(byId, seedId + "/" + mutant.getKey(), categoryOf(mutant.getValue()), mutant.getValue());
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    /**
     * Every structural mutation of one seed, keyed by mutation name. A mutation that cannot apply
     * to this seed's shape is skipped rather than producing a degenerate input.
     */
    public static Map<String, byte[]> mutate(byte[] seed) {
        Map<String, byte[]> mutants = new LinkedHashMap<>();
        List<byte[]> fields;
        try {
            fields = RlpFrames.listItems(seed, envelopeOffset(seed));
        } catch (RuntimeException e) {
            return mutants;
        }

        byte[] prefix = RlpFrames.slice(seed, 0, envelopeOffset(seed));
        addEnvelopeMutations(mutants, seed, prefix, fields);
        for (int index : LEGACY_SCALAR_INDICES) {
            if (index < fields.size()) {
                addScalarMutations(mutants, prefix, fields, index);
            }
        }
        return mutants;
    }

    private static void addEnvelopeMutations(Map<String, byte[]> mutants, byte[] seed, byte[] prefix, List<byte[]> fields) {
        mutants.put("envelope-longform-header", RlpFrames.concat(prefix, RlpFrames.encodeListLongForm(fields)));
        mutants.put("envelope-trailing-zero", RlpFrames.concat(seed, new byte[]{0x00}));
        mutants.put("envelope-trailing-item", RlpFrames.concat(seed, new byte[]{(byte) 0x80}));
        if (seed.length > 1) {
            mutants.put("envelope-truncated-tail", RlpFrames.slice(seed, 0, seed.length - 1));
        }
        if (!fields.isEmpty()) {
            mutants.put("envelope-field-dropped", rebuild(prefix, fields.subList(0, fields.size() - 1)));
            List<byte[]> duplicated = new ArrayList<>(fields);
            duplicated.add(fields.get(fields.size() - 1));
            mutants.put("envelope-field-duplicated", rebuild(prefix, duplicated));
        }
        List<byte[]> extended = new ArrayList<>(fields);
        extended.add(new byte[]{(byte) 0x80});
        mutants.put("envelope-field-appended", rebuild(prefix, extended));

        List<byte[]> nested = new ArrayList<>();
        nested.add(RlpFrames.encodeList(fields));
        mutants.put("envelope-wrapped-in-list", rebuild(prefix, nested));

        if (prefix.length == 0) {
            for (byte typePrefix : TYPE_PREFIXES) {
                mutants.put("envelope-type-prefix-0" + typePrefix, RlpFrames.concat(new byte[]{typePrefix}, seed));
            }
        }
    }

    private static void addScalarMutations(Map<String, byte[]> mutants, byte[] prefix, List<byte[]> fields, int index) {
        byte[] payload = RlpFrames.payloadOf(fields.get(index));
        String label = "field" + index;

        // Both mainnet spellings of zero. Coercing either into the other is the 2026-09-23 break.
        putField(mutants, prefix, fields, index, label + "-zero-empty", new byte[0]);
        putField(mutants, prefix, fields, index, label + "-zero-single-byte", new byte[]{0x00});

        putField(mutants, prefix, fields, index, label + "-leading-zero", RlpFrames.concat(new byte[]{0x00}, payload));
        putField(mutants, prefix, fields, index, label + "-leading-zeros-8", RlpFrames.concat(new byte[8], payload));
        putField(mutants, prefix, fields, index, label + "-padded-32", leftPad(payload, 32));
        putField(mutants, prefix, fields, index, label + "-padded-33", leftPad(payload, 33));

        // Boundary values whose minimal encoding has the top bit set, so RLP must use 0x81 xx.
        putField(mutants, prefix, fields, index, label + "-top-bit", new byte[]{(byte) 0x80});
        putField(mutants, prefix, fields, index, label + "-max-byte", new byte[]{(byte) 0xFF});
        putField(mutants, prefix, fields, index, label + "-two-bytes", new byte[]{0x01, 0x00});
        putField(mutants, prefix, fields, index, label + "-all-ones-32", allOnes(32));
        putField(mutants, prefix, fields, index, label + "-all-ones-33", allOnes(33));

        if (payload.length > 0) {
            putField(mutants, prefix, fields, index, label + "-truncated", RlpFrames.slice(payload, 0, payload.length - 1));
        }

        List<byte[]> longForm = new ArrayList<>(fields);
        longForm.set(index, RlpFrames.encodeItemLongForm(payload));
        mutants.put(label + "-longform-header", rebuild(prefix, longForm));

        List<byte[]> asList = new ArrayList<>(fields);
        asList.set(index, RlpFrames.encodeList(List.of(RlpFrames.encodeItem(payload))));
        mutants.put(label + "-list-framed", rebuild(prefix, asList));
    }

    private static void putField(Map<String, byte[]> mutants, byte[] prefix, List<byte[]> fields, int index, String name, byte[] payload) {
        List<byte[]> mutated = new ArrayList<>(fields);
        mutated.set(index, RlpFrames.encodeItem(payload));
        mutants.put(name, rebuild(prefix, mutated));
    }

    private static byte[] rebuild(byte[] prefix, List<byte[]> fields) {
        return RlpFrames.concat(prefix, RlpFrames.encodeList(fields));
    }

    /** Offset of the RLP list: 1 for a typed envelope, 0 for legacy. */
    static int envelopeOffset(byte[] encoded) {
        return encoded.length > 0 && (encoded[0] & 0xFF) < 0xC0 ? 1 : 0;
    }

    static String categoryOf(byte[] encoded) {
        return envelopeOffset(encoded) == 1 ? "typed" : "legacy";
    }

    private static byte[] leftPad(byte[] payload, int length) {
        if (payload.length >= length) {
            return payload.clone();
        }
        byte[] out = new byte[length];
        System.arraycopy(payload, 0, out, length - payload.length, payload.length);
        return out;
    }

    private static byte[] allOnes(int length) {
        byte[] out = new byte[length];
        java.util.Arrays.fill(out, (byte) 0xFF);
        return out;
    }

    private static void put(Map<String, Entry> byId, String id, String category, byte[] input) {
        byId.putIfAbsent(id, new Entry(id, category, input));
    }

    private static InputStream openSeedResource() {
        InputStream in = WideningCorpus.class.getResourceAsStream(SEED_RESOURCE);
        if (in == null) {
            throw new IllegalStateException("missing seed resource " + SEED_RESOURCE);
        }
        return in;
    }

    private static List<byte[]> readHexLines(InputStream in) {
        List<byte[]> out = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    out.add(Hex.decode(trimmed));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }
}
