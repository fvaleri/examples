```sh
# create the database
CREATE TABLE USERS (ID SERIAL PRIMARY KEY, NAME VARCHAR(255));
INSERT INTO USERS (NAME) VALUES ('John Doe');
INSERT INTO USERS (NAME) VALUES ('Donald Duck');

# install required features
bin/fuse
features:install camel-servlet camel-jackson camel-swagger-java \
  jdbc pax-jdbc-config pax-jdbc-pool-dbcp2 pax-jdbc-postgresql

# create the data source (registering data source factory)
jdbc:ds-create -dc org.postgresql.Driver -u admin -p admin \
  -url "jdbc:postgresql://localhost:5432/testdb" testdb
jdbc:ds-list
#config:list '(service.factoryPid=org.ops4j.datasource)'
jdbc:query testdb "SELECT * FROM users"

# build and install
mvn clean install

install -s mvn:it.fvaleri.example/karaf-rest/0.0.1-SNAPSHOT
http:list
log:set TRACE
log:tail

# test the service
curl -H "Content-Type: application/json" http://localhost:8181/api/users
curl -H "Content-Type: application/json" http://localhost:8181/api/users/1
curl -X POST -H "Content-Type: application/json" -d '{"name":"Grut"}' http://localhost:8181/api/users
curl -H "Content-Type: application/json" http://localhost:8181/api/doc
```
