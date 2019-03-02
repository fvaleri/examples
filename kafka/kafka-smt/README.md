```sh
mvn clean package -DskipTests
cp target/kafka-smt-*.jar $PLUGINS

# add to the connectors SMT chain
"transforms": "JsonWriter",
"transforms.JsonWriter.type": "it.fvaleri.example.JsonWriter"
```
