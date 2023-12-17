```sh
mvn compile exec:java
CLIENT_TYPE="consumer" mvn compile exec:java

# build and run on Kube
mvn clean package
docker build -t ghcr.io/fvaleri/kafka-client:latest .
docker login ghcr.io -u fvaleri 
docker push ghcr.io/fvaleri/kafka-client:latest
rm -rf ~/.docker/config.json
kubectl create -f kube/deployment.yaml
```
