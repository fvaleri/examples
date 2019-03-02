```sh
mvn clean spring-boot:run
# http://localhost:8080/api/doc

curl http://localhost:8080/api/proxy/todo/1
curl http://localhost:8080/api/proxy/todo/1000

curl http://localhost:8080/actuator
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/jolokia/read/org.apache.camel:context=*,type=routes,name=*
```
