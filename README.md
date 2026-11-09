# PorteriaYa v2

Portería digital para conjuntos residenciales, con un asistente **RAG** que responde sobre el
reglamento y los protocolos del conjunto citando sus fuentes.

Visitas preautorizadas con código de 6 dígitos y QR, aprobación en vivo de visitas no anunciadas,
control de permanencia y salida, paquetes con escalamiento automático, lista de personas no
permitidas y bitácora de auditoría.

Principio rector: **Spring Boot es el único dueño de reglas, tiempos y consultas a la IA.**
n8n solo orquesta mensajes y alertas. La IA solo informa; nunca decide un ingreso.

El contrato completo (reglas de negocio, estados, rutas, convenciones) vive en [`CLAUDE.md`](CLAUDE.md).

---

## Arranque rápido

```bash
cp .env.example .env     # pon valores reales: ADMIN_PIN, PORTERIA_SECRET, contraseña de la BD

docker compose up -d     # postgres, qdrant, n8n, mailpit

# Ollama va en su propio perfil porque carga modelos en RAM.
# Solo hace falta para el asistente.
docker compose up -d ollama
docker compose exec ollama ollama pull nomic-embed-text   # una sola vez, ~274 MB

./mvnw spring-boot:run -Dspring-boot.run.profiles=demo    # reloj simulado + datos de ejemplo
```

Abre **http://localhost:8080** y elige un rol.

| Servicio | URL |
| --- | --- |
| Aplicación | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |
| Mailpit (correos de prueba) | http://localhost:8025 |
| n8n | http://localhost:5678 |
| Qdrant | http://localhost:6333/dashboard |

Cuando termines con el asistente: `docker compose stop ollama` para liberar la RAM.

La app corre **fuera** de Compose, así que n8n la alcanza en `http://host.docker.internal:8080`,
no en `localhost`. Esa URL sale de `PORTERIA_BASE_URL` en `.env`.

---

## Las tres vistas

- **`/residente.html`** — crear autorizaciones (con su código y QR), ver las propias, cancelarlas,
  consultar paquetes y preguntarle al asistente.
- **`/porteria.html`** — pensada para tablet. Validar código, registrar ingreso y salida, visita no
  anunciada, recibir y entregar paquetes, ver quién está dentro, consultar protocolos.
- **`/admin.html`** — métricas, visitas por hora, paquetes por antigüedad, gestión de documentos del
  asistente, preguntas sin respuesta, lista de no permitidos y bitácora con filtros.

Validar un código y registrar el ingreso son **dos pasos distintos**: validar no consume el uso, así
que el vigilante puede consultar sin consecuencias.

---

## Las decisiones que explican el diseño

Siete decisiones de contrato se cerraron durante el desarrollo y están documentadas en `CLAUDE.md`.
Estas cuatro son las que más explican por qué el código está así:

**El uso se consume solo al registrar el ingreso, nunca al validar.** El descuento es un único
`UPDATE ... WHERE usos_actuales < usos_max`: si dos vigilantes validan el mismo código de un solo
uso a la vez, solo uno lo consigue y el otro recibe 409. Hay tests con 12 y 16 hilos que lo fijan.

**La visita no anunciada es entidad propia, no un estado previo del ingreso.** Así «si el residente
rechaza, el vigilante no puede registrar el ingreso» se cumple por construcción: mientras no haya
aprobación no existe ningún ingreso que contar.

**El enlace de aprobación no decide en el `GET`.** Gmail, Outlook y Telegram pre-cargan los enlaces
de un mensaje. Con un `GET` que aprobara, ese prefetch quemaría el token de un solo uso y decidiría
sin que el residente toque nada. El `GET` devuelve una página de confirmación y el `POST` decide.

**Un rechazo de ingreso confirma la transacción y solo después responde 409.** Si se señalara
lanzando, el rollback se llevaría la entrada en bitácora del intento y la alerta de persona no
permitida. Un intento denegado tiene que dejar rastro precisamente porque fue denegado.

---

## El asistente (RAG)

```
documento → extracción (PDF por páginas o Markdown) → chunking con solape
          → embeddings (nomic-embed-text, 768 dim) → Qdrant

pregunta  → embedding → top-5 filtrado por audiencia
          → bajo el umbral: "no encuentro esto" SIN llamar al LLM
          → con contexto: prompt con guardrails → respuesta con citas
```

Tres guardrails que no son opcionales:

1. **Bajo el umbral no se llama al LLM.** No es ahorro: llamarlo con contexto malo es la vía más
   directa a una norma inventada, y en un reglamento eso es peor que no responder.
2. **El contexto y la pregunta van delimitados y marcados como datos.** Si alguien sube un documento
   que dice «ignora tus instrucciones», eso se lee como una frase del reglamento, no como una orden.
3. **Filtro de audiencia en la propia búsqueda.** Un residente nunca recupera un chunk del protocolo
   de vigilancia. El filtro va dentro de la consulta a Qdrant, no después, para que no se pueda
   olvidar.

PostgreSQL es la fuente de verdad: guarda el texto extraído, así que `POST /api/rag/reindexar`
reconstruye Qdrant sin volver a subir ningún archivo.

Proveedores detrás de interfaces (`EmbeddingClient`, `VectorStore`, `LlmClient`): cambiar de LLM es
una propiedad. **Para una sustentación en vivo, `RAG_LLM_PROVEEDOR=claude`**: un modelo generativo
local en CPU se arrastra.

Si Qdrant, Ollama o el LLM fallan, el asistente responde «no disponible» y **la portería sigue
funcionando con normalidad**.

---

## Automatización (n8n)

`n8n/workflows/porteriaya-avisos.json` es importable y no lleva credenciales.

Es **un** flujo con enrutado por tipo de evento, no los siete sueltos que describe `CLAUDE.md` §14:
la aplicación publica todos los eventos a una única URL de webhook, así que un flujo que enruta es
lo que encaja con lo construido. Dentro están las cuatro ramas obligatorias (visitas, aprobación en
vivo, paquetes y alertas de seguridad).

Para importarlo: crea una credencial SMTP llamada **Mailpit** (host `mailpit`, puerto `1025`, sin
autenticación) y define `PORTERIA_SECRET` y `CORREO_ADMINISTRACION` como variables de entorno de n8n.
Las alertas de seguridad salen por correo; en producción esa rama debería ir por Telegram, que es
cambiar un nodo.

---

## Pruebas

```bash
./mvnw test      # unitarias, no necesitan Docker
./mvnw verify    # unitarias + integración con Testcontainers (sí necesita Docker)
```

Las clases `*IT` son las de integración y solo corren en `verify`.

### Estado de verificación

Es un MVP y no la entrega final. Lo que está comprobado y lo que no:

| | Estado |
| --- | --- |
| `./mvnw test` (unitarias) | ✅ en verde |
| Compilación completa | ✅ |
| Pasos 1 y 2 con `./mvnw verify` | ✅ verificados en su momento |
| `./mvnw verify` para los pasos 3 y 4 | ⚠️ **sin ejecutar**: Docker estaba abajo |
| RAG, vistas y flujo de n8n | ⚠️ **sin probar contra el sistema real** |
| Evaluación RAG de 20 preguntas (`CLAUDE.md` §11) | ⚠️ sin escribir; el umbral `0.55` está sin calibrar |
| `POST /api/demo/avanzar` | ⚠️ sin prueba automática |

**Lo primero al retomar:** levantar Docker y correr `./mvnw verify`, luego recorrer el guion de demo
de `CLAUDE.md` §19 en el navegador. Es razonable esperar ajustes en el RAG y en el JSON de n8n, que
nunca han corrido contra Qdrant, Ollama ni n8n de verdad.

---

## Seguridad: limitación conocida

El proyecto **no tiene inicio de sesión**. El rol viaja en las cabeceras `X-Rol` y `X-Actor`, que
son **autodeclaradas**: cualquiera puede enviarlas con `curl`. La validación por rol evita errores
de uso, **no es un control de autenticación**.

Lo único que protege de verdad las operaciones destructivas es el PIN de administrador
(`X-Admin-Pin` contra `ADMIN_PIN` del `.env`), exigido en subida y borrado de documentos,
reindexación, bitácora y lista de no permitidos. El límite de frecuencia por actor es evadible
cambiando `X-Actor`.

Los secretos viven solo en `.env`, que no se versiona. El secreto compartido con n8n es **distinto**
del PIN: el de n8n nunca llega al navegador.

---

## Privacidad

Del visitante se guardan únicamente el **nombre** y los **últimos 4 dígitos** del documento
(Ley 1581 de 2012). El documento completo no entra a la base de datos ni siquiera en la petición: la
API rechaza con 400 cualquier campo de documento que no sean exactamente 4 dígitos.

No hay datos personales de visitantes en logs, vectores, prompts ni en el historial del chat. Las
preguntas al asistente se sanitizan antes de guardarse: se eliminan correos, teléfonos, documentos y
tokens. Los **nombres** no se pueden detectar de forma fiable sin un modelo de entidades, y eso está
documentado como limitación en el propio código.

---

## Guion de demo

Con el perfil `demo` (30 apartamentos, autorizaciones de los cuatro tipos, paquetes de distinta
antigüedad, una persona no permitida y los dos documentos del asistente ya cargados):

1. El residente de **A-101** autoriza a un amigo y recibe el código.
2. El vigilante valida el código y registra el ingreso; el residente recibe el aviso en Mailpit.
3. Llega alguien no anunciado; el residente aprueba desde el enlace del correo.
4. Llega un paquete para A-101.
5. `POST /api/demo/avanzar?minutos=10080` adelanta una semana: aparecen los recordatorios de 24 h y
   72 h, el escalamiento a los 7 días y los cierres automáticos. La respuesta del endpoint dice
   exactamente qué provocó.
6. Se intenta ingresar a **Ivan Castano / 9931**, que está en la lista: el ingreso se niega y la
   alerta sale a administración.
7. El residente pregunta **«¿cuál es el horario de mudanzas?»** y el asistente responde citando el
   reglamento. Luego pregunta algo que no está en los documentos y recibe «no encuentro esto».
8. El mismo residente pregunta por un protocolo de vigilancia: no lo recupera, porque el filtro de
   audiencia no se lo permite.

---

## Estructura

```
src/main/java/co/porteriaya/
  config/        Clock, Async, Scheduling, CORS, propiedades tipadas, tareas programadas
  autorizacion/  tipos, recurrencia, consumo atómico del uso
  porteria/      validación en 5 pasos, ingresos y salidas
  solicitud/     visitas no anunciadas y token de aprobación
  paquete/       recepción, entrega y avisos escalonados
  nopermitido/   lista y cotejo en dos niveles
  bitacora/      auditoría de solo lectura
  dashboard/     métricas agregadas
  evento/        publicador hacia n8n, tolerante a fallos
  rag/           documento, embedding, vector, llm, consulta
  demo/          sembradores y reloj simulado (solo perfil demo)
  common/        errores, roles, normalización

src/main/resources/
  static/        index, residente, porteria, admin + js/api.js
  db/migration/  V1 esquema, V2 char→varchar, V3 contenido del documento
  rag/seed/      reglamento y protocolo de ejemplo
n8n/workflows/   flujo importable
```

## Requisitos

JDK 21, Docker, y nada más: el proyecto trae el wrapper de Maven (`./mvnw`).
