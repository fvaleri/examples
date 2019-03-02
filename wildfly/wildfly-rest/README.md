```sh
mvn install -Pwildfly
#mvn clean -Pwildfly

curl -X GET -H "Content-Type: application/json" http://localhost:8080/api/swagger
curl -X POST -H "Content-Type: application/json" -d '{"firstName":"John","lastName":"Doe"}' http://localhost:8080/api/customers
curl -X GET -H "Content-Type: application/json" http://localhost:8080/api/customers
curl -X GET -H "Content-Type: application/json" http://localhost:8080/api/customers/1
```
