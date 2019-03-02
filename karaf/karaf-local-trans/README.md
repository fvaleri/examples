```sh
# using deferred constraint to cause an Exception at commit time
CREATE TABLE snowflakes (i INT UNIQUE DEFERRABLE INITIALLY DEFERRED)
TRUNCATE snowflakes;

# build and run
mvn clean install

log:set DEBUG it.fvaleri.example
#log:set DEBUG org.springframework.transaction
features:addurl mvn:it.fvaleri.example/karaf-local-trans/0.0.1-SNAPSHOT/xml/features
features:install karaf-local-trans

log:tail
```
