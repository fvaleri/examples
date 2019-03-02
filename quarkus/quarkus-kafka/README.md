```sh
mvn quarkus:dev -Ddebug=false
# http://localhost:8080/q/dev

curl -XPOST http://localhost:8080/api/kafka -d'key=my-key' -d'value=my-value'
curl -XGET http://localhost:8080/api/kafka
curl -XGET http://localhost:8080/api/kafka/topics

# run integration test
mvn clean test -Dtest=KafkaClientIT

# build and run
mvn clean package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar

# build Linux executable and run
mvn clean package -DskipTests -Pnative
./target/*-runner
```
