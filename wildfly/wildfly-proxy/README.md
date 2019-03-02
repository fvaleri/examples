```sh
mvn install -Pwildfly
#mvn clean -Pwildfly
curl -iv -H "Content-Type: application/json" http://localhost:8080/eap-proxy-svc/camel1/edit/45 -o target/curl.txt
```
