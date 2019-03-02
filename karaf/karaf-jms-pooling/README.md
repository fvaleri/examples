```sh
# start Karaf and Artemis
bin/artemis create hosts/host0 --name host0 --user admin --password admin --allow-anonymous
hosts/host0/bin/artemis run

# build and install
mvn clean install

features:install camel-blueprint jms artemis-jms-client camel-jms
install -s mvn:org.apache.commons/commons-pool2/2.6.2.redhat-00001
install -s mvn:org.messaginghub/pooled-jms/1.0.4.redhat-00004
install -s mvn:it.fvaleri.example/karaf-jms-pooling/0.0.1-SNAPSHOT

# verify
list -l | grep karaf-artemis-pool
jms:connectionfactories
jms:info -u admin -p admin jms/artemis
camel:route-list
log:tail
```
