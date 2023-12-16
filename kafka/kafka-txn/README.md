```sh
export BOOTSTRAP_SERVERS="localhost:9092" INSTANCE_ID="kafka-txn-0" \
  GROUP_ID="my-group" INPUT_TOPIC="input-topic" OUTPUT_TOPIC="output-topic"
       
mvn compile exec:java -q

bin/kafka-console-producer.sh --bootstrap-server :9092 --topic input-topic
bin/kafka-console-consumer.sh --bootstrap-server :9092 --topic output-topic --from-beginning

# build and run on Kube
mvn clean package

docker build -t ghcr.io/fvaleri/kafka-txn:latest .
docker login ghcr.io -u fvaleri 
docker push ghcr.io/fvaleri/kafka-txn:latest
rm -rf ~/.docker/config.json

kubectl create -f kafka-txn.yaml
  
kubectl run client -q --restart="Never" --image="ghcr.io/strimzi/kafka:latest-kafka-3.5.1" -- \
  bin/kafka-console-producer.sh --bootstrap-server :9092 --topic input-topic
kubectl run client -q --restart="Never" --image="ghcr.io/strimzi/kafka:latest-kafka-3.5.1" -- \
  bin/kafka-console-consumer.sh --bootstrap-server :9092 --topic output-topic --from-beginning
```
