# ============ Etapa 1: build con Maven ============
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cachear dependencias antes de copiar el source (así rebuilds son rápidos)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests clean package

# ============ Etapa 2: runtime (imagen ligera) ============
FROM eclipse-temurin:17-jre
WORKDIR /opt/fondo

# JVM respeta el límite de memoria del contenedor
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

COPY --from=build /build/target/*.jar app.jar

# BD H2 y comprobantes viven aquí (bind mount ./data:/opt/fondo/data)
RUN mkdir -p /opt/fondo/data/comprobantes

EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
