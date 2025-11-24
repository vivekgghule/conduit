FROM eclipse-temurin:21-jre
WORKDIR /app
# Copy the built control plane jar (versioned by Maven) into the image
COPY ../../egress-pilot-control-plane-service/target/egress-pilot-control-plane-service-*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java","-jar","/app/app.jar"]
