```sh
export BOOTSTRAP_SERVERS="localhost:9092" INSTANCE_ID="kafka-txn-0" \
  GROUP_ID="my-group" INPUT_TOPIC="input-topic" OUTPUT_TOPIC="output-topic"
       
mvn compile exec:java -q

bin/kafka-console-producer.sh --bootstrap-server :9092 --topic input-topic
bin/kafka-console-consumer.sh --bootstrap-server :9092 --topic output-topic --from-beginning

# build and run on Minikube
mvn clean package

minikube start
eval "$(minikube docker-env)" && docker build -t ghcr.io/fvaleri/kafka-txn:latest .
docker system prune -f; eval "$(minikube docker-env --unset)"
sed -E "s/imagePullPolicy: .*/imagePullPolicy: Never/g" src/main/kube/statefulset.yaml | kubectl create -f -

krun() { kubectl run krun-"$(date +%s)" -itq --rm --restart="Never" --image="quay.io/strimzi/kafka:latest-kafka-3.6.1" -- sh -c "$*; exit 0"; }
krun bin/kafka-console-producer.sh --bootstrap-server my-cluster-kafka-bootstrap:9092 --topic input-topic
krun bin/kafka-console-consumer.sh --bootstrap-server my-cluster-kafka-bootstrap:9092 --topic output-topic --from-beginning
```
