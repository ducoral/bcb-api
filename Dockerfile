# ---------- Estágio 1: build ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------- Estágio 2: runtime ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

# Roda como usuário sem privilégios
RUN useradd -r -u 1001 appuser
USER appuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]