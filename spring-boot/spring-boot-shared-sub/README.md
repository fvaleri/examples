```sh
# add the following address to the broker.xml
<address name="example.foo">
    <multicast/>
</address>

# run clients in this order
mvn clean compile
mvn spring-boot:run -f ./spring-boot-shared-sub-cons-a/pom.xml
mvn spring-boot:run -f ./spring-boot-shared-sub-cons-b/pom.xml
mvn spring-boot:run -f ./spring-boot-shared-sub-prod/pom.xml
```
