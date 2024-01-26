```sh
###
### Run on localhost
###

KAFKA_VERSION="3.6.1" KAFKA_HOME="target/kafka" \
  && pkill -SIGKILL -ef "$KAFKA_HOME"; rm -rf "$KAFKA_HOME" && mkdir -p "$KAFKA_HOME"
curl -Lk "https://archive.apache.org/dist/kafka/$KAFKA_VERSION/kafka_2.13-$KAFKA_VERSION.tgz" \
  | tar xz -C "$KAFKA_HOME" --strip-components 1 && pushd "$KAFKA_HOME"
sed -Ei "s#log.dirs=.*#log.dirs: data#g" config/kraft/server.properties
bin/kafka-storage.sh format -t "$(bin/kafka-storage.sh random-uuid)" -c config/kraft/server.properties \
  && bin/kafka-server-start.sh -daemon config/kraft/server.properties && popd

mvn compile exec:java
CLIENT_TYPE="consumer" mvn compile exec:java

###
### Run on Minikube
###

mvn clean package

minikube start && eval "$(minikube docker-env)"
docker build -t ghcr.io/fvaleri/kafka-client:latest .
docker system prune -f; eval "$(minikube docker-env --unset)"

kubectl create -f kube/kafka.yaml
sed -E "s/imagePullPolicy: .*/imagePullPolicy: Never/g" kube/deployment.yaml | kubectl create -f -

kubectl logs -f $(kubectl get po -l app=my-producer -o name)
kubectl logs -f $(kubectl get po -l app=my-consumer -o name)

###
### Run on Kubernetes
###

mvn clean package

docker build -t ghcr.io/fvaleri/kafka-client:latest .
docker login ghcr.io -u fvaleri && docker push ghcr.io/fvaleri/kafka-client:latest
docker system prune -f; rm -rf ~/.docker/config.json

kubectl create -f kube/kafka.yaml
kubectl create -f kube/deployment.yaml

kubectl logs -f $(kubectl get po -l app=my-producer -o name)
kubectl logs -f $(kubectl get po -l app=my-consumer -o name)
```
