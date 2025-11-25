#!/bin/bash

if [ $# -ne 3 ]; then
    echo "Usage: $0 <jar_file> <host_name> <config_file>"
    echo "Example: $0 boton-1 rskj-core/build/libs/rskj-core-8.1.0-REED-all.jar rskj-core/bin/main/config/regtestA.conf"
    exit 1
fi

jar_file=$1
host_name=$2
config_file=$3

host_name="ubuntu@$host_name"

# Test if connnection is possible
ssh $host_name "echo 'Connection successful'" || {
    echo "Connection failed"
    exit 1
}

# Test if config file exists
if [ ! -f $config_file ]; then
    echo "Config file $config_file not found"
    exit 1
fi

echo "Deploying to $host_name with config $config_file"

echo ""
echo ""

echo "1. Stop the Node"
ssh $host_name "sudo systemctl stop rsk" || {
    echo "Failed to stop the node"
    exit 1
}

ssh $host_name "sudo systemctl status rsk"
echo ""
echo ""

echo "2. Copy the assets to the remote machine"
ssh $host_name "mkdir -p /tmp/rsk_config" || {
    echo "Failed to create the temporary directory"
    exit 1
}

scp $config_file $host_name:/tmp/rsk_config/custom.conf || {
    echo "Failed to copy the config file"
    exit 1
}

scp $jar_file $host_name:/tmp/rsk_config/rsk.jar || {
    echo "Failed to copy the jar file"
    exit 1
}

ssh $host_name "sudo mv /tmp/rsk_config/custom.conf /etc/rsk/custom.conf" || {
    echo "Failed to move the config file"
    exit 1
}

ssh $host_name "sudo mv /tmp/rsk_config/rsk.jar /usr/share/rsk/rsk.jar" || {
    echo "Failed to move the jar file"
    exit 1
}

echo ""
echo ""
echo "3. Create sysconfig file"
ssh $host_name "cat - <<EOF > /tmp/rsk_config/sysconfig.conf
JAVA_OPTS=-Xms3G -Xmx5G -Dlogback.configurationFile=/etc/rsk/logback.xml -Drsk.conf.file=/etc/rsk/custom.conf -Dkeyvalue.datasource=rocksdb -Ddatabase.dir=/var/lib/rsk/database/regtest

RSKJ_CLASS=co.rsk.Start
RSKJ_OPTS=--regtest
EOF
" || {
    echo "Failed to create sysconfig file"
    exit 1
}

ssh $host_name "sudo mv /tmp/rsk_config/sysconfig.conf /etc/sysconfig/rsk" || {
    echo "Failed to move the sysconfig file"
    exit 1
}

echo ""
echo ""
echo "4. Clean the database"
ssh $host_name "sudo rm -rf /var/lib/rsk/database/regtest/*" || {
    echo "Failed to clean the database"
    exit 1
}

echo ""
echo ""
echo "5. Restart the Node"
ssh $host_name "sudo systemctl restart rsk" || {
    echo "Failed to restart the node"
    exit 1
}

echo ""
echo ""
echo "6. Verify that the node is running correctly"
ssh $host_name "sudo systemctl status rsk"
echo ""
echo ""

echo "All set up correctly"
