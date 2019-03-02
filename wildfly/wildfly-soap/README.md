```sh
bin/add-user.sh -u admin -p changeit
bin/add-user.sh -a -u admin -p changeit -g guest
mkdir -p servers/server1
cp -rp standalone/* servers/server1
nohup bin/standalone.sh -Djboss.server.base.dir=servers/server1 -Djboss.tx.node.id=server1 \
  -Djboss.socket.binding.port-offset=0 -c standalone-full.xml &>/dev/null &

bin/jboss-cli.sh -c <<\EOF
/subsystem=transactions:write-attribute(name=node-identifier,value=host0)
/subsystem=messaging-activemq/server=default/jms-queue=my-queue:add(entries=["java:/jms/queue/my-queue", "java:jboss/exported/jms/queue/my-queue"])
reload
EOF

mvn install -Pwildfly
#mvn clean -Pwildfly

# http://localhost:8080/services/hello?wsdl
curl -H "Content-Type: text/xml;charset=UTF-8" -d '
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="http://it.fvaleri.example/hello">
    <soapenv:Header />
    <soapenv:Body>
        <tns:writeText>
            <arg0>test</arg0>
        </tns:writeText>
    </soapenv:Body>
</soapenv:Envelope>' http://localhost:8080/services/hello
```
