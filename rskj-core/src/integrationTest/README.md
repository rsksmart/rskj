## rskj-core integrationTests

This module contains integration tests for the RSKj node, these tests aim to validate core functionalities from the node
and validate key features from the tool. It takes sometime to be executed and is part of the CI pipeline to ensure the
RSKj core functionalities are working as expected.

We are trying to improve and increase the coverage of the integration tests, if you have any suggestion or want to 
contribute with new tests, please feel free to open a PR. To make this task easy, some classes were created to help the
creation of nodes inside integration tests scenarios and the validation that they worked as expected.

### Running the integration tests

You will need gradle installed in your machine to run the integration tests. To run the integration tests, execute the 
following command:

```shell
./gradlew --stacktrace integrationTest
```

#### Auxiliary classes to start a node inside a Integration Test

The most important auxiliary classes are on the package `/co/rsk/integrationTest/utils/cli`. These classes are intended to
make easier execute a command line process to start a node and with possibility to configure them according different 
needs. 

#### [RskjCommandLineBase](./java/co/rsk/util/cli/RskjCommandLineBase.java)
To make easier the creation of commands classes, we created an abstract base class called *RskjCommandLineBase* with useful
and common things necessary for any RSKj command:
1. Common way of execute a command accepting _parameters_ and _arguments_ that can be set by the class that inherits it.
2. It redirects the output stream to an output _StringBuilder_ and this output can be fetched at any moment calling a 
method to get it.
3. It reads the latest version of the RSKj from the generated _build_ folder and execute the class passed
as argument. For example, if it's the **co.rsk.cli.tools.ConnectBlocks** or **co.rsk.Start** depending on the command that
inherited and configured it.
4. It has a method to stop the process that was started by the command or also the possibility to wait for a timeout or
indefinitely until the process ends if nothing was passed.
5. It returns the process that can be used to interact with it, call _waitFor_ and _destroy_ methods for example. 

Based on the *RskjCommandLineBase* class, we created two children class for two different commands:

##### [ConnectBlocksCommandLine](./java/co/rsk/util/cli/ConnectBlocksCommandLine.java)

This class is used to connect the blocks exported in a CSV file to the database of a 
node according the mode of the node. If the CSV was exported from a *regtest* node, it will export to the default 
*regtest* database folder. If it was exported from a *testnet* node, it will export to the default *testnet* database 
folder and so on. The class receive as parameter the path from the CSV file inside the resources folder, it's recommended to have this file on the 
resources folder of the test. The test  Example of usage:

```java
    ConnectBlocksCommandLine connectBlocksCommandLine = new ConnectBlocksCommandLine(exportedBlocksCsvFullPath);
    connectBlocksCommandLine.executeCommand();
```

The test class [RskjCommandLineTest](./java/co/rsk/util/cli/RskjCommandLineBase.java) for our command line structure
uses this class.

##### [NodeIntegrationTestCommandLine](./java/co/rsk/util/cli/NodeIntegrationTestCommandLine.java)

Command line to start a node with the possibility to configure the node passing the path for the configuration file and 
the mode of the node (*regtest*, *testnet* or *mainnet*). 

Be aware that if you need to start multiple nodes on the same test, it will be necessary configure different ports for 
each one. You can have multiple configuration files on resources folder and pass the path for the file as parameter. 
There is a utility class to get the full path for a file on resources folder, the 
[FilesHelper#getAbsolutPathFromResourceFile](./java/co/rsk/util/FilesHelper.java) class. Example of usage:
    
```java
    String rskConfFile = FilesHelper.getAbsolutPathFromResourceFile(getClass(), "config_file_name.conf");
```

After this, you can start the node passing the configuration file and the mode of the node. Example of usage:

```java
    NodeIntegrationTestCommandLine node = new NodeIntegrationTestCommandLine(rskConfFile, "regtest");
    node.executeCommand();
```

Then you can start the node and interact with it. It is recommended to wait a bit before start to interact with the 
node, it takes some time for the node to start and be ready to receive commands. It was created a helper class for this 
as well. The [ThreadTimerHelper](./java/co/rsk/util/ThreadTimerHelper.java) class has a method to wait a certain amount
of seconds for example:

```java
    ThreadTimerHelper.waitForSeconds(20);
```

Now, finally, we will explain an example in how to start a node, wait for certain log to be printed and then execute
some JSON RPC command to the node to check some data.

```java
    String rskConfFullPath = FilesHelper.getAbsolutPathFromResourceFile(getClass(), RSKJ_SERVER_CONF_FILE_NAME);
    NodeIntegrationTestCommandLine clientNode = new NodeIntegrationTestCommandLine(rskConfFullPath, "--regtest");
    clientNode.startNode();

    ThreadTimerHelper.waitForSeconds(20);
    
    long startTime = System.currentTimeMillis();
    long endTime = startTime + TEN_MINUTES_IN_MILLISECONDS;
    boolean isClientSynced = false;

    while (System.currentTimeMillis() < endTime) {
        if(clientNode.getOutput().contains("Some message expected on the log.")) {
            try {
                JsonNode jsonResponse = OkHttpClientTestFixture.getJsonResponseForGetBestBlockMessage(portClientHttp);
                String bestBlockNumber = jsonResponse.get(0).get("result").get("transactions").get(0).get("blockNumber").asText();
                if(bestBlockNumber.equals("0x1000")) { // We reached the block expected
                    isClientSynced = true;
                    break;
                }
            } catch (Exception e) {
                System.out.println("Error while trying to get the best block number from the client: " + e.getMessage());
                System.out.println("We will try again in 10 seconds.");
                ThreadTimerHelper.waitForSeconds(10);
            }
        }
    }
```
This test is doing the following:
1. Get the full path from a configuration file previously created and saved on the resources folder.
2. Start a node with the configuration file on _regtest_ mode.
3. Wait for 20 seconds to the node be ready to receive commands.
4. Set a timeout of 10 minutes to check if the node is synced before enter the while loop
5. Inside the while loop, it checks if the log contains a message expected
   1. If it does, it tries to get the best block number from the node. If it reached the expected value, set the flag 
    _isClientSynced_ to true and break the loop.
   2. If not, it waits 10 seconds and try again

This code is just an example, you can create your own logic to interact with the node and check the data you need. But 
try to follow the best practices to avoid infinite loops and make the tests more reliable. If it's necessary, more types
of JSON RPC commands can be created on the [OkHttpClientTestFixture](./java/co/rsk/util/OkHttpClientTestFixture.java) and
so on. In summary, feel free to add functionalities to the classes to make the tests more reliable and easy to create.
