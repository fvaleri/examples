```sh
mvn quarkus:dev -Ddebug=false
# http://localhost:8080/q/dev

# build and run
mvn clean package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar

# build Linux executable and run
mvn clean package -DskipTests -Pnative
./target/*-runner

# build Linux executable and run on K8s
mvn clean package -DskipTests -Pnative
docker login quay.io -u fvaleri
docker build -f Dockerfile -t quay.io/fvaleri/quarkus-app:latest .
docker push quay.io/fvaleri/quarkus-app:latest
rm -rf $HOME/.docker/config.json

kubectl create ns test
kubectl create -f deploy.yaml -n test
```
