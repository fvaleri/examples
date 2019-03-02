```sh
bin/artemis create servers/server1 --name server1 --port-offset 0 \
  --user admin --password changeit --allow-anonymous
servers/server1/bin/artemis-service start

MAVEN_OPTS="-Xmx256m"; mvn compile exec:java -q
```
