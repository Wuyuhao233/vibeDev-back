FROM docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn dependency:go-offline -B -s /root/.m2/settings.xml
COPY src ./src
RUN mvn test -B -s /root/.m2/settings.xml
RUN mvn package -DskipTests -B -s /root/.m2/settings.xml

FROM docker.m.daocloud.io/library/eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
