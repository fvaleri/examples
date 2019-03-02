```sh
# create fabric and deploy the broker
client
create --clean --wait-for-provisioning --zookeeper-password admin
container-remove-profile root jboss-fuse-full
container-create-child root child0
container-create-child root child1
container-list
container-change-profile child0 mq-amq
container-connect child0
activemq:dstat

# deploy app (requires java8)
mvn clean install
profile-create --parent feature-cxf --parent feature-camel-jms my-app
profile-edit -b mvn:it.fvaleri.example/karaf-soap/0.0.1-SNAPSHOT my-app
profile-edit -p org.ops4j.pax.web/org.osgi.service.http.port=9090 my-app
profile-edit -f camel-cxf my-app

container-change-profile child1 my-app
container-connect child1
list | grep -i karaf-soap-service
log:tail

# http://localhost:9090/cxf
curl -H "Content-Type: text/xml;charset=UTF-8" \
  -H "SOAPAction: http://example.fvaleri.it/reportIncident" \
  -d@src/main/resources/test.xml http://localhost:9090/cxf/ws/incident
```
