# Use a base image with Java 25
FROM openjdk:25

# Set the working directory in the container
WORKDIR /app

# Copy the JAR file from the target directory into the container
COPY target/metro-timetable*.jar metro-timetable.jar

# Create a directory where the file will be saved
# Change permissions of the directory
RUN mkdir -p /tmp/timetable && chmod -R 777 /tmp/timetable

# Expose the port that your Spring Boot application will run on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "metro-timetable.jar"]