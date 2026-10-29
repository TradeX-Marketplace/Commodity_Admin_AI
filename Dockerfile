
FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY src ./src

RUN ./mvnw -B clean package -DskipTests

EXPOSE 9090

CMD ["sh", "-c", "java -jar target/*.jar --server.port=${PORT:-9090}"]
