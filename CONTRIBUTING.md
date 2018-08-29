# Contributing

## I don't want to read this whole thing I just have a question!!!

Please **don't file an issue** to ask a question. You'll get faster results by using the resources below.

* We have a [Gitter channel](https://gitter.im/rsksmart/rskj) for technical questions about RSK
* We have a [Telegram channel](https://t.me/RSKsmartcontracts) for general questions or discussions about RSK

## How to contribute to RSK

These are mostly guidelines, not rules. Use your best judgment, and feel free to propose changes to this document in a pull request.

### Did you find a bug?

* **Do not open up a GitHub issue if the bug is a security vulnerability**, and instead contact us at <security@rsk.co> or refer to our [Bug Bounty Program](https://bounty.rsk.co/).

* **Ensure the bug was not already reported** by searching on GitHub under [Issues](https://github.com/rsksmart/rskj/issues).

* If you're unable to find an open issue addressing the problem, [open a new one](https://github.com/rsksmart/rskj/issues/new). Be sure to include a **title and clear description**, as much relevant information as possible, and a **code sample** or an **executable test case** demonstrating the expected behavior that is not occurring.

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
