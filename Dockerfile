# Giai đoạn 1: Build source code thành file .jar
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Giai đoạn 2: Chạy file .jar
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/do_an_ck_J2EE-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]