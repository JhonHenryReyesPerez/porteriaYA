# PorteriaYa v2

Portería digital para conjuntos residenciales: visitas preautorizadas con código de 6 dígitos o QR,
aprobación en vivo de visitas no anunciadas, control de permanencia y salida, paquetes con
escalamiento, lista de personas no permitidas y bitácora de auditoría. La v2 añade un asistente
**RAG** que responde sobre el reglamento y los protocolos del conjunto citando sus fuentes.

Principio rector: **Spring Boot es el único dueño de reglas, tiempos y consultas a la IA.**
n8n solo orquesta mensajes y alertas. La IA solo informa; nunca decide un ingreso.

El contrato completo (reglas de negocio, estados, rutas y convenciones) vive en [`CLAUDE.md`](CLAUDE.md).

## Requisitos

- JDK 21
- Docker Desktop (o un daemon de Docker en marcha)
- No hace falta instalar Maven: el proyecto trae wrapper (`./mvnw`)

## Cómo levantarlo

```bash
cp .env.example .env                 # y pon valores reales: ADMIN_PIN, PORTERIA_SECRET, contrasena de la BD

docker compose up -d                 # postgres, qdrant, n8n, mailpit

# Ollama va en el perfil `rag` porque carga modelos en RAM: solo se levanta al usar el RAG
docker compose up -d ollama                               # nombrarla activa su perfil
docker compose exec ollama ollama pull nomic-embed-text   # una sola vez, ~274 MB
docker compose stop ollama                                # al terminar, libera la RAM

./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo    # reloj simulado + datos de demo
```

| Servicio | URL |
| --- | --- |
| Swagger | http://localhost:8080/swagger-ui.html |
| Mailpit | http://localhost:8025 |
| n8n | http://localhost:5678 |
| Qdrant | http://localhost:6333/dashboard |

La app corre **fuera** de Compose, así que n8n la alcanza en `http://host.docker.internal:8080`,
no en `localhost`. Esa URL sale de `PORTERIA_BASE_URL` en `.env`.
Para levantar también la app dentro de Compose: `docker compose --profile app up -d`.

## Pruebas

```bash
./mvnw test      # unitarias, no necesitan Docker
./mvnw verify    # unitarias + integración con Testcontainers (sí necesita Docker)
```

Las clases `*IT` son las de integración y solo corren en `verify`.

## Estado de avance

El orden de construcción está en `CLAUDE.md` §17.

- [x] **1. Infraestructura y esqueleto** — Compose, esqueleto Spring, Flyway con el esquema V1, semilla de demo
- [x] **2. Entidades, autorizaciones con recurrencia, validación con uso atómico, bitácora**
- [x] **3. Ingresos y salidas, solicitud no anunciada con token, paquetes, `@Scheduled`, eventos**
- [x] **4. No permitidas, `/dashboard/stats`, Swagger completo**
- [ ] 5. n8n: aprobación en vivo, paquetes, alertas de seguridad
- [ ] 6. Interfaz de las tres vistas
- [ ] 7. RAG: ingesta, embeddings, Qdrant, consulta con citas, evaluación
- [ ] 8. Chat en la UI y flujos 5 a 7 de n8n
- [ ] 9. Documentación y capturas

Lo que el paso 2 deja a medias a propósito: el paso 5 de la validación (`YA_DENTRO`) necesita
el repositorio de ingresos, y los eventos hacia n8n necesitan el publicador; las dos cosas llegan
en el paso 3. `POST /api/demo/avanzar` tampoco existe todavía. El reloj simulado del perfil
`demo` ya está, pero el endpoint debe además disparar la pasada del scheduler (§9), y el
scheduler nace en el paso 3.

## Estado de verificación

Lo que está comprobado y lo que no, a 8 de noviembre de 2026:

| | Estado |
| --- | --- |
| `./mvnw test` (159 unitarias, sin Docker) | ✅ en verde |
| Compilación completa, incluidas las pruebas de integración | ✅ |
| `./mvnw verify` para los pasos 3 y 4 | ⚠️ **sin ejecutar**: Docker estaba abajo |
| `POST /api/demo/avanzar` | ⚠️ sin prueba automática; falta probarlo a mano |
| Pruebas de integración de los endpoints del paso 4 | ⚠️ sin escribir |

**Lo primero al retomar:** levantar Docker y correr `./mvnw verify`. `SolicitudIT`
y `PaqueteIT` están escritos pero nunca se han ejecutado, así que es razonable
esperar algún ajuste.

## Seguridad: limitación conocida

El proyecto **no tiene login**. El rol viaja en las cabeceras `X-Rol` y `X-Actor`, que son
**autodeclaradas**: cualquiera puede enviarlas con `curl`. La validación por rol evita errores de
uso, **no es un control de autenticación**.

Lo único que protege de verdad las operaciones destructivas es el PIN de administrador
(`X-Admin-Pin`, contra `ADMIN_PIN` del `.env`), exigido en subida y borrado de documentos,
reindexación, bitácora, lista de no permitidos y anulaciones. El límite de frecuencia por actor
es evadible cambiando `X-Actor`.

Los secretos viven solo en `.env`, que no se versiona. El secreto compartido con n8n
(`X-Porteria-Secret`) es **distinto** del PIN: el de n8n nunca llega al navegador.

## Privacidad

Del visitante se guardan únicamente el **nombre** y los **últimos 4 dígitos** del documento
(Ley 1581 de 2012). El documento completo nunca entra a la base de datos. No hay datos
personales de visitantes en logs, vectores, prompts ni en el historial del chat.

## Decisiones de diseño cerradas

Están escritas en `CLAUDE.md` y materializadas en la migración V1:

| Decisión | Dónde |
| --- | --- |
| El uso se consume solo en `POST /api/porteria/ingresos`; `validar` no escribe | `CLAUDE.md` §6 |
| `usos_max IS NULL` significa usos ilimitados | `V1`, `ck_autorizacion_usos_max` |
| El código es único solo mientras la autorización está activa | `V1`, `ux_autorizacion_codigo_activa` (índice parcial) |
| La visita no anunciada es entidad propia; el `Ingreso` nace solo si se aprueba | `V1`, `solicitud_no_anunciada` + `token_aprobacion` |
| El token de aprobación se guarda hasheado; `GET` confirma y `POST` ejecuta | `CLAUDE.md` §6, §8 |
| `DELETE` de una autorización es cancelación lógica | `CLAUDE.md` §8, §12 |
| Endpoints de administrador con PIN | `CLAUDE.md` §7 |

Lo que sigue abierto está en `CLAUDE.md` §20. La más urgente es el **criterio de coincidencia con
la lista de no permitidos**, que bloquea el paso 2.
