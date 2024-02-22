```sh
# run on localhost
mvn compile exec:java
CLIENT_TYPE="consumer" mvn compile exec:java

# run on Kubernetes
mvn clean package
docker build -t ghcr.io/fvaleri/kafka-client:latest .
docker login ghcr.io -u fvaleri && docker push ghcr.io/fvaleri/kafka-client:latest
docker system prune -f; rm -rf ~/.docker/config.json
kubectl create -f kube/deployment.yaml
kubectl logs -f $(kubectl get po -l app=my-producer -o name)
kubectl logs -f $(kubectl get po -l app=my-consumer -o name)
```
