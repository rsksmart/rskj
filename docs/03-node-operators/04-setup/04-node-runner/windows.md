# How to compile and run an RSKj node on Windows
Here you have the steps to compile and run an Rootstock node on Windows.

## IntelliJ IDEA setup

### Compiling the node

After opening IDEA, we need to load the RSKj project, this can be done by using the *Import project* option in IDEA.

To do that follow the next steps:

- Go to *File* -> *New* -> *Project from Existing Sources...*
- Browse in the RSKj downloaded code the file `rskj\build.gradle` and select it.
- Within the dialog select *Use default gradle wrapper* and then press *Finish*.

![img](/img/rsk/howToInstallAndRun/IdeaRskJWelcome.png)

#### IDEA Build/Run configuration

We need to create a new configuration profile to run the node from IDEA.
That can be done by clicking on *Run* -> *Edit Configurations* or as shown in the following picture:

![img](/img/rsk/howToInstallAndRun/EditConfigs.png)

Then set the options as shown below:

![img](/img/rsk/howToInstallAndRun/AddNewConfig.png)

- Main Class: `co.rsk.Start`
- Working directory: `/path/to/code/rskJ`
- Use classpath of module: `rskj-core_main`
- JRE need to be set as: `Default (17 - SDK of 'rsk-core_main' module)`

:::info[Info]
- If it isn't configured the default JDK, you have to set it in: *File -> Project Structure*.
- If the IDE doesn't recognize the configuration options, open `rskj/rskj-core/build.gradle` and sync it from `Gradle` tab.
:::

#### Running the node

We are ready to run the node using IDEA, just press the *Start* button at the right of the configuration we've just created.

![img](/img/rsk/howToInstallAndRun/Run.png)

If everything is OK, you should see the debug information like that:

![img](/img/rsk/howToInstallAndRun/Running.png)

And yes! Congratulations! Now you're running a local Rootstock node :)

You're joined to Mainnet by default.

If you want to switch the network, add:

- For Testnet: `--testnet`
- For Regtest: `--regtest`

Inside the field `Program Arguments` in your run configuration.

## Visual Studio Code setup

### Recommended Plugins

- [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack).
- [Gradle Plugin](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle)

#### Visual Studio Configuration Files:

In order to setup JDK configuration, we use `.vscode/settings.json`. Here we can setup the latest JDK for [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack), then use the recommended version for RSKj, for instance:

**.vscode/settings.json**
```
{
   "java.jdt.ls.java.home": "C:\\jdk-17",
   "java.configuration.runtimes": [
      {
        "name": "JavaSE-1.8",
        "path": "C:\\jdk-1.8",
        "default": true
      },
      {
        "name": "JavaSE-17",
        "path": "C:\\jdk-17",
      },
    ]
}
```

In this example, we have setup `Java 17` for [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) to work as expected and the default java compiler is `Java 1.8`. These paths should point to your java home.

Be aware that the path may vary depending on how you installed it.

In order to build, run or debug RSKj, we use `.vscode/launch.json`. Here we can setup the commands that will be used to run our application, for instance:

**.vscode/launch.json**
```text
{
    // Use IntelliSense to learn about possible attributes.
    // Hover to view descriptions of existing attributes.
    // For more information, visit: https://go.microsoft.com/fwlink/?linkid=830387
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Run RSK Start",
            "request": "launch",
            "mainClass": "co.rsk.Start",
            "args" : "--testnet -Xkeyvalue.datasource=leveldb"
        }
    ]
}
```

In this example we are going to run the application with the following arguments: `--testnet -Xkeyvalue.datasource=leveldb`.

#### Running the project

We are ready to run the node using Visual Studio Code, select your configuration from `launch.json` within `Run and Debug`.

![img](/img/rsk/howToInstallAndRun/VSCode_Launch.png)

Click on `start (green play icon at the left of your configuration name)`.

![img](/img/rsk/howToInstallAndRun/VSCode_Run.png)

A debug tools menu shows up at the top of the IDE window, were you could run the node step by step!

#### Building the project with Gradle in Visual Studio Code

In order to build the project using `Gradle`, we can simply go to the respective tab. On tab, we should be able to see all available `Gradle` configurations from the application. Select the project to be built and double-click the desired `Gradle Task`.

![img](/img/rsk/howToInstallAndRun/VSCode_Gradle.png)

#### Testing in Visual Studio Code

In order to run tests, we can simply go to the `Testing` tab where you can see all the tests. We can also go directly to the test file and right-click the icon at the left of a declaration of a test and then decide to either run or debug the test.

![img](/img/rsk/howToInstallAndRun/VSCode_Test_Debug.png)

## Any problems?

Check out the [troubleshooting section](/node-operators/troubleshooting/), hope it helps!