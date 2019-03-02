```sh
mvn clean install

$AMQ_HOME/bin/artemis create hosts/host0 --name host0 --user admin --password admin --require-login --port-offset 1
$INSTANCE_HOME/bin/artemis run

sed -i '/admin=admin/s/^#//g' $FUSE_HOME/etc/users.properties
$FUSE_HOME/bin/fuse
uninstall karaf-bridge
install -s mvn:it.fvaleri.example/karaf--bridge/0.0.1-SNAPSHOT
```
