FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY common-events common-events
COPY order-service order-service
COPY inventory-service inventory-service
COPY payment-service payment-service
COPY acceptance-tests acceptance-tests
RUN mvn -q -pl order-service -am package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/order-service/target/order-service-*-boot.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
