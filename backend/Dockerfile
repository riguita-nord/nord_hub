FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S nordhub && adduser -S nordhub -G nordhub
WORKDIR /app
COPY --from=build /build/target/nord-hub-0.2.0-SNAPSHOT.jar /app/nord-hub.jar
USER nordhub:nordhub
EXPOSE 8081
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/nord-hub.jar"]
