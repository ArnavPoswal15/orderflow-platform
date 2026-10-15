FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY common-events common-events
COPY order-service order-service
COPY inventory-service inventory-service
COPY payment-service payment-service
RUN mvn -q -pl inventory-service -am package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/inventory-service/target/inventory-service-*-boot.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
