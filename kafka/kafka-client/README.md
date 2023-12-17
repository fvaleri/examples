```sh
mvn compile exec:java
CLIENT_TYPE="consumer" mvn compile exec:java

# build and run on Minikube
mvn clean package
minikube start
eval "$(minikube docker-env)" && docker build -t ghcr.io/fvaleri/kafka-client:latest .
docker system prune -f; eval "$(minikube docker-env --unset)"
sed -E "s/imagePullPolicy: .*/imagePullPolicy: Never/g" src/main/kube/deployment.yaml | kubectl create -f -
kubectl logs -f $(kubectl get po -l app=my-producer -o name)
kubectl logs -f $(kubectl get po -l app=my-consumer -o name)

# build and run on Kubernetes
mvn clean package
docker build -t ghcr.io/fvaleri/kafka-client:latest .
docker login ghcr.io -u fvaleri && docker push ghcr.io/fvaleri/kafka-client:latest
docker system prune -f; rm -rf ~/.docker/config.json
kubectl create -f src/main/kube/deployment.yaml
kubectl logs -f $(kubectl get po -l app=my-producer -o name)
kubectl logs -f $(kubectl get po -l app=my-consumer -o name)
```
