```sh
mvn compile
mvn exec:java -Dexec.mainClass="it.fvaleri.example.EchoServer" -Dexec.args="8000"
mvn exec:java -Dexec.mainClass="it.fvaleri.example.EchoClient" -Dexec.args="localhost 8000"
```
