# Sealed versioning for BlockHeaderExtension (RSKIP-351)

RSKIP-351 block header extensions are consensus-versioned: the set of valid
extension versions is fixed by hard forks, not open-ended at runtime. We
therefore make `BlockHeaderExtension` a **sealed interface** (`permits` V1, V2)
and move serialization out of the interface into a single
`BlockHeaderExtensionCodec`. Encoding is one polymorphic expression (the
version byte comes from `getVersion()` — no switch, no `instanceof` guard);
decoding is one switch on the version byte whose default case preserves
today's exact `"Unknown extension with version: "` rejection. Adding a version
means adding one final class, one `permits` entry, and one decode case: the
compiler forces the `permits` change, and the one obvious place to touch stays
consensus-reviewed instead of silently runtime-extensible.

## Considered options

- **Instanceof chains in interface statics (status quo).** Closed for
  modification: every new version edits the interface, and the `toEncoded`
  guard forces every future extension to *inherit* from
  `BlockHeaderExtensionV1`.
- **Version-to-codec registry (`Map<Byte, ...>`).** Truly open/closed at
  runtime — but open-ended extensibility is a liability for consensus-fixed
  versions, and registration becomes a hidden classloading dependency.
- **Sealed interface + codec switch (chosen).** The version set is
  consensus-fixed anyway; the compiler must force whoever adds V3 to handle
  every site, and a missed case is a compile error, not a runtime surprise.

## Consequences

- Mockito's inline mock maker cannot mock a sealed interface (it synthesizes an
  implementor, which `permits` forbids — verified empirically against
  Mockito 5.12.0 / Byte Buddy 1.14.15). Tests must mock the concrete version
  classes or use real instances.
- Wire behavior is byte-for-byte preserved: encodings, hashes, and the exact
  rejection messages for unknown versions are pinned by characterization
  tests captured before the refactor.
- The interface carries the surface the header seam needs —
  `getLogsBloom`/`getTxExecutionSublistsEdges` accessors and the `with...`
  copy methods — so `BlockHeaderV1`'s accessors (inherited by V2 headers)
  dispatch polymorphically instead of re-introducing `instanceof` chains.
- V1/V2 are final immutable value objects: arrays are copied once at
  construction and getters expose them directly; the scattered per-set/get
  defensive copies are gone.
- One deliberate deviation from byte-for-byte preservation (approved):
  `setExtension` now rejects a mismatched extension version with
  `IllegalArgumentException` (null included, so it routes into the existing
  `IllegalArgumentException` handling). Previously a V1 header silently
  accepted a V2 extension only because V2 inherited from V1. The sync
  states route the rejection into their existing invalid-body handling
  (`handleInvalidBody` / INVALID_MESSAGE peer scoring) instead of letting
  it escape to `NodeMessageHandler`'s broad catch.
