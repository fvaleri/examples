FROM registry.access.redhat.com/ubi8/ubi-minimal:latest

ENV APP_HOME="/opt/example"
ENV JAVA_VERSION="17"
ENV JAVA_HOME="/usr/lib/jvm/jre-$JAVA_VERSION"
ENV JAVA_OPTS="$JAVA_OPTS -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
ENV JAVA_CLASSPATH="$JAVA_CLASSPATH:$APP_HOME/lib/*:$APP_HOME:app.jar"
ENV TINI_VERSION="v0.19.0"

USER root

RUN microdnf update \
    && microdnf --setopt=install_weak_deps=0 --setopt=tsflags=nodocs install java-$JAVA_VERSION-openjdk-headless shadow-utils \
    && microdnf clean all

# add Tini
RUN curl -skL https://github.com/krallin/tini/releases/download/$TINI_VERSION/tini-amd64 -o /usr/bin/tini \
    && echo "93dcc18adc78c65a028a84799ecf8ad40c936fdfc5f2a57b1acda5a8117fa82c */usr/bin/tini" | sha256sum -c \
    && chmod +x /usr/bin/tini
ENTRYPOINT ["/usr/bin/tini", "-e", "143", "--"]

# add user with UID 1001 (group 0 to access mounted volumes and storage)
RUN useradd -r -m -u 1001 -g 0 fvaleri

RUN mkdir -p $APP_HOME
WORKDIR $APP_HOME

COPY ./target/*.jar ./app.jar
COPY ./target/lib/ ./lib/

USER 1001

# run your program under Tini
CMD java $JAVA_OPTS -cp "$JAVA_CLASSPATH" it.fvaleri.example.Main "$@"
