FROM adoptopenjdk/openjdk13:alpine-jre
LABEL maintainer="David Jaiyeola<david.jaiyeola@andela.com>"
# Add the service itself
ADD target/proxy-service-1.0-SNAPSHOT.jar /usr/share/proxy-service/proxy-service-1.0-SNAPSHOT.jar
ENV JAVA_OPTS=""
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /usr/share/proxy-service/proxy-service-1.0-SNAPSHOT.jar" ]
