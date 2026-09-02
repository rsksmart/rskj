# Bootstrap a node using Import Sync

Import sync bootstraps a new node from **published bootstrap data** — a pre-synchronized database, signed by its publishers — instead of syncing the whole chain from peers. RSKj downloads the bootstrap data, checks it against signatures from keys it already trusts, loads it into a fresh database, and then continues syncing normally from that point.

It is a **one-time operation you run once, on a new node**, before putting it into service.

:::danger[Import sync erases the database every time it runs]

Enabling import does not mean "import if needed". Every time a node starts with import enabled, it **deletes its database directory** and downloads the bootstrap data again — even if the node was fully synced a minute earlier.

Run it as a one-off from the command line, and never leave `database.import.enabled = true` in a configuration file. A node whose config has it enabled will wipe itself on every restart, including restarts you did not ask for, such as a reboot or a crash recovery.

:::

## Who this page is for

These instructions cover running the node from the **JAR file**, on **Mainnet** or **Testnet**.

| | Import sync |
|---|---|
| JAR (`java -cp … co.rsk.Start`) | Supported — this page |
| Docker | Not documented. See [the note below](#docker-and-the-ubuntu-package) |
| Ubuntu package / `systemd` service | Not documented. See [the note below](#docker-and-the-ubuntu-package) |
| Regtest | Not supported. No bootstrap data is published for Regtest, and because the database is erased before the import is attempted, running `--import` there deletes the Regtest database and then fails |

## Before you start

- **Java 17 JDK** and **RSKj `VETIVER-9.0.4` or later** — see [Setup node using Java](/node-operators/setup/installation/java/). Earlier versions may not be able to read the published bootstrap data.
- **Disk for the database.** Size the data directory for a full node, per the [minimum requirements](/node-operators/setup/requirements/) — not for the size of the download.
- **Disk for temporary files.** RSKj downloads the bootstrap archive and extracts it into the JVM's temporary directory: `/tmp` on Linux, a per-user directory under `/var/folders` on macOS. To see the exact path yours will use, run `java -XshowSettings:properties -version 2>&1 | grep java.io.tmpdir`. Both files exist there at the same time. The extracted contents are larger than the archive, so allow **about three times the size of the bootstrap archive** in temporary space, on top of the database itself. To check that size before committing to the download, see [Check where import sync will land you](#check-where-import-sync-will-land-you).

## How bootstrap data is trusted

Bootstrap data is published by several independent signers. RSKj ships, per network, the source URL and the **public keys** it will accept, so a default installation needs no configuration to use import sync.

| Network | `database.import.url` |
| --- | --- |
| Mainnet | `https://import.mainnet.rskcomputing.net/dbs/mainnet/` |
| Testnet | `https://import.testnet.rskcomputing.net/dbs/testnet/` |

Each signer publishes its own index under that URL, at a path named for its public key:

<Tabs>
  <TabItem value="index-mainnet" label="Mainnet" default>
    ```text
    https://import.mainnet.rskcomputing.net/dbs/mainnet/<TRUSTED-PUBLIC-KEY>/index.json
    ```
  </TabItem>
  <TabItem value="index-testnet" label="Testnet">
    ```text
    https://import.testnet.rskcomputing.net/dbs/testnet/<TRUSTED-PUBLIC-KEY>/index.json
    ```
  </TabItem>
</Tabs>

Every entry in an index carries a block `height`, the path to the archive, its `hash`, and the signer's signature over that entry.

RSKj will only import bootstrap data that **enough trusted signers agree on**: the same height, with the same hash, correctly signed by each. The threshold is a majority of the keys you have configured, and never fewer than two — with the three keys shipped for each network, that is two. A height offered by too few signers is ignored, however recent it is. Among the heights that meet that bar, RSKj takes the highest.

The trusted keys are long, and they are the one thing worth taking from the JAR you are about to run rather than from this page, since that is the authoritative answer for your version:

<Tabs>
  <TabItem value="mainnet-keys" label="Mainnet" default>
    ```shell
    unzip -p <PATH-TO-THE-RSKJ-JAR> config/main.conf | sed -n '/import {/,/}/p'
    ```
  </TabItem>
  <TabItem value="testnet-keys" label="Testnet">
    ```shell
    unzip -p <PATH-TO-THE-RSKJ-JAR> config/testnet.conf | sed -n '/import {/,/}/p'
    ```
  </TabItem>
</Tabs>

## Check where import sync will land you

**Import sync does not leave you at the chain tip.** It leaves you at the newest height that enough signers agree on, and the node then syncs the remaining blocks from peers in the normal way. Depending on how recent the published data is, that remainder can still be substantial: import sync shortens the initial sync, it does not remove it.

You can see exactly which height you would land on before downloading anything. Substitute the three trusted keys for your network, then:

<Tabs>
  <TabItem value="land-mainnet" label="Mainnet" default>
    ```shell
    mkdir -p ~/rskj-index/mainnet && cd ~/rskj-index/mainnet

    URL=https://import.mainnet.rskcomputing.net/dbs/mainnet/

    for KEY in \
      <TRUSTED-KEY-1> \
      <TRUSTED-KEY-2> \
      <TRUSTED-KEY-3>
    do
      curl -sS -o "index-$KEY.json" "$URL$KEY/index.json"
    done

    jq -s '(((length / 2) | floor) + 1 | if . < 2 then 2 else . end) as $required
           | [.[].dbs[] | {height, hash}] | group_by(.height)
           | map({height: .[0].height, agreeing: ([group_by(.hash)[] | length] | max)})
           | map(select(.agreeing >= $required)) | max_by(.height)' index-*.json
    ```
  </TabItem>
  <TabItem value="land-testnet" label="Testnet">
    ```shell
    mkdir -p ~/rskj-index/testnet && cd ~/rskj-index/testnet

    URL=https://import.testnet.rskcomputing.net/dbs/testnet/

    for KEY in \
      <TRUSTED-KEY-1> \
      <TRUSTED-KEY-2> \
      <TRUSTED-KEY-3>
    do
      curl -sS -o "index-$KEY.json" "$URL$KEY/index.json"
    done

    jq -s '(((length / 2) | floor) + 1 | if . < 2 then 2 else . end) as $required
           | [.[].dbs[] | {height, hash}] | group_by(.height)
           | map({height: .[0].height, agreeing: ([group_by(.hash)[] | length] | max)})
           | map(select(.agreeing >= $required)) | max_by(.height)' index-*.json
    ```
  </TabItem>
</Tabs>

This reproduces the node's own selection rule and prints the height it would choose, along with how many signers agree on it. The threshold is derived from the number of index files you fetched, so the query stays correct if you configure a different set of keys.

Each network gets its own directory because the final `jq` reads every `index-*.json` it finds. Index files are named after the signer's key, and the two networks use different keys, so running both checks in one directory leaves six files there rather than overwriting three — and the query then takes the highest height across both networks. On a Testnet check that had Mainnet files alongside it, the answer would be a Mainnet height, reported with two agreeing signers and no error.

:::warning[The newest entry in an index is not necessarily the one you get]

Do not read the last entry of a single index and assume that is your landing height. Signers publish independently, so the most recent entries may be offered by only one of them — and those can never be selected. The query above applies the same threshold RSKj does.

:::

Once you know the height, you can check how large the download will be. Run this in the same directory, so `$URL` and the index files still refer to the network you just checked. Any of the agreeing signers' indexes will do, since they publish the same archive:

```shell
DB=$(jq -r --argjson h <HEIGHT> '.dbs[] | select(.height == $h) | .db' index-<TRUSTED-KEY>.json)
curl -fsSI "$URL$DB" | grep -i content-length
```

Use that figure to size your temporary space, as described in [Before you start](#before-you-start).

## Run the import

Run this on a node that is **not** already running, with no valuable database in place — the first thing it does is erase the database directory for the selected network.

<Tabs>
  <TabItem value="import-mainnet" label="Mainnet" default>
    ```shell
    java -Xmx4G -cp <PATH-TO-THE-RSKJ-JAR> co.rsk.Start --import
    ```
  </TabItem>
  <TabItem value="import-testnet" label="Testnet">
    ```shell
    java -Xmx4G -cp <PATH-TO-THE-RSKJ-JAR> co.rsk.Start --testnet --import
    ```
  </TabItem>
</Tabs>

The `--import` flag sets `database.import.enabled` for that run only. It supplies no URL and no keys of its own — those come from the network configuration shipped in the JAR. `-Xmx4G` is the heap recommended for this command in [Setup node using Java](/node-operators/setup/installation/java/); left out, the JVM sizes the heap from physical RAM instead.

The import prints nothing to the console. It writes to `logs/rsk.log`, relative to the directory you ran the command from. A successful import logs there, in order:

```text
Bootstrap data downloaded
Bootstrap data hash checked
Bootstrap data extracted
Bootstrap data has successfully been imported in <n> mills
```

The node then continues into normal operation and starts importing blocks from peers, beginning just above the imported height.

Both stages take time: the download depends on your connection, and the load that follows depends on the machine. Neither is instant, and the whole import is expected to take minutes rather than the hours a full sync would.

## Confirm it worked, then restart without the flag

Once you see the import succeed and blocks being processed, **stop the node and start it again without `--import`**. This is the step that turns a one-off import into a normally operating node.

<Tabs>
  <TabItem value="restart-mainnet" label="Mainnet" default>
    ```shell
    java -cp <PATH-TO-THE-RSKJ-JAR> co.rsk.Start
    ```
  </TabItem>
  <TabItem value="restart-testnet" label="Testnet">
    ```shell
    java -cp <PATH-TO-THE-RSKJ-JAR> co.rsk.Start --testnet
    ```
  </TabItem>
</Tabs>

Check that it is serving and that it kept the imported data:

```shell
curl -sS http://localhost:4444 -X POST -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}'
```

```text
{"jsonrpc":"2.0","id":1,"result":"0x…"}
```

The result is the latest synced block in hexadecimal. If it is at or above the height you identified earlier and climbing, the import held and the node is syncing the remainder from peers. If it comes back at zero, the node did not keep the imported database — check that you started it without `--import` and against the same network.

### Clean up temporary files

RSKj leaves the downloaded archive and its extracted contents behind in the temporary directory — together, more than twice the size of the archive. They are not needed once the import has completed.

```shell
TMP=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/.*java.io.tmpdir = //p')
ls -d "$TMP"/import*
```

Remove anything left there.

## Common failures

| Message | What it means |
|---|---|
| `Failed to download and parse index from <url>` | A signer's index could not be fetched or parsed. Check network access to the import URL, and that you have not overridden `database.import.url` with something unreachable. |
| `Downloaded files doesn't contain enough entries for a common height` | No single height is offered, with a matching hash, by the required majority of trusted signers. This is what you see if `database.import.trusted-keys` has been narrowed to fewer working signers than the threshold, or if the indexes have no height in common. |
| `Not enough valid signatures: selected height <n> doesn't have enough trustworthy sources: <x> of <y>` | A height looked agreed-upon, but too few signatures actually verified against the trusted keys. The bootstrap data is not trustworthy. Do not work around this by lowering the requirement. |
| `Failed to create a temporary directory. Please start again the import process` | RSKj could not create its working directory under the JVM's temporary directory. This happens before anything is downloaded. Check that `java.io.tmpdir` exists and is writable by the user running the node — a read-only or otherwise restricted temporary directory is the usual cause. |
| `File: <path> does not match with expected hash: <hash>` | The downloaded archive does not match the hash the signers committed to. Usually an incomplete or corrupted download; remove the temporary files and retry. |
| `Error downloading bootstrap data from <url>. Please start again the import process` | The archive could not be retrieved, or could not be written to disk. Check network access, and check free space in the temporary directory — a download that fills the filesystem fails here. If it persists, the published archive may be missing from the location its index advertises. |
| `The file is corrupted or incomplete. Please start again the import process` | The archive downloaded and matched its hash, but could not be unpacked. Check free space in the temporary directory first: running out during extraction produces this message even though the archive itself is fine. If space is adequate and it persists, the published archive is faulty — report it rather than working around it. |
| `Error trying to read bootstrap data contents. Please start again the import process` | The unpacked data could not be read back. Check free space in the temporary directory, then retry. |
| `Configuration has less trusted sources than the minimum required <n> of 2` | Fewer than two trusted keys are configured, and two is the floor however few you configure. Restore the shipped keys for the network. This is a warning at startup, not the failure itself — the run continues and then fails on one of the messages above. |
| `java.lang.OutOfMemoryError` | The load stage ran out of heap. Raise `-Xmx` above the 4G used above and run the import again; note that a retry downloads the bootstrap data again. |

## Switching between LevelDB and RocksDB

Switching the [`keyvalue.datasource`](/node-operators/setup/configuration/reference#keyvaluedatasource-experimental) between `leveldb` and `rocksdb` requires starting with an empty database directory, because an existing database cannot be reopened under a different engine. Running with `--import` achieves that, which is why it is described as a way to switch.

Import sync is not a database conversion, though. It **discards your current database** and replaces it with the published bootstrap data, so a node that had synced to the tip comes back at the imported height and has to sync the difference again.

The one-shot rule still applies. Use `--import` on the command line for the switchover, and do not leave import enabled in configuration afterwards.

## Docker and the Ubuntu package

Import sync is documented here for the JAR only, and deliberately so.

The hazard at the top of this page — that import erases the database on **every** start — is difficult to contain in a setup where the node restarts on its own. A container configured to import on start, or a `systemd` service whose configuration file has `database.import.enabled = true`, will erase and re-download its database on every restart and every reboot, indefinitely.

If you operate a node under Docker or the Ubuntu package and want to bootstrap it from published data, run the import as a **separate one-off invocation** of the JAR against the same data directory, then start your usual service normally with import disabled.

## Related

- [Setup node using Java](/node-operators/setup/installation/java/)
- [`database.import` configuration reference](/node-operators/setup/configuration/reference#databaseimport)
- [CLI flags](/node-operators/setup/configuration/cli/)
- [Minimum hardware requirements](/node-operators/setup/requirements/)
