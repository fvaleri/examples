```sh
export KEYCLOAK_HOME="/path/to/home"
$KEYCLOAK_HOME/bin/add-user.sh -u admin -p admin
$KEYCLOAK_HOME/bin/add-user-keycloak.sh -r master -u admin -p admin
mkdir -p $KEYCLOAK_HOME/servers/server0 && cp -rp $KEYCLOAK_HOME/standalone/* $KEYCLOAK_HOME/servers/server0
nohup $KEYCLOAK_HOME/bin/standalone.sh -Djboss.server.base.dir=$KEYCLOAK_HOME/servers/server0 \
  -Djboss.tx.node.id=server0 -Djboss.socket.binding.port-offset=100 -c standalone.xml >/dev/null &
# http://localhost:8180/auth/admin
# create a new Realm called quarkus, user0 with admin role and user1 with user role
# create a new Client called quarkus with Root URL http://localhost:8080/api

get_access_token() {
  local username="$1"
  local password="$2"
  if [[ -n $username && -n $password ]]; then
    local token=$(curl -ksX POST http://localhost:8180/auth/realms/test/protocol/openid-connect/token \
      -H "Content-Type: application/x-www-form-urlencoded" -d "grant_type=password" \
      -d "client_id=my-app" -d "username=$username" -d "password=$password" | jq -r '.access_token')
    printf $token;
  fi
}

mvn clean compile
mvn spring-boot:run -f ./spring-boot-keycloak-products/pom.xml
mvn spring-boot:run -f ./spring-boot-keycloak-providers/pom.xml

# unauthorized access to products (requires an access_token)
curl -v http://localhost:8081/api/products

# retry as user
curl -v -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(get_access_token user1 user1)" \
  http://localhost:8081/api/products

# forbidden access to product details (requires admin user)
curl -v -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(get_access_token user1 user1)" \
  http://localhost:8081/api/products/1

# retry as admin
curl -v -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(get_access_token user0 user0)" \
  http://localhost:8081/api/products/1
```
