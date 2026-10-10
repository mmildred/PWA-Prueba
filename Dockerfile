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

# Copiar el JAR generado en la etapa de compilación
COPY --from=build /app/build/libs/*.jar app.jar

# Exponer el puerto predeterminado
EXPOSE 8080

# Comando para arrancar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]