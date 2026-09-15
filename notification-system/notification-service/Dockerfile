FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml ./
COPY notification-common/pom.xml notification-common/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY notification-worker/pom.xml notification-worker/pom.xml
RUN mvn -B -q -pl notification-service -am dependency:go-offline
COPY notification-common/src notification-common/src
COPY notification-service/src notification-service/src
RUN mvn -B -q -pl notification-service -am clean package -DskipTests
RUN find notification-service/target -maxdepth 1 -name '*.jar' ! -name '*.original' -exec cp {} app.jar \;

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=build /build/app.jar ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
