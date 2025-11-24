FROM eclipse-temurin:21-jre
WORKDIR /app
# Copy the built sample client jar (versioned by Maven) into the image
COPY ../../egress-pilot-sample-client-service/target/egress-pilot-sample-client-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
