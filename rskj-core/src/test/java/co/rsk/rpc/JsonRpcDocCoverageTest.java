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

package co.rsk.rpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.rsk.cli.tools.GenerateOpenRpcDoc;
import org.ethereum.rpc.Web3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Keeps the JSON-RPC reference under {@code doc/rpc} in step with the methods the node actually dispatches.
 *
 * <p>The exposed set is derived by reflection over {@link Web3RskImpl}, and the choice of class is
 * load-bearing rather than incidental. {@code JsonRpcWeb3ServerHandler} hands jsonrpc4j
 * {@code service.getClass()} — the concrete implementation — as its {@code remoteInterface}, and that class
 * is what backs both the existence gate in {@code JsonRpcCustomServer.extractMethodNames} and dispatch in
 * {@code ReflectionUtil.findCandidateMethods}. The {@link Web3} interface is <em>not</em> the dispatch
 * surface: three methods are public on the implementation and absent from it, so a guard pointed at the
 * interface is blind to them while looking entirely correct.
 *
 * <p>The documented set is read from the {@code name} field of every fragment under {@code doc/rpc/methods/}
 * — the field is what reaches the published document, so a fragment whose {@code name} disagreed with its
 * filename would otherwise be checked under a name that never ships. Methods deliberately left out of the
 * reference are listed in {@code doc/rpc/undocumented.json}, each with a reason.
 *
 * <p>Both directions are checked: a method exposed but neither documented nor allowlisted fails, and so does
 * a fragment documenting a method the node no longer exposes. The document itself is then assembled and
 * checked in both directions too — no {@code $ref} may dangle and no component may be left unreferenced —
 * because the generator merges fragments without validating references and exits zero either way.
 */
class JsonRpcDocCoverageTest {

    /**
     * A JSON-RPC method name as it travels on the wire: {@code namespace_method}. Also the filter that keeps
     * reflection over {@link Web3RskImpl} from picking up the inherited {@link Object} methods and the
     * {@code start()}/{@code stop()} pair {@code InternalService} contributes.
     */
    private static final Pattern RPC_METHOD_NAME = Pattern.compile("[a-z][a-z0-9]*_[A-Za-z0-9_]+");

    /**
     * The class the JSON-RPC server dispatches against. Kept as a named constant so the one decision that
     * makes or breaks this guard is visible in one place rather than inlined in a stream.
     */
    private static final Class<?> DISPATCH_SURFACE = Web3RskImpl.class;

    private static final String DOC_RPC_DIR = "doc/rpc";
    private static final String METHODS_DIR = "methods";
    private static final String UNDOCUMENTED_FILE = "undocumented.json";

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * Components that reach the published document without anything referencing them, and that predate the
     * guard. They are legal OpenRPC and harmless; they are listed so that a component orphaned from here on
     * is a build failure rather than one more entry in a growing pile.
     */
    private static final Set<String> KNOWN_UNREFERENCED_COMPONENTS = Set.of(
            "#/components/contentDescriptors/Block",
            "#/components/contentDescriptors/Transaction",
            "#/components/schemas/ProofNode",
            "#/components/schemas/Transactions");

    @Test
    void everyExposedMethodIsDocumentedOrAllowlisted() {
        Set<String> exposed = exposedMethods();
        Map<String, Path> documented = documentedMethods();
        Map<String, String> allowlisted = allowlistedMethods();

        Set<String> undocumented = new TreeSet<>(exposed);
        undocumented.removeAll(documented.keySet());
        undocumented.removeAll(allowlisted.keySet());

        Set<String> orphaned = new TreeSet<>(documented.keySet());
        orphaned.removeAll(exposed);

        if (undocumented.isEmpty() && orphaned.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder(String.format(
                "doc/rpc has drifted from %s: %d method(s) exposed, %d documented, %d allowlisted.%n",
                DISPATCH_SURFACE.getSimpleName(), exposed.size(), documented.size(), allowlisted.size()));

        if (!undocumented.isEmpty()) {
            message.append(String.format(
                    "%n%d method(s) exposed but neither documented nor allowlisted. Add a fragment under %s/%s/, "
                            + "or an entry with a reason to %s/%s:%n",
                    undocumented.size(), DOC_RPC_DIR, METHODS_DIR, DOC_RPC_DIR, UNDOCUMENTED_FILE));
            undocumented.forEach(name -> message.append("  - ").append(name).append(System.lineSeparator()));
        }

        if (!orphaned.isEmpty()) {
            message.append(String.format(
                    "%n%d fragment(s) documenting a method %s no longer exposes. Delete or rename them:%n",
                    orphaned.size(), DISPATCH_SURFACE.getSimpleName()));
            orphaned.forEach(name -> message.append("  - ").append(name)
                    .append(" (").append(documented.get(name).getFileName()).append(")")
                    .append(System.lineSeparator()));
        }

        fail(message.toString());
    }

    @Test
    void everyAllowlistEntryIsExposedUndocumentedAndJustified() {
        Set<String> exposed = exposedMethods();
        Set<String> documented = documentedMethods().keySet();
        Map<String, String> allowlisted = allowlistedMethods();

        List<String> problems = new ArrayList<>();

        allowlisted.forEach((name, reason) -> {
            if (reason == null || reason.trim().isEmpty()) {
                problems.add(name + " — carries no reason");
            }
            if (!exposed.contains(name)) {
                problems.add(name + " — is not exposed by " + DISPATCH_SURFACE.getSimpleName() + "; drop the entry");
            }
            if (documented.contains(name)) {
                problems.add(name + " — is both allowlisted and documented; pick one");
            }
        });

        assertTrue(problems.isEmpty(), () -> String.format("%s/%s is stale:%n  - %s",
                DOC_RPC_DIR, UNDOCUMENTED_FILE, String.join(System.lineSeparator() + "  - ", problems)));
    }

    @Test
    void everyDocumentedMethodNameIsClaimedByExactlyOneFragment() {
        Map<String, List<Path>> byName = new TreeMap<>();

        for (Path fragment : methodFragments()) {
            byName.computeIfAbsent(readMethodName(fragment), name -> new ArrayList<>()).add(fragment);
        }

        List<String> duplicates = byName.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " — claimed by " + entry.getValue().stream()
                        .map(path -> path.getFileName().toString())
                        .collect(Collectors.joining(", ")))
                .toList();

        assertTrue(duplicates.isEmpty(), () -> String.format(
                "Two or more fragments under %s/%s declare the same method name, so one of them never reaches "
                        + "the published document:%n  - %s",
                DOC_RPC_DIR, METHODS_DIR, String.join(System.lineSeparator() + "  - ", duplicates)));
    }

    @Test
    void everyReferenceInTheAssembledDocumentResolves(@TempDir Path tempDir) {
        JsonNode document = assembleDocument(tempDir);

        Set<String> unresolved = collectReferences(document);
        unresolved.removeAll(definedComponents(document));

        assertTrue(unresolved.isEmpty(), () -> String.format(
                "The assembled document carries %d reference(s) that resolve to nothing. The generator merges "
                        + "fragments without validating references and exits zero on a document like this, so "
                        + "nothing downstream would catch it:%n  - %s%n%nNote that a component is defined under "
                        + "its declared key, not its filename -- the two disagree at least once under "
                        + "%s/components/schemas/.",
                unresolved.size(), String.join(System.lineSeparator() + "  - ", unresolved), DOC_RPC_DIR));
    }

    @Test
    void noComponentIsLeftUnreferencedByTheAssembledDocument(@TempDir Path tempDir) {
        JsonNode document = assembleDocument(tempDir);

        Set<String> unreferenced = definedComponents(document);
        unreferenced.removeAll(collectReferences(document));
        unreferenced.removeAll(KNOWN_UNREFERENCED_COMPONENTS);

        assertTrue(unreferenced.isEmpty(), () -> String.format(
                "%d component(s) survive in the assembled document that no method and no other component "
                        + "reaches. Deleting a fragment usually orphans the components only it referenced, and "
                        + "those can orphan further components in turn, so this needs a second pass rather than "
                        + "one sweep. Delete them, or add them to KNOWN_UNREFERENCED_COMPONENTS with a "
                        + "reason:%n  - %s",
                unreferenced.size(), String.join(System.lineSeparator() + "  - ", unreferenced)));
    }

    /**
     * The published document, assembled from the fragments exactly as the devportal sync assembles it.
     */
    private static JsonNode assembleDocument(Path tempDir) {
        Path assembled = tempDir.resolve("rskj-openrpc.json");
        new GenerateOpenRpcDoc("0.0.0-test", docRpcDir().toString(), assembled.toString()).call();
        return readJson(assembled);
    }

    /**
     * The reference each component in the document is reachable by. The key a component declares is what
     * {@code $ref} resolves against — not the filename it was read from, and the two do disagree.
     */
    private static Set<String> definedComponents(JsonNode document) {
        Set<String> defined = new TreeSet<>();
        for (String kind : List.of("schemas", "contentDescriptors")) {
            document.path("components").path(kind).fieldNames()
                    .forEachRemaining(name -> defined.add("#/components/" + kind + "/" + name));
        }
        return defined;
    }

    /**
     * Every {@code $ref} value anywhere in the document, however deeply nested and including references a
     * component makes to another component.
     */
    private static Set<String> collectReferences(JsonNode document) {
        Set<String> references = new TreeSet<>();
        collectReferencesInto(document, references);
        return references;
    }

    private static void collectReferencesInto(JsonNode node, Set<String> references) {
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isTextual()) {
                references.add(ref.asText());
            }
            node.fields().forEachRemaining(field -> collectReferencesInto(field.getValue(), references));
        } else if (node.isArray()) {
            node.forEach(element -> collectReferencesInto(element, references));
        }
    }

    /**
     * Every JSON-RPC method the node dispatches, deduplicated across overloads.
     *
     * <p>Reflects over the implementation, not the {@link Web3} interface — see the class Javadoc. If this
     * ever needs changing, confirm the new class against the {@code remoteInterface} argument at the
     * {@code JsonRpcCustomServer} construction site rather than picking the type that reads best.
     */
    private static Set<String> exposedMethods() {
        return Stream.of(DISPATCH_SURFACE.getMethods())
                .map(Method::getName)
                .filter(name -> RPC_METHOD_NAME.matcher(name).matches())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Method name declared by each fragment, mapped back to the file that declares it.
     */
    private static Map<String, Path> documentedMethods() {
        Map<String, Path> documented = new TreeMap<>();
        methodFragments().forEach(fragment -> documented.putIfAbsent(readMethodName(fragment), fragment));
        return documented;
    }

    /**
     * Methods deliberately left out of the published reference, mapped to the reason each was left out.
     */
    private static Map<String, String> allowlistedMethods() {
        Path allowlist = docRpcDir().resolve(UNDOCUMENTED_FILE);
        JsonNode methods = readJson(allowlist).path("methods");
        if (!methods.isArray()) {
            throw new IllegalStateException(allowlist + " must hold a \"methods\" array");
        }

        Map<String, String> allowlisted = new TreeMap<>();
        methods.forEach(entry -> allowlisted.put(entry.path("name").asText(), entry.path("reason").asText(null)));
        return allowlisted;
    }

    private static List<Path> methodFragments() {
        Path methodsDir = docRpcDir().resolve(METHODS_DIR);

        try (Stream<Path> files = Files.list(methodsDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(JsonRpcDocCoverageTest::isJson)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list the fragments under " + methodsDir, e);
        }
    }

    private static boolean isJson(Path path) {
        return path.getFileName().toString().endsWith(".json");
    }

    private static String readMethodName(Path fragment) {
        JsonNode name = readJson(fragment).path("name");
        if (!name.isTextual()) {
            throw new IllegalStateException(fragment + " declares no \"name\", so nothing can be documented by it");
        }
        return name.asText();
    }

    private static JsonNode readJson(Path path) {
        try {
            return JSON_MAPPER.readTree(path.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not parse " + path, e);
        }
    }

    /**
     * The reference the node ships, at the repository root — deliberately not the single-method fixture under
     * {@code src/test/resources/doc/rpc}, which is a generator fixture and would make this guard satisfiable
     * by editing the thing under test. Located by walking up from the working directory, which is the
     * subproject directory under Gradle and the repository root under some IDE runners.
     */
    private static Path docRpcDir() {
        for (Path dir = Paths.get("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(DOC_RPC_DIR);
            if (Files.isRegularFile(candidate.resolve("template.json"))) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not find " + DOC_RPC_DIR + " above " + Paths.get("").toAbsolutePath());
    }
}
