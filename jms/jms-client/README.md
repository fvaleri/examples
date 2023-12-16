```sh
mvn compile exec:java
CLIENT_TYPE="consumer" mvn compile exec:java

# build and run on Kube
mvn clean package

docker build -t ghcr.io/fvaleri/jms-client:latest .
docker login ghcr.io -u fvaleri 
docker push ghcr.io/fvaleri/jms-client:latest
rm -rf ~/.docker/config.json

kubectl create -f jms-consumer.yaml
kubectl create -f jms-producer.yaml
```
