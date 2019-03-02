FROM alpine:3.12
ENV HOME="/opt/app"
RUN mkdir -p $HOME
COPY /usr/local/lib/libmicrohttpd.so* /usr/local/lib/
COPY ./build/hello-rest-c $HOME
USER 1001
WORKDIR $HOME
EXPOSE 8080
CMD ["/opt/app/hello-rest-c", "--debug"]
