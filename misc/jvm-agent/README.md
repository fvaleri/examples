```sh
mvn package

# static load
java -javaagent:agent/target/agent.jar="New string" \
  -jar application/target/application.jar
```
