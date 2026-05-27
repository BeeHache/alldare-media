# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy all poms first to cache dependencies
COPY alldare-parent ./alldare-parent/
RUN mvn -f alldare-parent/pom.xml install -N

COPY alldare-common ./alldare-common/
RUN mvn -f alldare-common/pom.xml install -DskipTests

COPY alldare-media ./alldare-media/

WORKDIR /app/alldare-media
RUN mvn package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the built JAR from the media service target directory
COPY --from=build /app/alldare-media/target/media-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
