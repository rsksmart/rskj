# Contributing

## I don't want to read this whole thing I just have a question!!!

Please **don't file an issue** to ask a question. You'll get faster results by using the resources below.
* We have a [Developer Web Page](dev.rootstock.io) for resources and documentation about Rootstock. 
* We have a [Open Slack channel](https://open-rsk-dev.slack.com) for technical questions about Rootstock
* We have a [Telegram channel](https://t.me/RSKsmartcontracts) and a [Discord Channel](https://discord.gg/fPerbqcWGE) for general questions or discussions about Rootstock and RIF ecosystem. 

## How to contribute to Rootstock

These are mostly guidelines, not rules. Use your best judgment, and feel free to propose changes to this document in a pull request.

### Code Reviews

Continued code reviews and audits are required for security. As such, we encourage interested security researchers to:

* Review our code, even if no contributions are planned.
* Publish their findings whichever way they choose, even if no particular bug or vulnerability was found. We can all learn from new sets of eyes and benefit from increased scrutiny.

### Code contributions

A code contribution process starts with someone identifying a need for writing code. If you're thinking about making your first contribution, we suggest you take a moment to get in touch and see how your idea fits in the development plan:

* Is it a bug in our [issue tracker](https://github.com/rsksmart/rskj/issues)?
* Is it a novel idea that should be proposed and discussed first?

#### Review process

Once you know what to do, it is important that you provide a full description of the proposed changes. You can also send a draft pull request if you already have code to show.

We make use of GitHub Checks to ensure all changes meet a certain criteria:

1. The `master` branch is protected and only changeable through pull requests
1. All unit tests must pass
1. SonarQube quality gate must be met
1. A project maintainer must approve the pull request
1. An authorized merger must merge the pull request

Since this is a security-sensitive project, we encourage everyone to be proactive and participate in the review process. To help collaboration we propose adhering to these conventions:

* **Request changes** only for correctness and security issues.
* **Comment** when leaving feedback without explicit approval or rejection. This is useful for design and implementation discussions.
* **Approve** when changes look good from a correctness, security, design and implementation standpoint.

All unit and integration tests pass without loss of coverage (e.g can't remove tests without writing equivalent or better ones).

All code paths on new code must be unit tested, including sensible edge cases and expected errors. Exceptions to this rule must be justified (i.e. highly similar paths already tested) in written form in the PR description. 

Any update to a dependency must come with a corresponding PR to the https://github.com/rsksmart/reproducible-builds repository, where it is demonstrated that the downloaded binary code's hash matches that of an independent compilation. The RSK team will audit the new code in order to protect the project security.

New dependencies are discouraged in order to minimize the attack surface. However, when the problem requires it, the new dependency will follow the same procedure as an update to an existing one dependency.

In order to ease review, it is expected that the code diff is maintained to a minimum. This includes things like not changing unrelated files, not changing names or reordering code when there isn't an evident benefit.

When automatic code quality and security checks are ready in the pipeline for external PRs, then the PR must pass all PR validations including code coverage (Sonar), code smells (Sonar), Security advisories (Sonar, LGTM).

## Styleguides

### Pull request etiquette

* Separate your changes into multiple commits
* If your pull request gets too big, try to split it
* Each commit should at least compile, and ideally pass all unit tests
* Avoid merge commits, and always rebase your changes on top of `master`
* Follow the [Java](#java-styleguide) styleguide

### Java Styleguide

#### General

**Naming**

* **Identifiers** use only ASCII letters and digits.
* **Package names** are all lowercase, with consecutive words simply concatenated together (no underscores).
* **Class names** are written in `UpperCamelCase`.
* **Method names** are written in `lowerCamelCase`.
* **Non-constant** field names (static or otherwise) are written in `lowerCamelCase`.
* **Parameter names** are written in `lowerCamelCase`.
* **Local variable names** are written in `lowerCamelCase`.
* **Constant names** use `CONSTANT_CASE`: all uppercase letters, with each word separated from the next by a single underscore. But what is a constant, exactly?

Constants are static final fields whose contents are deeply immutable and whose methods have no detectable side effects. This includes primitives, Strings, immutable types, and immutable collections of immutable types. If any of the instance's observable state can change, it is not a constant. Merely intending to never mutate the object is not enough.


**Prefer readonly accessors and injection over setters**

An object’s dependencies might be injected during construction or later on with setters.
Construction injection is preferred over setters because the object can never be in an inconsistent state as long as it was properly injected.

Setters on the other hand leave the door open to `NullPointerException`s, prevent using `private final` fields, and make it harder to reason about the dependency graph.

```java
public class BlockChainImpl {
    private final BlockRecorder blockRecorder;

    public BlockChainImpl(BlockRecorder blockRecorder, ...) {
        this.blockRecorder = blockRecorder;
        // ...
    }

    public ImportResult tryToConnect(Block block) {
        // ...
        blockRecorder.writeBlock(block);
}
```


**Prefer private final fields for dependencies**

`private final` fields guarantee they are only assigned once, at object construction time.
This ensures that the injected dependency will always be in a consistent state as long as the object was properly injected.

```java
public class SyncProcessor implements SyncEventsHandler {
    private final RskSystemProperties config;
    private final Blockchain blockchain;
```


**Avoid `@VisibleForTesting`**

Breaking a class encapsulation just for testing is a sign that we either shouldn’t be testing that, or that we should do it differently. Think about the following questions first:

* Is this just an implementation detail?
* Can you perform the same validations on an injected dependency? (e.g. a mock)


**Prefer `Optional<T>` over `null`**

Unlike with `null`s, you’re forced to check for existence before using an `Optional` value.

```java
public Optional<byte[]> getReturnDataBufferData(DataWord off, DataWord size) {
    long endPosition = (long) off.intValueSafe() + size.intValueSafe();
    if (endPosition > getReturnDataBufferSizeI()) {
        return Optional.empty();
    }
```


**Always annotate `@Nullable` methods**

> I call it my billion-dollar mistake. It was the invention of the `null` reference in 1965 - Tony Hoare.

`NullPointerException`s have been the source of pain in many Java projects.
Even though `Optional` is the modern Java pattern to deal with optional values, `null`s are still around and are the right choice in some situations.

**If you absolutely need to return `null`**, always annotate the method with `@Nullable`.
Everywhere else, you should assume objects aren’t `null`.

```java
@Nullable
public static Coin deserializeCoin(byte[] data) {
    if (data == null || data.length == 0) {
        return null;
    }
```


**Check not null preconditions**

A common pattern is to use `Objects::requireNonNull` on constructor parameters. Current rskj code doesn’t do this for historical reasons (e.g. test code passes `null`s), but we should do it everywhere possible.

```java
public PendingStateImpl(
        Blockchain blockChain,
        BlockStore blockStore,
        RskSystemProperties config, ...) {
    this.blockChain = Objects.requireNonNull(blockChain);
    this.blockStore = Objects.requireNonNull(blockStore, "null is bad");
    this.config = Objects.requireNonNull(config, () -> "don’t " + "use it");
    ...
```


**Unused code should be deleted**

Unless there's a good reason to keep it around, in which case you should add comments explaining why. Comments are not needed in cases in which the underlying reason for having the (unused) method is trivial enough (e.g., a method `add` on a `Calculator` class).

This guideline includes code with tests but no usages in production.


#### Formatting

* **All** control structures should use curly braces
* A **closing** curly brace should be located at the beginning of a **new line**
* An **opening** curly brace should be located at the end of the last line
* A **closing** curly brace and the next `else`, `catch` and `finally` keywords should be located on the **same line**
* When a method or constructor **declaration has many parameters**, you should prefer starting a new line and indenting all parameters with 8 spaces
* When a method or constructor **invocation has many parameters**, you should prefer starting a new line and indenting all parameters with 8 spaces

For automatic formatting in IntelliJ IDEA, the following `xml` files can be placed in the `.idea/codeStyles` folder in the root folder of the project:

`codeStyleConfig.xml`:

```xml
<component name="ProjectCodeStyleConfiguration">
  <state>
    <option name="USE_PER_PROJECT_SETTINGS" value="true" />
  </state>
</component>
```

`Project.xml`:

```xml
<component name="ProjectCodeStyleConfiguration">
  <code_scheme name="Project" version="173">
    <codeStyleSettings language="JAVA">
      <option name="ALIGN_MULTILINE_PARAMETERS_IN_CALLS" value="true" />
      <option name="CALL_PARAMETERS_WRAP" value="5" />
      <option name="CALL_PARAMETERS_LPAREN_ON_NEXT_LINE" value="true" />
      <option name="CALL_PARAMETERS_RPAREN_ON_NEXT_LINE" value="true" />
      <option name="METHOD_PARAMETERS_WRAP" value="5" />
      <option name="METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE" value="true" />
      <option name="IF_BRACE_FORCE" value="3" />
      <option name="DOWHILE_BRACE_FORCE" value="3" />
      <option name="WHILE_BRACE_FORCE" value="3" />
      <option name="FOR_BRACE_FORCE" value="3" />
    </codeStyleSettings>
  </code_scheme>
</component>
```
