```sh
export BOOTSTRAP_SERVERS="localhost:9092" \
       REGISTRY_URL="http://localhost:8080/apis/registry/v2" \
       TOPIC_NAME="my-topic" \
       ARTIFACT_GROUP="default"
mvn compile exec:java -q
```
