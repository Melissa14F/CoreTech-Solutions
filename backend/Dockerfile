# API de CoreTech (RNF-11): compila con Maven y ejecuta con un JRE 17, sin privilegios de administrador.
FROM eclipse-temurin:17-jdk-alpine AS compilar
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S coretech && adduser -S coretech -G coretech \
    && mkdir -p /datos/archivos && chown -R coretech:coretech /datos
WORKDIR /app
COPY --from=compilar /app/target/*.jar app.jar
USER coretech
ENV SPRING_PROFILES_ACTIVE=prod \
    CORETECH_ARCHIVOS=/datos/archivos \
    TZ=America/Bogota
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
