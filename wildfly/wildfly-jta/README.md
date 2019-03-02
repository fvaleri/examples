```sh
psql template1
CREATE DATABASE testdb WITH TEMPLATE template0 ENCODING UTF8 LC_CTYPE en_US;
\c testdb;
CREATE USER admin WITH ENCRYPTED PASSWORD 'admin';
GRANT ALL PRIVILEGES ON DATABASE testdb to admin;
\q
psql testdb -U admin
CREATE TABLE testtb (id SERIAL PRIMARY KEY, text VARCHAR);
\q

curl https://jdbc.postgresql.org/download/postgresql-42.2.18.jar -o /tmp/postgresql.jar
bin/jboss-cli.sh -c <<\EOF
module add --name=org.postgresql --resources=/tmp/postgresql.jar --dependencies=javax.api,javax.transaction.api
/subsystem=datasources/jdbc-driver=postgres:add(driver-name="postgres",driver-module-name="org.postgresql",driver-class-name=org.postgresql.Driver)
data-source add --jndi-name=java:jboss/datasources/testdbDS --name=testdbDS --connection-url=jdbc:postgresql://localhost:5432/testdb --driver-name=postgres --user-name=admin --password=admin
/subsystem=transactions:write-attribute(name=node-identifier,value=host0)
/subsystem=messaging-activemq/server=default/jms-queue=my-queue:add(entries=["java:/jms/queue/my-queue", "java:jboss/exported/jms/queue/my-queue"])
reload
EOF

mvn install -Pwildfly
#mvn clean -Pwildfly
```
