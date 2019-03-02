```sh
# start Artemis broker
MAVEN_OPTS="-Xmx256m"; mvn clean compile camel:run
dd if=/dev/zero of=/tmp/test count=1024 bs=1048576 && mv /tmp/test target/inbox/
```
