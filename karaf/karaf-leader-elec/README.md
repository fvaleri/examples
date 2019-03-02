```sh
mvn clean install

bin/start
bin/client
create --clean --wait-for-provisioning --bind-address localhost --resolver manualip \
  --global-resolver manualip --manual-ip 127.0.0.1 --zookeeper-password admin
container-remove-profile root jboss-fuse-full

profile-import file:///karaf-leader-elec-profile/target/karaf-leader-elec-profile-0.0.1-SNAPSHOT-solution.zip
container-create-child --profile quartz root child1
container-create-child --profile quartz root child2
```
