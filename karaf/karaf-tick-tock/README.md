```sh
mvn clean install

# standalone
install -s mvn:it.fvaleri.example/karaf-tick-tock-tick/0.0.1-SNAPSHOT
install -s mvn:it.fvaleri.example/karaf-tick-tock-tock/0.0.1-SNAPSHOT

config:edit it.fvaleri.example.tick
config:property-set delay 1000
config:update

# fabric
profile-create --parents default my-profile
profile-edit -b mvn:it.fvaleri.example/karaf-tick-tock-tick/0.0.1-SNAPSHOT my-profile
profile-edit -b mvn:it.fvaleri.example/karaf-tick-tock-tock/0.0.1-SNAPSHOT my-profile
profile-edit -p it.fvaleri.example.tick/delay=1000 my-profile

profile-list
profile-display my-profile

container-add-profile child1 my-profile
container-connect child1
```
