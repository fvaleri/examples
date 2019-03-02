FROM registry.redhat.io/fuse7/fuse-java-openshift:1.7
COPY target/spring-boot-*.jar /app.jar

EXPOSE 8080
USER nonroot

CMD ["java", "-jar", "/app.jar"]
