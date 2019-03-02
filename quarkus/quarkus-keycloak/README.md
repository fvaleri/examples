```sh
export KEYCLOAK_HOME="/path/to/home"
$KEYCLOAK_HOME/bin/add-user.sh -u admin -p admin
$KEYCLOAK_HOME/bin/add-user-keycloak.sh -r master -u admin -p admin
mkdir -p $KEYCLOAK_HOME/servers/server0 && cp -rp $KEYCLOAK_HOME/standalone/* $KEYCLOAK_HOME/servers/server0
nohup $KEYCLOAK_HOME/bin/standalone.sh -Djboss.server.base.dir=$KEYCLOAK_HOME/servers/server0 \
  -Djboss.tx.node.id=server0 -Djboss.socket.binding.port-offset=100 -c standalone.xml >/dev/null &
# http://localhost:8180/auth/admin
# create a new Realm called quarkus, user0 with admin role and user1 with user role
# create a new Client called quarkus with Root URL http://localhost:8080/api and Access Type confidential
# save and take note of the Secret in Credentials tab

get_access_token() {
  local username="$1"
  local password="$2"
  if [[ -n $username && -n $password ]]; then
    local token=$(curl -ksX POST http://localhost:8180/auth/realms/quarkus/protocol/openid-connect/token \
      -d "grant_type=password" -d "client_id=my-app" -d "client_secret=f9885426-3ce0-4d2f-95bb-ee87c18ec417" \
      -d "username=$username" -d "password=$password" | jq -r '.access_token')
    printf $token;
  fi
}

mvn quarkus:dev -Ddebug=false
# http://localhost:8080/q/dev
# http://localhost:8080/q/swagger-ui

curl -v -H "Content-Type: application/json" -H "Authorization: Bearer $(get_access_token user0 user0)" \
  -d '{"title":"1984","category":"Science fiction"}' http://localhost:8080/api/books | jq
curl -v -H "Content-Type: application/json" -H "Authorization: Bearer $(get_access_token user0 user0)" \
  -d '{"title":"Alice in Wonderland","category":"Story"}' http://localhost:8080/api/books | jq

curl -v -H "Authorization: Bearer $(get_access_token user1 user1)" http://localhost:8080/api/books | jq
```
