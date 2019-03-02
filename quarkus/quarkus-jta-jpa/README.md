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
   GRANT ALL ON testdb.* TO 'admin'@'%';
   GRANT XA_RECOVER_ADMIN on *.* to 'admin'@'%';
   FLUSH PRIVILEGES;"
docker exec -it db-mysql mysql testdb -uadmin -padmin -e \
  "CREATE TABLE IF NOT EXISTS audit_log (
    id SERIAL PRIMARY KEY,
    message VARCHAR(255) NOT NULL
  );"

# start Artemis
docker run --name artemis \
  -e AMQ_USER=admin \
  -e AMQ_PASSWORD=admin \
  -d -p 61616:61616 \
  quay.io/artemiscloud/activemq-artemis-broker

# run the application
# note: this does not currently support TX recovery
mvn quarkus:dev -Ddebug=false
# http://localhost:8080/q/dev

# build and run
mvn clean package -DskipTests
export NODE_IDENTIFIER="myid0"
java -jar target/quarkus-app/quarkus-run.jar

# build Linux executable and run
# note: this example doesn't work with native builds
mvn clean package -DskipTests -Pnative
./target/*-runner

ADDRESS="http://localhost:8080/api"
curl -X POST $ADDRESS/messages/hello
curl $ADDRESS/messages
curl -X POST $ADDRESS/messages/fail
```
