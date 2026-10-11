# ============================================================================
# Backend Spring Boot — build multi-stage con Maven (sin Java local)
#
# STAGE 1: compila el .jar con Maven 3.9 + Temurin 17.
# STAGE 2: imagen de ejecución mínima con JRE 17 (jammy) y usuario no-root.
#
# El build OMI'TE los tests (-DskipTests): el quality gate real es el
# test-harness.ps1 local, que corre las 165 pruebas contra MySQL.
# ============================================================================

# ---------- STAGE 1: compilación Maven ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Se copia el POM y el código en un solo paso: sin go-offline el build es
# menos cacheable pero a prueba de fallos de resolución offline.
COPY pom.xml .
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- STAGE 2: runtime JRE ligero ----------
FROM eclipse-temurin:17-jre-jammy AS runtime

# Principio de mínimo privilegio: nada corre como root dentro del contenedor.
RUN groupadd --system app && useradd --system --gid app app

WORKDIR /app
COPY --from=build /app/target/veterinaria-backend-*.jar app.jar

USER app
EXPOSE 8080

# La conexión a la DB se resuelve por env (DB_HOST/DB_PORT/DB_USERNAME/DB_PASSWORD),
# con defaults en application.properties que preservan el dev local con XAMPP.
ENTRYPOINT ["java", "-jar", "app.jar"]