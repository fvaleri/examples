```sh
mvn quarkus:dev -Ddebug=false
# http://localhost:8080/q/dev
# http://localhost:8080/q/swagger-ui

curl http://localhost:8080/api/greet
curl http://localhost:8080/api/greet/fede

# run integration test
mvn clean test -Dtest=GreetingResourceIT

# build and run
mvn clean package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar

# build Linux executable and run
mvn clean package -DskipTests -Pnative
./target/*-runner

# build Linux executable and run on K8s
mvn clean package -DskipTests -Pnative
docker login ghcr.io -u fvaleri
docker build -f Dockerfile -t ghcr.io/fvaleri/quarkus-app:latest .
docker push ghcr.io/fvaleri/quarkus-app:latest
rm -rf $HOME/.docker/config.json

kubectl create ns test
# deploy on minikube
kubectl create -f deploy-mk.yaml -n test
export DNS="$(kubectl get ingress quarkus-app -o jsonpath={.status.loadBalancer.ingress[0].ip}) \
  $(kubectl get ingress quarkus-app -o jsonpath={.spec.rules[0].host})" && sudo bash -c "echo '$DNS' >>/etc/hosts"
curl http://quarkus-app.minikube.io/api/greet
# deploy on ocp
kubectl create -f deploy-ocp.yaml -n test
curl http://$(kubectl get route quarkus-app -o jsonpath={.status.ingress[0].host})/api/greet
```
