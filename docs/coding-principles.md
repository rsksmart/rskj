# Rootstock coding principles

These guidelines are inspired by Clean Code (Robert C. Martin), but are adapted to Rootstock’s blockchain and security requirements.

They are guidelines, not dogma. Apply them with judgment. In security-sensitive or money-related code, correctness and behavior preservation are more important than stylistic cleanup.

## General principles

* Code is read far more often than it is written. Optimize for readability and maintainability.
* Leave touched code in a better state than you found it.
* Keep consistency with the rest of the codebase whenever possible.
* Small improvements are valuable: rename unclear variables, remove duplication, simplify conditions, and delete dead code when safe.
* Clarity is more important than cleverness.
* Avoid introducing unnecessary dependencies.
* Prefer boring, obvious code over surprising code.
* Code should look like it was written by someone who cared.
* Keep source files clean, well organized, and free of unnecessary whitespace.

## Naming

Names are one of the most important parts of readability.

* Use intention-revealing names.
* Prefer names that reveal intent without requiring comments.
* Names should describe domain concepts, not implementation details.
* Include units in names when working with monetary values, time values, or measurements, such as amountInSatoshis, amountInWei, amountInRBTC, timeoutMillis, and blockHeight.
* Never rely on implicit monetary units.
* Avoid vague suffixes such as Data, Info, Manager, Processor, or Helper unless they accurately describe the abstraction.
* Avoid names that differ only in small or unclear ways.
* Prefer searchable names over overly short names.
* Prefer pronounceable names.
* Use one word for one concept and use it consistently.
* Avoid using the same word for different concepts.
* Do not add unnecessary context or prefixes.
* Classes and objects should usually have noun or noun-phrase names.
* Methods should usually have verb or verb-phrase names.
* Avoid cute names. Choose clarity over entertainment value.
* Reevaluate names as the code evolves.

## Functions

Functions are the first line of organization in a program.

* Functions should do one thing and do it well.
* Prefer focused functions with a clear responsibility and consistent level of abstraction.
* Prefer descriptive function names over short names.
* Avoid deeply nested conditionals; use guard clauses when appropriate.
* Keep indentation shallow when practical.
* Prefer small parameter lists. If a function consistently requires many related parameters, consider introducing a parameter object.
* Avoid boolean selector arguments. Split behavior into separate methods when practical.
* Avoid other selector arguments, such as enums or integers, when they only choose between different behaviors that should be separate functions.
* Functions should either perform an action or return information, but avoid mixing both responsibilities.
* Avoid hidden side effects.
* Avoid output arguments. If state must change, prefer changing the owning object.
* Keep variables close to where they are used.
* Do not assign constants to local variables unless it clearly improves readability.
* Replace magic numbers with named constants.
* Remove unused methods.

## Classes

* Classes should have a single responsibility and a single reason to change.
* Prefer many focused classes over large multi-purpose classes.
* Avoid "god classes".
* Class names with vague words such as Manager, Processor, Super, Data, or Info may indicate unclear responsibilities.
* Prefer composition over inheritance.
* Base classes should not know implementation details of derived classes.
* Keep interfaces small and focused.
* Keep coupling low by limiting what each class exposes.
* Hide data, utility functions, constants, and temporary details when possible.
* Avoid weakening encapsulation just to make testing easier unless there is no better option.
* Public constants should come first, followed by private static fields, then private instance fields.
* Public methods should generally appear before private helpers.
* Private helpers may be placed near the public method that uses them to preserve readability.

## Abstractions

* Eliminate duplication whenever practical.
* Create abstractions only when they simplify the code, remove duplication, or represent a stable domain concept.
* Do not introduce abstractions that are more complicated than the duplicated code they replace.
* Introduce abstractions to represent stable domain concepts, not merely to reduce line count.
* Separate high-level business concepts from low-level implementation details.
* Avoid mixing different abstraction levels in the same function.
* Consider polymorphism before introducing large switch statements.
* Prefer explicit code over premature abstraction in security-sensitive paths.
* Do not hide important protocol, consensus, signing, or transaction-building behavior behind vague abstractions.

## Objects and data structures

* Think carefully about how data should be represented.
* Do not add getters and setters automatically.
* Objects should usually hide data behind behavior.
* Data structures may expose data when they are intentionally simple carriers.
* Avoid hybrid structures that expose data while also pretending to own complex behavior.
* Follow the Law of Demeter: avoid reaching through one object to manipulate another object's internals.
* Tell objects what to do instead of asking them for their internals and doing the work elsewhere.

## Error handling

* Error handling should not obscure business logic.
* Prefer exceptions over error codes.
* Use informative exception messages.
* Define exception types around the caller's needs.
* Do not return null when a safer alternative exists.
* Do not pass null unless the API explicitly requires it and the behavior is documented.
* Leave the system in a consistent state after failures.
* Never log secrets, private keys, credentials, or sensitive transaction data.
* Extract complex try/catch bodies when it improves readability.
* Robustness and readability are not conflicting goals.

## Comments

* Prefer self-explanatory code over explanatory comments.
* Comments should explain intent, rationale, protocol constraints, security considerations, or non-obvious decisions.
* Comments should say what the code cannot say for itself.
* Do not add comments that merely restate the code.
* Do not use comments to compensate for unclear code when the code can be improved instead.
* Remove obsolete comments and commented-out code.
* Do not add author attribution comments; Git history already provides this information.
* Do not add journal-style comments describing historical changes.
* A useful comment should be accurate, clear, and maintained with the code.

## Testing

* New behavior should include tests.
* Tests should be readable and easy to maintain.
* Prefer Arrange-Act-Assert structure.
* Test observable behavior rather than implementation details.
* Cover success paths, failure paths, edge cases, and boundary conditions.
* Tests must be independent and runnable in any order.
* Do not disable failing tests.
* Do not skip trivial tests when the behavior could break.
* When fixing a bug, add focused tests around the bug and nearby boundary conditions.
* Test code should be clean, but it does not need to be optimized like production code.
* Use helper methods or fixtures when they make tests easier to read.
* Avoid excessive test cleverness.

## Pull requests and refactoring

* Keep changes focused and reviewable.
* Avoid large refactorings unrelated to the goal of the change.
* Do not mix formatting-only changes with behavioral changes.
* If a cleanup would significantly obscure the purpose of a PR, perform it separately.
* AI-generated suggestions are not automatically improvements. Evaluate them critically.
* Refactors must improve clarity, maintainability, or correctness.
* Do not introduce abstractions, indirection, or code movement without a clear benefit.
* Sometimes the right choice is to stop optimizing and move forward.

## Java-specific guidance

* Follow standard Java naming conventions.
* Prefer enums over integer constants when representing a closed set of values.
* Avoid inheriting constants.
* Prefer static imports for constants when it improves readability.
* Avoid wildcard imports; prefer explicit imports to keep diffs predictable and consistent with the existing codebase.
* Prefer constructor injection and immutable fields for new code when practical.
* Validate required constructor arguments.
* Avoid nullable returns where Optional<T> would make behavior clearer.
