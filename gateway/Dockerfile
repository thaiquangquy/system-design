FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests
RUN find target -maxdepth 1 -name '*.jar' ! -name '*.original' -exec cp {} app.jar \;

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
COPY --from=build /build/app.jar ./app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
