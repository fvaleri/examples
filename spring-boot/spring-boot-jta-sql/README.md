```sh
# start MySQL
WORK_DIR="$HOME/.local/mysql"
docker run --name db-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -v $WORK_DIR:/var/lib/mysql \
  -d -p 3306:3306 mysql
docker exec -it db-mysql mysql -uroot -proot -e \
  "CREATE DATABASE testdb CHARACTER SET utf8mb4;
   CREATE USER 'admin'@'%' IDENTIFIED WITH mysql_native_password BY 'admin';
   GRANT CREATE,SELECT,INSERT,UPDATE,DELETE ON testdb.* TO 'admin'@'%';
   GRANT XA_RECOVER_ADMIN on *.* to 'admin'@'%';
   FLUSH PRIVILEGES;"
docker exec -it db-mysql mysql testdb -uadmin -padmin -e \
  "CREATE TABLE IF NOT EXISTS audit_log (
    id SERIAL PRIMARY KEY,
    message VARCHAR(255) NOT NULL
  );"

# start Artemis
ARTEMIS_URL="https://archive.apache.org/dist/activemq/activemq-artemis/2.22.0/apache-artemis-2.22.0-bin.tar.gz"
ARTEMIS_HOME="/tmp/artemis" && mkdir -p $ARTEMIS_HOME $ARTEMIS_HOME/servers/server0
curl -sL $ARTEMIS_URL | tar xz -C $ARTEMIS_HOME --strip-components 1
export PATH="$ARTEMIS_HOME/bin:$ARTEMIS_HOME/servers/server0/bin:$PATH"
artemis create $ARTEMIS_HOME/servers/server0 --name server0 --user admin --password admin --require-login
artemis-service start

# run the application
mvn clean spring-boot:run -Dnode.identifier="server0"

ADDRESS="http://localhost:8080"
curl -X POST $ADDRESS/api/messages/hello
curl $ADDRESS/api/messages
curl -X POST $ADDRESS/api/messages/fail
```
