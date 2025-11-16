# build stage
FROM maven:3.9.5-eclipse-temurin-21 as build
WORKDIR /work
COPY pom.xml .
COPY src ./src
RUN mvn -e -B -DskipTests package

# runtime stage
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /work/target/distribuidos-1.0-SNAPSHOT.jar /app/distribuidos.jar
EXPOSE 50051
ENTRYPOINT ["java","-jar","/app/distribuidos.jar"]
