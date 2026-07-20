# `bootstrap-data.bin` format

Specification of the on-disk bootstrap snapshot payload consumed by `BootstrapImporter` and produced by
the `bootstrap-exporter` tool (separate repo, which restates the same constants on its side). This
document is the human-readable contract; the machine-readable constants live in
[`BootstrapV2Format.java`](./BootstrapV2Format.java) and must stay in sync with it.

Two versions exist. **v1** is the legacy format (still fully supported for already-published snapshots);
**v2** is the current format. A reader auto-detects which one it is reading — there is no external version
flag, because the bootstrap index entry carries none.

## Motivation — why v2 exists

The bootstrap snapshot is the supported fast-onboarding path: a node with `import.enabled` reconstructs
its state from a signed `bootstrap-data.zip` (containing `bootstrap-data.bin`) instead of syncing from
peers. A wrong reconstructed state root would silently fork the node, so this is a consensus-critical
path.

The v1 format cannot represent large state. It encodes the whole payload as one nested RLP list, and
rskj's RLP length fields are signed 32-bit `int`, so the total payload is capped at `Integer.MAX_VALUE`
(~2 GiB). The same ceiling appears on the import side three times over: v1 reads the whole `.bin` (and the
whole `.zip` for hashing) into single `byte[]` arrays and decodes the entire payload into in-memory
queues — so peak memory scales with total state size, and a payload past 2 GiB can be neither produced
nor consumed.

A network's serialized state grows over time and eventually exceeds the ~2 GiB ceiling; past that point a
v1 snapshot for that network can be neither exported nor imported. Testnet, being ahead in state size,
crossed it first — which is what forced v2.

**v2** removes the ceiling and makes both export and import bounded-memory, independent of total state
size, while keeping the leaf encoding byte-identical to v1 (only the container framing changes).

## Version detection

The importer dispatches on the **first byte** of `bootstrap-data.bin`:

- A v1 file always starts with an RLP list prefix byte (`0xc0`+).
- A v2 file starts with the ASCII `'R'` (`0x52`) of its magic.

These never collide, so a single peeked byte is sufficient (`BootstrapV2Format.isV2(int)`).

## v2 on-disk layout

```
HEADER
  magic   = "RSKBOOT\n"   (8 bytes, ASCII)
  version = 0x02          (1 byte)

SECTION blocks   (tag 0x01)
SECTION values   (tag 0x03)   <- co-located before nodes (see "Section ordering")
SECTION nodes    (tag 0x02)
SECTION end      (tag 0x00)   <- terminator (a bare tag byte, no chunks)

SECTION =
  [1 byte  section tag]
  zero or more chunks:
      [8-byte big-endian chunk length L]   (0 < L; normally L <= CHUNK_MAX)
      [L bytes: a concatenation of whole canonical RLP elements]
  [8-byte big-endian 0]                    <- end-of-section sentinel
```

All multi-byte integers are big-endian. All lengths are 8-byte (`long`) — this is what lifts the 2 GiB
ceiling, since no single length field is a 32-bit `int` anymore.

### Chunks

A **chunk** holds a whole number of self-delimiting canonical RLP elements — an element never straddles a
chunk boundary. The exporter accumulates encoded elements into a buffer and flushes a chunk once the
buffer crosses `CHUNK_MAX` (256 MiB) *on an element boundary*. The one exception: a single element larger
than `CHUNK_MAX` becomes its own oversized chunk (it cannot be split). Because a chunk is bounded, the
reader loads it into one `byte[]`, decodes it with the ordinary `RLP` element machinery, processes it, and
discards it — so import memory is bounded by roughly `CHUNK_MAX` plus one live `Trie` at a time.

### Sections

Each section is a tag byte, then a sequence of length-prefixed chunks, terminated by a zero-length
sentinel. Three payload sections are defined:

| Tag    | Name       | Contents (per element)                                             |
|--------|------------|--------------------------------------------------------------------|
| `0x01` | blocks     | `LIST[ELEMENT(block), ELEMENT(totalDifficulty)]`                   |
| `0x03` | values     | `RLP.encodeElement(value)` — a trie node's long (non-embedded) value |
| `0x02` | nodes      | `RLP.encodeElement(trie.toMessage())` — a state trie node          |
| `0x00` | end        | terminator; carries no chunks                                      |
| `0x04` | (manifest) | **reserved**, not yet written or read (see "Forward compatibility") |

### Section ordering — the co-location invariant

The **values section precedes the nodes section** deliberately. On import, a node is persisted with
`TrieStore.save`, which resolves the node's long value lazily from the destination store *at save time* —
including long values of embeddable children reached through parent recursion. So every long value a node
can reference must already be in the store before that node is saved.

Because the exporter co-locates all values ahead of the nodes, the importer satisfies this with a **single
streaming pass**: it applies the `values` section (`TrieStore.saveValue`) before it reaches any node in
the `nodes` section. No two-pass read and no temporary side store are needed.

(The `blocks` section is independent and may come first.)

### Leaf encoding is unchanged from v1

The per-element encoding is identical to v1 — nodes, long values, and block/TD tuples all encode exactly
as before. `Trie.fromMessage` and `BlockFactory.decodeBlock` are untouched. Only the *container* around
the elements changed. This is intentional: rskj's RLP `int` length handling is consensus-adjacent, so v2
sidesteps it by chunking under the ceiling rather than widening RLP.

### Forward compatibility

The reader **skips unknown or unwanted section tags** (their chunks are still length-validated, then
discarded). This makes the format additively extensible: a newer exporter can emit an optional section an
older reader does not recognize, and the older reader tolerates it. `TAG_MANIFEST = 0x04` is reserved for
exactly this — a future optional metadata/manifest section (e.g. height, state root, and disk-size hints
for pre-import validation) — but is not written or read today.

### Robustness

Chunk length fields are validated before any allocation: a length must be positive, must not exceed
`2 * CHUNK_MAX` (headroom over the "CHUNK_MAX plus one oversized element" worst case while rejecting a
corrupt length), and must not exceed the bytes actually remaining in the file (catches truncation and
corrupt lengths). A missing end-of-sections marker (EOF before `TAG_END`) is rejected as truncated. The
import also fails fast if the blocks or the nodes section is absent or empty, rather than "succeeding"
with no state and only crashing later at first state access.

## Constants

Defined in [`BootstrapV2Format.java`](./BootstrapV2Format.java):

| Constant       | Value            | Meaning                                             |
|----------------|------------------|-----------------------------------------------------|
| `MAGIC`        | `"RSKBOOT\n"`    | 8-byte ASCII header magic; leading `'R'` marks v2   |
| `VERSION`      | `0x02`           | format version byte                                 |
| `TAG_END`      | `0x00`           | end-of-sections terminator                          |
| `TAG_BLOCKS`   | `0x01`           | blocks section                                      |
| `TAG_NODES`    | `0x02`           | state trie nodes section                            |
| `TAG_VALUES`   | `0x03`           | long trie values section                            |
| `TAG_MANIFEST` | `0x04`           | reserved (unused)                                   |
| `CHUNK_MAX`    | `256 MiB`        | soft chunk-flush threshold (tuning only)            |

`CHUNK_MAX` is a tuning knob (peak memory vs. chunk count), not a hard limit — changing it does not break
compatibility, since chunk lengths are self-describing.

## v1 (legacy) format

v1 remains supported for already-published snapshots and is selected automatically (see "Version
detection"). The whole payload is a single nested RLP list:

```
LIST[
  ELEMENT(blocks-list),   <- LIST of LIST[ELEMENT(block), ELEMENT(td)]
  LIST[ nodes-list, values-list ]
]
```

The importer reads the entire `.bin` into one `byte[]` and decodes it into in-memory queues, then builds
the state. This is why v1 is memory-unbounded and 2 GiB-capped; it is kept only for backward compatibility
and must not be changed.

## Out of scope

- **Widening rskj's RLP `int` length handling.** v2 chunks under the ceiling instead.
- **The bootstrap index / signature scheme.** The sha256-over-`.zip` + signature are unchanged; v2 only
  changes how the `.bin` inside the zip is framed. (The importer now computes the zip hash with a
  streaming digest rather than reading the whole zip into memory, but the hash value and index logic are
  identical.)
