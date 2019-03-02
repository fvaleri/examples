```sh
mvn clean spring-boot:run

kubectl create -f - <<EOF
apiVersion: v1
kind: ServiceAccount
metadata:
  name: spring-boot-leader-elec
---
apiVersion: v1
kind: RoleBinding
metadata:
  name: spring-boot-leader-elec
roleRef:
  # enable resources edit from whithin the pod
  name: edit
subjects:
  - kind: ServiceAccount
    name: spring-boot-leader-elec
EOF

mvn clean k8s:deploy -Pkube
#mvn k8s:undeploy -Pkube

# scale the service and observe that only one is active (singleton)
kubectl scale dc spring-boot-leader-elec --replicas=2
```
