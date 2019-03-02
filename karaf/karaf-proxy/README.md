```sh
mvn clean install

# standalone
features:install war camel-servlet camel-http4
install -s mvn:it.fvaleri.example/karaf-proxy/0.0.1-SNAPSHOT
http:list

# fabric
profile-create --parents feature-camel my-app
profile-edit -f war my-app
profile-edit -f camel-servlet my-app
profile-edit -f camel-http4 my-app
profile-edit -b mvn:it.fvaleri.example/karaf-proxy/0.0.1-SNAPSHOT my-app
profile-display my-app
container-add-profile child0 my-app

# test
curl -H "Content-Type: application/json" http://localhost:8181/proxy/test
curl -X POST -H "Content-Type: application/json" \
  -d '{"title":"test post","body":"test post","userId":1}' http://localhost:8181/proxy/test
```
