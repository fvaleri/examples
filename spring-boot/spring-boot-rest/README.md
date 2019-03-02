```sh
mvn clean compile
mvn spring-boot:run -f ./spring-boot-rest-server/pom.xml
mvn spring-boot:run -f ./spring-boot-rest-client/pom.xml

curl -H "Content-Type: application/json" http://localhost:8080/actuator/health
curl -H "Content-Type: application/json" http://localhost:8080/api/doc
curl -H "Content-Type: application/json" http://localhost:8080/api/greet/fede

mvn clean oc:deploy -Pkube
#mvn oc:undeploy -Pkube
kubectl get route spring-boot-rest-server -o jsonpath={.spec.host}
```
