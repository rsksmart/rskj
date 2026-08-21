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
import org.ethereum.rpc.parameters.BlockRefParam;
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
 * reference are listed in {@code doc/rpc/undocumented.json}, each with a reason and the basis that reason
 * rests on: {@code configuration} for a method shipped node configuration does not serve, {@code editorial}
 * for one left out by judgement. The basis is a label and nothing more — the guard checks that every entry
 * declares a recognised one, never that the reason written beside it is still true.
 *
 * <p>Both directions are checked: a method exposed but neither documented nor allowlisted fails, and so does
 * a fragment documenting a method the node no longer exposes. The document itself is then assembled and
 * checked in both directions too — no {@code $ref} may dangle and no component may be left unreferenced —
 * because the generator merges fragments without validating references and exits zero either way.
 *
 * <p>Each fragment is separately held to carrying at least one example, and to every example passing as many
 * parameters as the fragment declares — coverage and arity only, never that an example is realistic or that
 * it shows the branch of a union a reader most needs.
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

    /**
     * The bases an allowlist entry may declare. {@code configuration} says shipped node configuration does
     * not serve the method, so a configuration change could invalidate the entry; {@code editorial} says the
     * method was left out by judgement, which no configuration change can touch. The guard checks the label
     * is present and recognised and stops there: deliberately, nothing here resolves the node configuration
     * to confirm a {@code configuration} entry, because doing so would encode which node the reference
     * documents into this class and turn the next re-litigation of that question into a code change rather
     * than a data edit.
     */
    private static final Set<String> ALLOWLIST_BASES = Set.of("configuration", "editorial");

    /**
     * The node-side type of a block reference: the parameter that accepts a tag, a block number in hex or in
     * decimal, <em>and</em> the {@code {"blockHash": ...}} object. Methods are found by reflecting for this
     * type rather than by listing their names, so a method that starts or stops taking a block reference is
     * carried into the check by the same reflection the rest of this guard runs on.
     */
    private static final Class<?> BLOCK_REFERENCE_PARAMETER = BlockRefParam.class;

    /**
     * The schema for the object form of a block reference, and the part a narrowly documented parameter drops.
     * The object form has been in the fragments since 2022; a method documented without it tells the reader
     * the node will reject a block hash it in fact accepts.
     */
    private static final String BLOCK_REFERENCE_OBJECT = "#/components/schemas/BlockRef";

    /**
     * The one descriptor every block-reference parameter is meant to resolve through, named here only so the
     * failure message can point at it.
     */
    private static final String BLOCK_REFERENCE_DESCRIPTOR = "#/components/contentDescriptors/BlockRefOrNumberOrTag";

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
        Map<String, AllowlistEntry> allowlisted = allowlistedMethods();

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
        Map<String, AllowlistEntry> allowlisted = allowlistedMethods();

        List<String> problems = new ArrayList<>();

        allowlisted.forEach((name, entry) -> {
            String reason = entry.reason();
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

    /**
     * A reader auditing the allowlist needs to know which entries a configuration change could invalidate and
     * which rest on judgement no configuration change can touch. The basis is that label, and it is checked
     * for presence and for being one of {@link #ALLOWLIST_BASES} — not against the resolved node
     * configuration, which is a deliberate limit rather than an omission; see {@link #ALLOWLIST_BASES}.
     */
    @Test
    void everyAllowlistEntryDeclaresARecognisedBasis() {
        List<String> problems = allowlistedMethods().entrySet().stream()
                .filter(entry -> !isRecognisedBasis(entry.getValue().basis()))
                .map(entry -> isBlank(entry.getValue().basis())
                        ? entry.getKey() + " — declares no basis"
                        : entry.getKey() + " — declares basis \"" + entry.getValue().basis() + "\"")
                .toList();

        assertTrue(problems.isEmpty(), () -> String.format(
                "%d entr(ies) in %s/%s declare no basis, or one that is not recognised. Every entry must carry "
                        + "a \"basis\" of %s, matching the reason already written on it:%n  - %s",
                problems.size(), DOC_RPC_DIR, UNDOCUMENTED_FILE,
                String.join(" or ", new TreeSet<>(ALLOWLIST_BASES)),
                String.join(System.lineSeparator() + "  - ", problems)));
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

    /**
     * A fragment with no example, or with an example passing a different number of arguments than the
     * fragment declares parameters, misleads exactly the reader who skims examples instead of schemas. Both
     * halves held across all 54 fragments when checked by hand; they are asserted here so they stop being
     * facts someone has to re-verify.
     *
     * <p>What this buys is coverage and arity and nothing beyond them. It does not hold that an example is
     * realistic, nor that it demonstrates the branch of a union a reader most needs to see: every one of the
     * 54 fragments passed this assertion while not one of them showed the object form of a block reference.
     */
    @Test
    void everyFragmentCarriesAnExampleMatchingItsDeclaredArity() {
        List<String> problems = new ArrayList<>();

        for (Path fragment : methodFragments()) {
            JsonNode method = readJson(fragment);
            int declared = method.path("params").size();
            JsonNode examples = method.path("examples");

            if (!examples.isArray() || examples.isEmpty()) {
                problems.add(fragment.getFileName() + " — carries no example; add one to \"examples\" passing "
                        + declared + " parameter(s)");
                continue;
            }

            for (JsonNode example : examples) {
                int passed = example.path("params").size();
                if (passed != declared) {
                    problems.add(fragment.getFileName() + " — example \"" + example.path("name").asText()
                            + "\" passes " + passed + " parameter(s) but the fragment declares " + declared
                            + "; give the example a value for every declared parameter, or drop the parameter");
                }
            }
        }

        assertTrue(problems.isEmpty(), () -> String.format(
                "%d fragment(s) under %s/%s carry no example, or an example that disagrees with the parameters "
                        + "it is meant to illustrate. A reader who skims examples rather than schemas reads the "
                        + "example as the whole call:%n  - %s",
                problems.size(), DOC_RPC_DIR, METHODS_DIR,
                String.join(System.lineSeparator() + "  - ", problems)));
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
     * A parameter the node reads as a block reference must be documented as one. Four of the five methods
     * taking {@link BlockRefParam} resolved to a schema carrying the object form and one declared
     * hex-number-or-tag only, so its reference stated the node would reject a block hash it accepts — a gap a
     * user hit despite the object form being documented elsewhere since 2022.
     *
     * <p>The methods are found by reflection over {@link #DISPATCH_SURFACE}, not from a list kept here, and
     * each is resolved through whatever its fragment declares: a shared descriptor, an inline union, or a
     * chain of {@code $ref}s between them. That is what makes an inline redefinition of the parameter
     * answerable to the same check as a reference to the shared descriptor.
     */
    @Test
    void everyMethodTakingABlockReferenceDocumentsTheObjectForm(@TempDir Path tempDir) {
        Set<String> takingBlockReference = methodsTakingABlockReference();
        assertTrue(!takingBlockReference.isEmpty(), () -> String.format(
                "No method on %s takes %s, so this guard checks nothing. Either the parameter type was renamed "
                        + "and BLOCK_REFERENCE_PARAMETER needs to follow it, or block references are no longer "
                        + "a parameter type and this test should go.",
                DISPATCH_SURFACE.getSimpleName(), BLOCK_REFERENCE_PARAMETER.getSimpleName()));

        JsonNode document = assembleDocument(tempDir);
        Map<String, JsonNode> documented = documentedMethodsInDocument(document);

        List<String> problems = new ArrayList<>();
        for (String name : takingBlockReference) {
            JsonNode method = documented.get(name);
            if (method == null) {
                // Allowlisted rather than documented; everyExposedMethodIsDocumentedOrAllowlisted owns that case.
                continue;
            }
            boolean documentsObjectForm = false;
            for (JsonNode param : method.path("params")) {
                if (resolvesTo(document, param, BLOCK_REFERENCE_OBJECT, new TreeSet<>())) {
                    documentsObjectForm = true;
                    break;
                }
            }
            if (!documentsObjectForm) {
                problems.add(name + " — no parameter of it resolves to " + BLOCK_REFERENCE_OBJECT);
            }
        }

        assertTrue(problems.isEmpty(), () -> String.format(
                "%d documented method(s) take %s, which accepts a block hash object, but document no parameter "
                        + "that resolves to %s. A reader is told the node will reject an input it accepts. Point "
                        + "the parameter at %s, the descriptor the other block-reference methods share, rather "
                        + "than widening a second definition of it:%n  - %s",
                problems.size(), BLOCK_REFERENCE_PARAMETER.getSimpleName(), BLOCK_REFERENCE_OBJECT,
                BLOCK_REFERENCE_DESCRIPTOR, String.join(System.lineSeparator() + "  - ", problems)));
    }

    /**
     * Every JSON-RPC method the node dispatches that takes a block reference, deduplicated across overloads.
     */
    private static Set<String> methodsTakingABlockReference() {
        return Stream.of(DISPATCH_SURFACE.getMethods())
                .filter(method -> RPC_METHOD_NAME.matcher(method.getName()).matches())
                .filter(method -> Stream.of(method.getParameterTypes()).anyMatch(BLOCK_REFERENCE_PARAMETER::equals))
                .map(Method::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * The methods of the assembled document, by the name each declares.
     */
    private static Map<String, JsonNode> documentedMethodsInDocument(JsonNode document) {
        Map<String, JsonNode> methods = new TreeMap<>();
        document.path("methods").forEach(method -> methods.putIfAbsent(method.path("name").asText(), method));
        return methods;
    }

    /**
     * Whether {@code node} reaches {@code target}, following every {@code $ref} it carries through the
     * components of the document. {@code visited} keeps a component that references itself, directly or in a
     * cycle, from looping forever.
     */
    private static boolean resolvesTo(JsonNode document, JsonNode node, String target, Set<String> visited) {
        if (node == null) {
            return false;
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                if (resolvesTo(document, element, target, visited)) {
                    return true;
                }
            }
            return false;
        }
        if (!node.isObject()) {
            return false;
        }

        JsonNode ref = node.get("$ref");
        if (ref != null && ref.isTextual()) {
            String reference = ref.asText();
            if (target.equals(reference)) {
                return true;
            }
            if (visited.add(reference) && resolvesTo(document, componentAt(document, reference), target, visited)) {
                return true;
            }
        }

        for (JsonNode value : node) {
            if (resolvesTo(document, value, target, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The component a {@code #/components/<kind>/<name>} reference points at, or {@code null} if the document
     * defines none — a dangling reference, which everyReferenceInTheAssembledDocumentResolves reports.
     */
    private static JsonNode componentAt(JsonNode document, String reference) {
        String[] segments = reference.split("/");
        if (segments.length != 4 || !"#".equals(segments[0]) || !"components".equals(segments[1])) {
            return null;
        }
        JsonNode component = document.path("components").path(segments[2]).get(segments[3]);
        return component == null || component.isNull() ? null : component;
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

    private static boolean isRecognisedBasis(String basis) {
        return !isBlank(basis) && ALLOWLIST_BASES.contains(basis);
    }

    /**
     * An absent field and an empty one are the same mistake here, and they do not arrive the same way: a
     * missing {@code basis} reads back as {@code ""} rather than {@code null}, because {@code path()} answers
     * a missing node and its {@code asText(default)} ignores the default. Both are reported as no basis at
     * all, which is what the maintainer who left it out needs to read.
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * An entry in the allowlist: why the method is absent from the reference, and what that reason rests on.
     */
    private record AllowlistEntry(String reason, String basis) {
    }

    /**
     * Methods deliberately left out of the published reference, mapped to the entry that excuses each.
     */
    private static Map<String, AllowlistEntry> allowlistedMethods() {
        Path allowlist = docRpcDir().resolve(UNDOCUMENTED_FILE);
        JsonNode methods = readJson(allowlist).path("methods");
        if (!methods.isArray()) {
            throw new IllegalStateException(allowlist + " must hold a \"methods\" array");
        }

        Map<String, AllowlistEntry> allowlisted = new TreeMap<>();
        methods.forEach(entry -> allowlisted.put(entry.path("name").asText(),
                new AllowlistEntry(entry.path("reason").asText(null), entry.path("basis").asText(null))));
        return allowlisted;
    }

    /**
     * The fragments under {@code doc/rpc/methods/}, as {@link GenerateOpenRpcDoc} counts them -- what the
     * generator assembles is what ships, so this guard reads from that definition instead of keeping a
     * second one that could drift away from it.
     */
    private static List<Path> methodFragments() {
        Path methodsDir = docRpcDir().resolve(METHODS_DIR);

        try (Stream<Path> files = Files.list(methodsDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(GenerateOpenRpcDoc::isFragment)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list the fragments under " + methodsDir, e);
        }
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
