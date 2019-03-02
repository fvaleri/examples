```sh
# start MySQL and ActiveMQ (user: admin, pass: admin)
CREATE TABLE users (
    firstname VARCHAR(50) NOT NULL,
    lastname VARCHAR(50),
    login VARCHAR(12),
    password VARCHAR(20),
    PRIMARY KEY (login)
);

# build and run in Karaf
mvn clean install

features:addurl mvn:it.fvaleri.example/karaf-jta-activemq-features/0.0.1-SNAPSHOT/xml/features
features:install karaf-jta-activemq

# test the route
#log:set DEBUG it.fvaleri.example
#log:set DEBUG org.springframework.transaction.PlatformTransactionManager

activemq:producer --user admin --password admin \
  --destination queue://SQL_IN --persistent true --messageCount 1 \
  --message "John, Smith, jsmith, secret"
activemq:dstat
activemq:browse --amqurl tcp://localhost:61616 --user admin --password admin SQL_OUT

# if you re-send the same message it will be redelivered maximumRedeliveries times
# every time you will see a rollback and in the end the message goes to ActiveMQ.DLQ
activemq:dstat
activemq:browse --amqurl tcp://localhost:61616 --user admin --password admin ActiveMQ.DLQ

mysql testdb -u admin -p
SELECT * FROM users;
```
