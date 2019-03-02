```sh
sudo dnf install libmicrohttpd-devel

make
./build/hello-rest-c --debug

curl http://localhost:8080/health
curl http://localhost:8080/metrics
curl http://localhost:8080/greet/fede
```
