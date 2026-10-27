# Working in the OSS Scanner image

RSKj is already built at `/src` and the Gradle cache is warm. **There is no network**: pass
`--offline` to every Gradle command.

```
./gradlew --offline --no-daemon test --tests 'co.rsk.peg.BridgeSupportTest'   # unit tests
./gradlew --offline --no-daemon assemble                                      # rebuild after an edit
```

Fat jar: `rskj-core/build/libs/rskj-core-<version>-<modifier>-all.jar`

## A local node

A standalone regtest node needs no peers, mines on its own, and serves JSON-RPC on
`127.0.0.1:4444`. It is usually the quickest way to reproduce something end to end.

```
java -Dlogging.stdout=INFO -Dpublic.ip=127.0.0.1 \
     -cp rskj-core/build/libs/rskj-core-*-all.jar co.rsk.Start --regtest
```

Keep `-Dpublic.ip`. Without it the node queries `checkip.amazonaws.com` for its public IP; offline
that stalls on DNS and logs an ERROR stack trace. The node recovers by falling back to `127.0.0.1`
(`SystemProperties.getMyPublicIpFromRemoteService`), so that error is an artefact of the sandbox,
not a fault worth reporting.

## Resources

The audit VM has 2 CPUs and 8 GB, and the `test` task asks for `-Xmx4G`. Keep `--tests` selections
narrow, and do not use the `parallelTest` task.

The `integrationTest` suite spawns real node processes and binds ports. Its dependencies are
cached, but it is slow and is rarely needed to reproduce a finding.

## Reporting

A reproducer written as a JUnit test under `rskj-core/src/test/java` is the most useful form a
finding can take — it drops straight into our suite.
