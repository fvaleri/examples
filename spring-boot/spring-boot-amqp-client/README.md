```sh
bin/artemis create hosts/host0 \
  --name host0 \
  --user admin \
  --password admin \
  --allow-anonymous \
  --queues example.foo \
  --no-amqp-acceptor \
  --no-hornetq-acceptor \
  --no-mqtt-acceptor \
  --no-stomp-acceptor

#qdrouterd -c qdrouterd.conf
mvn clean spring-boot:run
```
