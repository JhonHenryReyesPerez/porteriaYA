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

Los siete flujos de `CLAUDE.md` §14 están en `n8n/workflows/`, cada uno en su JSON importable y sin
credenciales. Son ocho archivos porque hay un **enrutador** delante:

| Archivo | Qué hace | Se dispara con |
| --- | --- | --- |
| `00-enrutador.json` | Valida `X-Porteria-Secret` y reparte el evento al flujo que le toca | webhook `porteriaya` |
| `01-visitas.json` | Código de ingreso y aviso de que el visitante entró | el enrutador |
| `02-aprobacion-en-vivo.json` | Aprobar o rechazar la visita no anunciada | el enrutador |
| `03-paquetes.json` | Recepción, recordatorios de 24 h y 72 h, escalamiento a los 7 días | el enrutador |
| `04-alertas-seguridad.json` | Persona no permitida, permanencia excedida, salida no registrada | el enrutador |
| `05-ingesta-documentos.json` | Descarga un documento por URL y lo sube al asistente | webhook `porteriaya-ingesta` |
| `06-bot-telegram.json` | Preguntar al asistente desde Telegram | mensaje de Telegram |
| `07-resumen-semanal.json` | Cifras del conjunto por correo a administración | cron, lunes 7:00 |

**Por qué hace falta el enrutador.** La aplicación publica *todos* los eventos a una sola URL
(`porteria.n8n.webhook-url`), así que algo tiene que repartirlos. El enrutador valida el secreto una
vez y reenvía el evento intacto por HTTP al webhook del flujo correspondiente. Reparte por nombre de
webhook y no con nodos *Execute Workflow* a propósito: esos referencian el workflow por ID, y los ID
se asignan al importar, así que habría que cablearlos a mano después de cada importación.

### Para importarlos

1. Crea una credencial SMTP llamada **Mailpit**: host `mailpit`, puerto `1025`, sin autenticación,
   sin TLS. Los flujos la referencian por nombre; el `id` va como `REEMPLAZAR` y n8n lo resuelve al
   seleccionarla en el nodo.
2. Importa los ocho JSON y **actívalos**. Un flujo con webhook inactivo no recibe nada.
3. Las variables de entorno las pasa ya `docker-compose.yml` desde tu `.env`: `PORTERIA_SECRET`,
   `PORTERIA_BASE_URL`, `PORTERIA_URL_PUBLICA`, `CORREO_ADMINISTRACION` y `ADMIN_PIN`.

Los flujos 06 y 07 arrancan solos (uno por mensaje de Telegram, el otro por cron); el 06 necesita
además una credencial `telegramApi` con el token del bot.

### Tres decisiones dentro de los flujos

**El QR no va incrustado en el correo.** Generarlo en n8n obligaría a mandar el código de 6 dígitos
a un servicio externo de imágenes, y ese código abre la puerta del conjunto. El correo lleva el
código y un enlace a `residente.html`, donde el QR se genera en el navegador.

**Telegram llega desactivado.** §14 pide Telegram para las alertas de seguridad, y el nodo está
puesto en los flujos 02 y 04, pero `disabled: true`: el proyecto no trae token de bot, y con Mailpit
todo es demostrable sin cuentas externas. Activarlo es crear la credencial, poner `TELEGRAM_CHAT_ID`
y pulsar el interruptor del nodo.

**El resumen semanal no lleva un nodo de LLM**, aunque §14 lo titule «con IA». La lógica de IA vive
en Spring (§10), y no hay endpoint que resuma la operación: `/api/rag/preguntar` solo responde con
lo que está en los documentos del reglamento, que no son estas cifras. Escribir un prompt en n8n
sería exactamente lo que el contrato prohíbe. Para tenerlo redactado por un modelo hace falta un
endpoint nuevo en Spring que use el `LlmClient` que ya existe.

Dos detalles más, por si extrañan: el flujo 04 también transporta `DOCUMENTO_INDEXADO` y
`RAG_SIN_RESPUESTA`, que §10 emite pero §14 no asigna a ningún flujo — van ahí porque comparten
destinatario, marcados con `severidad: 'aviso'` para que no se confundan con una alerta. Y el bot de
Telegram consulta como **RESIDENTE**, el rol más restrictivo, para que un chat no sirva para sacar
los protocolos de vigilancia.

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
| RAG y las cuatro vistas | ⚠️ **sin probar contra el sistema real** |
| Los ocho flujos de n8n | ⚠️ **nunca importados en n8n**: el JSON es válido y el JS de los nodos Code compila, pero las versiones de nodo y las expresiones no se han comprobado dentro de n8n |
| Evaluación RAG de 20 preguntas (`CLAUDE.md` §11) | ⚠️ sin escribir; el umbral `0.55` está sin calibrar |
| `POST /api/demo/avanzar` | ⚠️ sin prueba automática |

**Lo primero al retomar:** levantar Docker y correr `./mvnw verify`, luego recorrer el guion de demo
de `CLAUDE.md` §19 en el navegador. Es razonable esperar ajustes en el RAG y en los flujos de n8n,
que nunca han corrido contra Qdrant, Ollama ni n8n de verdad.

Los flujos tienen su propio comprobador, que no necesita Docker:

```bash
node n8n/valida-flujos.js n8n/workflows
```

Comprueba que cada JSON parsea, que el JS de los nodos Code compila, que ninguna conexión apunta a
un nodo que no existe, que no hay nodos sueltos y que no se cuela ningún `id` de credencial real.
Lo que **no** puede comprobar es si los `typeVersion` de los nodos y las expresiones `{{ }}` son los
que espera n8n 2.47: eso solo se ve importando.

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
n8n/
  workflows/     enrutador + los siete flujos de §14, importables
  valida-flujos.js   comprobador de los flujos, sin Docker
```

## Requisitos

JDK 21, Docker, y nada más: el proyecto trae el wrapper de Maven (`./mvnw`).
