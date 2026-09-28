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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accept/reject verdicts for raw transaction bytes, emitted as JSONL so two builds can be compared
 * by diffing their streams.
 *
 * <p>Reflection only, and no rskj imports: the same source compiles against the master jar, where
 * the entry point is {@code new ImmutableTransaction(byte[])} rather than
 * {@code Transaction.fromRaw(byte[])}. Run it with {@code main} against the baseline build.
 */
public final class DecodeVerdicts {

    /** How a divergence between two streams breaks. */
    public enum Divergence {
        /** Current accepts what the baseline rejects: a chain split no historical block can show. */
        WIDENING,
        /** Current rejects what the baseline accepts. */
        NARROWING,
        /** Both accept but produce different bytes — the shape of the 2026-09-23 gasPrice break. */
        VALUE,
        /** The two streams do not describe the same corpus. */
        CORPUS_MISMATCH
    }

    public static final class Verdict {
        private final String id;
        private final String category;
        private final String inputDigest;
        private final boolean accepted;
        private final String reason;
        private final String encodedDigest;
        private final String hash;
        private final boolean roundTrip;

        Verdict(String id, String category, String inputDigest, boolean accepted, String reason,
                String encodedDigest, String hash, boolean roundTrip) {
            this.id = id;
            this.category = category;
            this.inputDigest = inputDigest;
            this.accepted = accepted;
            this.reason = reason;
            this.encodedDigest = encodedDigest;
            this.hash = hash;
            this.roundTrip = roundTrip;
        }

        public String id() {
            return id;
        }

        public String category() {
            return category;
        }

        public boolean accepted() {
            return accepted;
        }

        public String reason() {
            return reason;
        }

        public boolean roundTrip() {
            return roundTrip;
        }

        public String toJson() {
            return "{\"id\":\"" + id + "\",\"category\":\"" + category + "\",\"input\":\"" + inputDigest
                    + "\",\"verdict\":\"" + (accepted ? "accept" : "reject") + "\",\"reason\":\"" + escape(reason)
                    + "\",\"encoded\":\"" + encodedDigest + "\",\"hash\":\"" + hash
                    + "\",\"roundTrip\":" + roundTrip + "}";
        }

        /** Rejected with a {@code reason} that survives a build change, so it is safe to diff on. */
        Verdict withoutReason() {
            return new Verdict(id, category, inputDigest, accepted, "", encodedDigest, hash, roundTrip);
        }
    }

    public static final class Difference {
        private final Divergence kind;
        private final Verdict baseline;
        private final Verdict current;

        Difference(Divergence kind, Verdict baseline, Verdict current) {
            this.kind = kind;
            this.baseline = baseline;
            this.current = current;
        }

        public Divergence kind() {
            return kind;
        }

        public String id() {
            return current != null ? current.id() : baseline.id();
        }

        @Override
        public String toString() {
            return kind + " " + id()
                    + " baseline=" + (baseline == null ? "absent" : baseline.toJson())
                    + " current=" + (current == null ? "absent" : current.toJson());
        }
    }

    private static Method fromRaw;
    private static Constructor<?> immutableConstructor;
    private static String entryPoint;

    private DecodeVerdicts() {
    }

    /** Decodes {@code input} and records what the build did with it. */
    public static Verdict verdict(String id, String category, byte[] input) {
        String inputDigest = sha256(input);
        try {
            Object transaction = decode(input);
            byte[] encoded = (byte[]) transaction.getClass().getMethod("getEncoded").invoke(transaction);
            String hash = hashOf(transaction);
            return new Verdict(id, category, inputDigest, true, "", sha256(encoded), hash,
                    java.util.Arrays.equals(input, encoded));
        } catch (InvocationTargetException e) {
            return rejected(id, category, inputDigest, e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("decode entry point is not usable on this build", e);
        } catch (RuntimeException | StackOverflowError e) {
            return rejected(id, category, inputDigest, e);
        }
    }

    private static Verdict rejected(String id, String category, String inputDigest, Throwable cause) {
        return new Verdict(id, category, inputDigest, false, cause.getClass().getSimpleName(), "", "", false);
    }

    private static Object decode(byte[] input) throws ReflectiveOperationException {
        resolveEntryPoint();
        if (fromRaw != null) {
            return fromRaw.invoke(null, (Object) input);
        }
        return immutableConstructor.newInstance((Object) input);
    }

    private static String hashOf(Object transaction) throws ReflectiveOperationException {
        Object hash = transaction.getClass().getMethod("getHash").invoke(transaction);
        if (hash == null) {
            return "";
        }
        try {
            return Hex.encode((byte[]) hash.getClass().getMethod("getBytes").invoke(hash));
        } catch (NoSuchMethodException e) {
            return String.valueOf(hash);
        }
    }

    static synchronized void resolveEntryPoint() throws ReflectiveOperationException {
        if (entryPoint != null) {
            return;
        }
        try {
            fromRaw = Class.forName("org.ethereum.core.Transaction").getMethod("fromRaw", byte[].class);
            entryPoint = "Transaction.fromRaw";
        } catch (NoSuchMethodException e) {
            immutableConstructor = Class.forName("org.ethereum.core.ImmutableTransaction").getConstructor(byte[].class);
            entryPoint = "new ImmutableTransaction";
        }
    }

    public static String entryPoint() {
        try {
            resolveEntryPoint();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no usable decode entry point on this build", e);
        }
        return entryPoint;
    }

    // ---------------------------------------------------------------- streams

    /** Corpus file format: {@code id TAB category TAB hex}, one input per line. */
    public static void writeCorpus(Path file, List<WideningCorpus.Entry> corpus) {
        List<String> lines = new ArrayList<>(corpus.size());
        for (WideningCorpus.Entry entry : corpus) {
            lines.add(entry.id() + "\t" + entry.category() + "\t" + Hex.encode(entry.input()));
        }
        write(file, lines);
    }

    public static List<Verdict> emit(Path corpusFile, Path outFile) {
        List<Verdict> verdicts = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        lines.add("{\"kind\":\"meta\",\"entryPoint\":\"" + entryPoint() + "\"}");
        for (String line : read(corpusFile)) {
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\t", 3);
            Verdict verdict = verdict(parts[0], parts[1], Hex.decode(parts[2]));
            verdicts.add(verdict);
            lines.add(verdict.toJson());
        }
        write(outFile, lines);
        return verdicts;
    }

    public static List<Verdict> parse(Path file) {
        List<Verdict> verdicts = new ArrayList<>();
        for (String line : read(file)) {
            if (line.isEmpty() || line.contains("\"kind\":\"meta\"")) {
                continue;
            }
            verdicts.add(parseLine(line));
        }
        return verdicts;
    }

    static Verdict parseLine(String json) {
        return new Verdict(
                field(json, "id"),
                field(json, "category"),
                field(json, "input"),
                "accept".equals(field(json, "verdict")),
                field(json, "reason"),
                field(json, "encoded"),
                field(json, "hash"),
                json.contains("\"roundTrip\":true"));
    }

    private static String field(String json, String name) {
        String key = "\"" + name + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            return "";
        }
        start += key.length();
        return json.substring(start, json.indexOf('"', start));
    }

    // ---------------------------------------------------------------- diffing

    /**
     * Divergences between two verdict streams, keyed by corpus id. Exception messages are not
     * compared: two builds may reject the same bytes for differently worded reasons.
     */
    public static List<Difference> diff(List<Verdict> baseline, List<Verdict> current) {
        Map<String, Verdict> baselineById = new LinkedHashMap<>();
        for (Verdict verdict : baseline) {
            baselineById.put(verdict.id, verdict);
        }
        List<Difference> differences = new ArrayList<>();
        for (Verdict now : current) {
            Verdict before = baselineById.remove(now.id);
            if (before == null) {
                differences.add(new Difference(Divergence.CORPUS_MISMATCH, null, now.withoutReason()));
            } else if (!before.inputDigest.equals(now.inputDigest)) {
                differences.add(new Difference(Divergence.CORPUS_MISMATCH, before, now));
            } else if (!before.accepted && now.accepted) {
                differences.add(new Difference(Divergence.WIDENING, before, now));
            } else if (before.accepted && !now.accepted) {
                differences.add(new Difference(Divergence.NARROWING, before, now));
            } else if (now.accepted && !(before.encodedDigest.equals(now.encodedDigest) && before.hash.equals(now.hash))) {
                differences.add(new Difference(Divergence.VALUE, before, now));
            }
        }
        for (Verdict orphan : baselineById.values()) {
            differences.add(new Difference(Divergence.CORPUS_MISMATCH, orphan, null));
        }
        return differences;
    }

    public static List<Difference> of(List<Difference> differences, Divergence kind) {
        List<Difference> selected = new ArrayList<>();
        for (Difference difference : differences) {
            if (difference.kind == kind) {
                selected.add(difference);
            }
        }
        return selected;
    }

    // ---------------------------------------------------------------- runner

    /**
     * {@code emit <corpus> <out.jsonl>} runs this build over a corpus; {@code diff <baseline>
     * <current>} compares two streams and exits non-zero on a widening or value divergence.
     */
    public static void main(String[] args) {
        if (args.length == 3 && "emit".equals(args[0])) {
            List<Verdict> verdicts = emit(Path.of(args[1]), Path.of(args[2]));
            System.out.println("entryPoint=" + entryPoint() + " inputs=" + verdicts.size() + " out=" + args[2]);
            return;
        }
        if (args.length == 3 && "diff".equals(args[0])) {
            System.exit(report(diff(parse(Path.of(args[1])), parse(Path.of(args[2])))));
            return;
        }
        System.err.println("usage: DecodeVerdicts emit <corpus> <out.jsonl> | diff <baseline.jsonl> <current.jsonl>");
        System.exit(2);
    }

    private static int report(List<Difference> differences) {
        for (Divergence kind : Divergence.values()) {
            List<Difference> selected = of(differences, kind);
            System.out.println(kind + ": " + selected.size());
            for (int i = 0; i < selected.size() && i < 20; i++) {
                System.out.println("  " + selected.get(i));
            }
            if (selected.size() > 20) {
                System.out.println("  ... " + (selected.size() - 20) + " more");
            }
        }
        boolean fatal = !of(differences, Divergence.WIDENING).isEmpty()
                || !of(differences, Divergence.VALUE).isEmpty()
                || !of(differences, Divergence.CORPUS_MISMATCH).isEmpty();
        return fatal ? 1 : 0;
    }

    // ---------------------------------------------------------------- io

    private static List<String> read(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private static void write(Path file, List<String> lines) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    static String sha256(byte[] data) {
        try {
            return Hex.encode(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
