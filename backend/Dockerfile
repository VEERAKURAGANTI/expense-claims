FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY backend/pom.xml ./pom.xml
COPY backend/mvnw ./mvnw
COPY backend/.mvn ./.mvn
COPY backend/src ./src

RUN chmod +x mvnw && ./mvnw clean package -DskipTests

EXPOSE 8080

CMD ["sh", "-c", "java -jar target/*.jar"]