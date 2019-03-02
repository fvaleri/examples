```sh
mvn clean compile camel:run
curl -H "Content-Type: application/xml" \
  -d @src/main/resources/data/test-uk.xml \
  http://localhost:9000/orders
```
