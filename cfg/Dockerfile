FROM mcr.microsoft.com/java/jdk:17-zulu-alpine
LABEL mantainer="Hispam" \
      build="docker build --build-arg nombreArtefacto=artefacto -t microservicio:1.0 -f Dockerfile ." \
      run="docker run -dit --restart on-failure:5 --name microservicio -p 8080:8080 microservicio:1.0"
ARG nombreArtefacto=artefacto.jar
ENV nombreArtefacto ${nombreArtefacto}
RUN apk update && apk upgrade \
	&& apk add ca-certificates \
	&& update-ca-certificates \
# Change TimeZone
	&& apk add --update tzdata
ENV TZ=America/Lima
# Copy *.jar and resources files
COPY ${nombreArtefacto} /opt/
WORKDIR /opt/
# Run service java
CMD ["sh","-c","java -jar ${nombreArtefacto}"]
# Clean APK cache
RUN rm -rf /var/cache/apk/*