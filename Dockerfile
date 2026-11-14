FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY . .
RUN javac -d out src/Servidor.java
EXPOSE 8080
CMD ["java", "-cp", "out", "Servidor"]