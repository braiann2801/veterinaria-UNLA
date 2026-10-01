# AGENTS.md — Protocolo de Desarrollo y Arquitectura del Sistema
> Proyecto: Sistema de Gestión Veterinaria y Guardería Canina (UNLa)
> Repositorio: veterinaria-UNLA

---

## 1. Topología del Sistema y Stack Tecnológico

- **Frontend:** Next.js (React / Tailwind CSS / shadcn/ui) ejecutándose en puerto `3000` con compilador Webpack (`next dev --webpack`).
- **Backend:** Java 17+ con Spring Boot 3.x (Spring Web, Spring Data JPA, Lombok, Validation) en puerto `8080`.
- **Base de Datos:** MySQL 8.0 relacional en puerto `3306` (Base: `veterinaria_db`).
- **Comunicación:** REST API vía JSON desacoplada. Configuración obligatoria de CORS para `http://localhost:3000` y subdominios `*.vercel.app`.

---

## 2. Reglas de Negocio Inviolables (Core Domain)

1. **Aforo de Guardería:** Límite máximo e innegociable de 10 perros concurrentes por día. Ningún endpoint ni flujo de UI puede confirmar una reserva si `cuposOcupados >= 10`.
2. **Turnos Clínicos:** Bloques estructurados de 45 minutos de atención médica seguidos obligatoriamente de 15 minutos de desinfección/limpieza antes del próximo turno.
3. **Desacople Clínico-Contable:** La consulta médica registra anamnesis, diagnóstico, tratamiento y notas de conducta. NO almacena cobros ni medios de pago en la vista del veterinario; genera una comanda de cobro diferida al mostrador.
4. **Relación Tutor-Mascota ($N:M$):** Un tutor puede tener $N$ mascotas; una mascota puede tener $M$ tutores/cotutores. Cada tupla debe registrar explícitamente el flag booleano `autorizado_retiro`.
5. **Caja Multipago y Retiros:** La liquidación permite pagos combinados (Efectivo, Transferencia, Débito, Crédito, Seña previa imputada por mascota). Todo egreso de guardería con saldo deudor pendiente dispara alerta visual de morosidad.

---

## 3. Directivas de UI/UX y Diseño Responsive (Mobile-First)

Todo desarrollo en el frontend debe cumplir estrictamente con los siguientes estándares de adaptabilidad:

1. **Enfoque Mobile-First:** Diseñar y estructurar los layouts pensando primero en pantallas reducidas (`viewport < 640px`) y escalar progresivamente hacia tablets y desktops usando prefijos de Tailwind (`sm:`, `md:`, `lg:`, `xl:`).
2. **Navegación Móvil:**
   - En pantallas móviles (`< md`), la barra lateral de navegación debe colapsarse en un menú hamburguesa o menú inferior accesible con el pulgar (*bottom navigation bar*).
   - En escritorio (`>= md`), mostrar la barra lateral fija o expandible.
3. **Tablas de Datos y Grillas:**
   - Evitar el desbordamiento horizontal (*horizontal scroll*) en páginas completas.
   - En celulares, las tablas densas (turnos, historial clínico, pacientes) deben transformarse en tarjetas individuales (*cards* apiladas) o encapsularse en contenedores con scroll horizontal localizado (`overflow-x-auto`).
4. **Formularios e Inputs Táctiles:**
   - Los campos de entrada y botones deben tener un área mínima de toque de 44x44 px (`min-h-[44px]`) para facilitar la interacción táctil.
   - Modales y diálogos (`shadcn/ui Sheet` o `Dialog`) deben ocupar el ancho completo o adaptarse como paneles inferiores deslizantes (*bottom sheets*) en pantallas pequeñas.

---

## 4. Dinámica de Trabajo y Roles de Agentes

El flujo de trabajo sigue una arquitectura de agentes especializados con división de responsabilidades:

### 4.1. Agente Orquestador (Team Lead)
- **Función:** Descompone las épicas en tareas atómicas, coordina la interacción entre agentes y evita el desborde de ventana de contexto delegando tareas operativas.
- **Memoria de Ejecución:** No almacena historiales extensos en memoria; consulta y actualiza el archivo `PROGRESS.md` al inicio y fin de cada tarea.
- **Gobernanza sobre `AGENTS.md`:** 
  - Solo puede sugerir cambios a este archivo ante dependencias no previstas o refactorizaciones estructurales.
  - **REGLA CRÍTICA:** Queda estrictamente prohibido sobreescribir `AGENTS.md` de forma autónoma. Debe presentar la propuesta de cambio en formato *diff* y esperar la confirmación explícita del usuario (`braiann2801`) antes de aplicar cualquier edición.

### 4.2. Agente Implementador (Code Writer)
- **Función:** Escribe el código en Next.js (migración de mockData a llamadas `fetch` y vistas responsive) o en Spring Boot (entidades, repositorios, servicios y controladores).
- **Alcance:** Solo modifica los archivos necesarios para la tarea puntual asignada por el Orquestador. No toca archivos ajenos al alcance de la tarea.
- **Restricción:** No puede commitear cambios si el Agente Revisor no aprobó la solución o si falla el script de tests.

### 4.3. Agente Revisor (Code Reviewer)
- **Función:** Inspecciona el código generado por el Implementador antes de darlo por listo.
- **Criterios de Aprobación:**
  - Código desacoplado, escalable y con nombres semánticos.
  - Verificación de diseño responsive y usabilidad en pantallas móviles (sin rupturas de layout ni scrolls no deseados).
  - Arquitectura en capas estricta en Spring Boot (`Controller` -> `Service` -> `Repository`).
  - Uso de DTOs para evitar ciclos infinitos de serialización Jackson en relaciones bidireccionales ($N:M$).
  - Manejo de errores controlados (`try/catch`, respuestas HTTP semánticas `400`, `404`, `409`, `500`).
  - Componentes de React sin fugas de memoria y con manejo de estados de carga (`loading`) y error.

### 4.4. Agente Test Harness (`test-harness.ps1` / `init.sh`)
- **Función:** Barrera de control automatizada (*Quality Gate*).
- **Comportamiento:** Si alguna prueba o verificación de compilación falla, el flujo se detiene por completo: **no se aplican cambios, no se hace commit y se emite un reporte inmediato de error**.

---

## 5. Harness de Pruebas Automatizadas

El archivo `test-harness.ps1` (o `init.sh` en Linux/macOS) debe validar los siguientes puntos en cada ciclo de integración:

1. **Frontend Integrity:** Compilación limpia de Next.js (`npm run build` o chequeo de tipos con `tsc --noEmit`).
2. **Backend Unit & Domain Tests:** Ejecución de suites de prueba (`mvn test` / `mvnw.cmd test`):
   - Prueba unitaria de rechazo de reserva cuando cupo sea igual a 10.
   - Prueba de validación de turnos (intervalo mínimo de 15 minutos).
   - Prueba de autorización de retiro por cotutor.
3. **Database Contract Check:** Validación de que los nombres de tablas y columnas referenciadas coincidan con el esquema relacional 3FN.

---

## 6. Protocolo de Persistencia de Contexto (`PROGRESS.md`)

Para mantener el consumo de tokens bajo control y permitir retomar el trabajo en cualquier momento, se mantiene un log estructurado en `PROGRESS.md`:

```markdown
# PROGRESS.md

## Estado Actual
- Tarea en curso: [ID y Descripción]
- Agente asignado: [Implementador / Revisor / Harness]
- Bloqueantes: [Ninguno / Detalle del error]

## Tareas Completadas
- [x] Setup inicial repositorio y frontend base v0.
- [x] Configuración de AGENTS.md y reglas de gobernanza.
- [x] Pipeline local validado (XAMPP + Next.js + test-harness.ps1).

## Registro de Cambios Técnicos
- [Commit Hash / Archivo modificado]: [Breve justificación del cambio]