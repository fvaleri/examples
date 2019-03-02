```sh
# start PostgreSQL, Karaf and Artemis
bin/artemis create hosts/host0 --name host0 --user admin --password admin --allow-anonymous
hosts/host0/bin/artemis run

# build and install (using pax-jdbc-config pax-jms-config for cf setup)
mvn clean install

install -s mvn:org.postgresql/postgresql/42.2.5
features:install jdbc jms jndi
features:install pax-jdbc-pool-narayana pax-jms-pool-narayana pax-jms-artemis pax-jdbc-config pax-jms-config
features:install camel-blueprint camel-jpa camel-jms
features:install jpa hibernate-orm

cp connection-factory.xml $FUSE_HOME/deploy
cp data-source.xml $FUSE_HOME/deploy
install -s mvn:it.fvaleri.example/karaf-jta-artemis/0.0.1-SNAPSHOT

# test the route
#log:set DEBUG it.fvaleri.example
#log:set DEBUG org.springframework.transaction.PlatformTransactionManager
camel:route-list

jms:send -u admin -p admin jms/artemisXACF xaQueue1 "Hello Camel XA"
jms:browse -u admin -p admin jms/artemisXACF xaQueue2
jdbc:query jdbc/postgresXADS 'select * from messages'
```
