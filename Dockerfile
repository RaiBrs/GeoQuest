FROM maven:3.10.0-eclipse-temurin-25-noble AS build
WORKDIR /build

COPY pom.xml ./
COPY src ./src
# HTTP integration tests read the frontend assets from the project directory.
COPY frontend ./frontend
RUN mvn -B -ntp verify \
    org.apache.maven.plugins:maven-dependency-plugin:3.11.0:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory=target/lib

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
ENV HOST=0.0.0.0 PORT=8080

COPY --from=build /build/target/*.jar ./app.jar
COPY --from=build /build/target/lib ./lib
COPY --from=build /build/frontend ./frontend

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "app.jar:lib/*", "com.raibrs.geoquest.WebApp"]
