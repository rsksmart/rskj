This section explains how to solve some known or frequently encountered issues.

If what you need is not in this section, **contact us** without hesitation through the [Rootstock Community on Discord](https://rootstock.io/discord). We will be happy to help you!

## Node Startup and Network Connectivity Issues

### Discovery Can't Be Started
- On Windows, if you start the node and it doesn't do anything, there is a high chance you have a problem with the UDP port of the node.
- The UDP port is configured in the node's configuration file, specifically with the value `peer.port`. By default this port is configured to `5050`.
- To check if that port is already taken by other application you can follow these steps:
    - Open a `cmd` console and run `netstat -ano -p UDP | findstr :5050` (or replace `5050` with the port of your preference).
    - You will get a result with the process ID (if any) already using that port for UDP.
    - With the process ID (the value at the far right), run this command `tasklist /FI "PID eq processId-you-got"`.
    - This will let you know which application/service is using this port.
- Please make sure the port of your preference is not taken by other application. If so, you need to change the [node configuration](/node-operators/setup/configuration/preferences), by overwriting the [peer](/node-operators/setup/configuration/preferences).

### Can't Get Public IP
- If you get the error: `Can't get public IP` when you're trying to run your rskj node, the reason is that rskj uses Amazon Check IP service to set the [`public.ip`](/node-operators/setup/configuration/reference/) parameter.
- To solve it, you need to change the `public.ip` key in config file with your IP address (if you don't know your IP, simply [search for it](https://www.google.com/search?q=what's+my+IP+address)).
- Visit the [Config page](/node-operators/setup/configuration/) to change a node's configuration file.

## Logging and Visibility

### I Don't See the Logs
- You can configure your own log level, following these [instructions](/node-operators/setup/configuration/verbosity).

## Dependency and Plugin Issues

### Plugin with id witness not found
- If you have this error it's possible that you have missed to run rskj's dependencies.
- So please, follow the instructions depending on your operation system:
    - [On Windows](/node-operators/setup/node-runner/windows)
    - [On Linux](/node-operators/setup/node-runner/linux)
    - [On Mac](/node-operators/setup/node-runner/macos)

## Command-Line Tools

### Rewind Blocks

- This tool should be used in a scenario where an Rootstock node processes blocks that are corrupted or invalid, for example after a hard fork. It allows one to remove such blocks and start from a previously known state. It does so by removing the blocks with block number higher than the block number parameter command line argument.

> Note: The node must be turned off before the rewind, and restarted after.

- Example: `java -cp rsk-core-<VERSION>.jar co.rsk.cli.tools.RewindBlocks 1000000`
- The above command removes the blocks with number 1000001 or higher.

### DbMigrate: Migrate Between Databases
- This tool allows the user to migrate between different supported databases such as rocksdb and leveldb.

How to use
- To use the DbMigrate tool to migrate between databases, we will need a tool class and CLI arguments.
  The tool class is: `co.rsk.cli.tools.DbMigrate`

**Required CLI arguments:**
- `args[0]` - database target where we are going to insert the information from the current selected database.
    - Note: You cannot migrate to the same database or an error will be thrown. It is highly recommended to turn off the node in order to perform the migration since latest data could be lost.> > - Example migrating from leveldb to rocksdb:
- `java -cp rsk-core-<VERSION>.jar co.rsk.cli.tools.DbMigrate rocksdb`

## Docker Issues

### ERROR: failed to solve: failed to read dockerfile
- The first error indicates that Docker couldn't find the Dockerfile in your current directory. Ensure you're in the correct directory or specify the path to the Dockerfile
- If your Dockerfile is in txt, move the Dockerfile.txt to Dockerfile: `mv /path/to/Dockerfile.txt /path/to/Dockerfile`
- Proceed with Docker Build command: `docker build -t regtest /path/to/rskj-node-jar`

### WARNING: The requested image's platform (linux/amd64) does not match
- This warning indicates that the platform of the image doesn't match the platform of your host machine. The image is built for linux/amd64 architecture, but your host machine is linux/arm64/v8 architecture.
- Use a compatible image:1 `docker run -d --name rsk-node -p 4444:4444 -p 50505:50505 rsksmart/rskj:arm64v8-latest node --regtest`.