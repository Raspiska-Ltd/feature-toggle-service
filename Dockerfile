FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

RUN mkdir -p /app/data

COPY build/libs/*.jar app.jar

EXPOSE 8090

ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
