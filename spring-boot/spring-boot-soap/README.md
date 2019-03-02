```sh
mvn clean compile

mvn spring-boot:run -f ./spring-boot-soap-server/pom.xml -Dspring-boot.run.arguments="--server.port=8011"
mvn spring-boot:run -f ./spring-boot-soap-server/pom.xml -Dspring-boot.run.arguments="--server.port=8012"
mvn spring-boot:run -f ./spring-boot-soap-server/pom.xml -Dspring-boot.run.arguments="--server.port=8013"

mvn clean spring-boot:run -f ./spring-boot-soap-client/pom.xml
```
