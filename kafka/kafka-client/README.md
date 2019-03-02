```sh
mvn compile exec:java
CLIENT_TYPE="consumer" mvn compile exec:java

# build and run on Kube
mvn clean package

docker build -t quay.io/fvaleri/kafka-client:latest .
docker login quay.io -u fvaleri 
docker push quay.io/fvaleri/kafka-client:latest
rm -rf ~/.docker/config.json

kubectl create -f kafka-consumer.yaml
kubectl create -f kafka-producer.yaml
```
