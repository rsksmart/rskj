# Switch network
If you want to switch your node to Mainnet, Testnet, or RegTest networks:

1\. If your node is not in `localhost`, connect your computer to the node over `ssh`.

```shell
ssh user@server
```

2\. Pick a network that you would like to connect to.

#### Regtest

In order to switch from another network to Regtest:

```bash
sudo service rsk stop
cd /etc/rsk
sudo rm -f node.conf
sudo ln -s regtest.conf node.conf
sudo service rsk start
```

Using this network, it allows you to start with some wallets (accounts) on your node. These wallets have funds.


#### Testnet

In order to switch from another network to Testnet:

```bash
sudo service rsk stop
cd /etc/rsk
sudo rm -f node.conf
sudo ln -s testnet.conf node.conf
sudo service rsk start
```

#### For Mainnet

In order to switch from another network to Mainnet:

```bash
sudo service rsk stop
cd /etc/rsk
sudo rm -f node.conf
sudo ln -s mainnet.conf node.conf
sudo service rsk start
```

By running these instructions in your shell, you are:

- Stopping the running RSK service.
- Moving to RSK configuration folder (`cd`).
- Removing `node.conf`, that is a symbolic link to the configuration you're using (`rm` deletes it).
- Linking `node.conf` with the configuration file you decide (`ls` with the `-s` option, it makes symbolic - or _soft_ - links). The node is configured to read directly from the `node.conf` link.
- Restarting the RSK service.
