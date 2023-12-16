```sh
mvn clean spring-boot:run

# build and run
mvn clean package -DskipTests
java -jar target/spring-boot-*.jar

# build and run on K8s
mvn clean package -DskipTests
docker login registry.redhat.io
docker login ghcr.io
docker build -f Dockerfile -t ghcr.io/fvaleri/spring-boot-app:latest .
docker push ghcr.io/fvaleri/spring-boot-app:latest
rm -rf $HOME/.docker/config.json
kubectl create ns test
kubectl create -f deploy.yaml -n test
```
