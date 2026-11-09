FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY . .
ARG MODULE
RUN mvn -B -pl ${MODULE} -am package -DskipTests && cp ${MODULE}/target/${MODULE}-1.0.0.jar /app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 app
COPY --from=build /app.jar /app/app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
