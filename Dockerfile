# Etapa 1: Compilación del proyecto
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copiar archivos de Gradle y código fuente
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

# Dar permisos de ejecución a gradlew y compilar
RUN chmod +x gradlew
RUN ./gradlew bootJar -x test

# Etapa 2: Ejecución de la aplicación
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 1. Crear un grupo y un usuario sin privilegios (sintaxis para Alpine)
RUN addgroup -S spring && adduser -S spring -G spring

# 2. Copiar el .jar asignando directamente la propiedad al nuevo usuario
COPY --from=build --chown=spring:spring /app/build/libs/*.jar app.jar

# 3. Cambiar al usuario no-root antes de arrancar la aplicación
USER spring:spring

ENTRYPOINT ["java", "-jar", "app.jar"]