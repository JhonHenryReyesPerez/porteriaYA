# CLAUDE.md — PorteriaYa v2

Guía para desarrollar PorteriaYa con Claude Code. Lee este archivo completo antes de escribir código. Si algo aquí contradice una petición, pregunta antes de actuar.

## 1. Qué es el proyecto

Portería digital para conjuntos residenciales: visitas preautorizadas con código de 6 dígitos o QR, aprobación en vivo de visitas no anunciadas, control de permanencia y salida, paquetes con escalamiento, lista de personas no permitidas y bitácora de auditoría. La v2 añade un asistente **RAG** que responde sobre reglamento y protocolos del conjunto con citas.

Principio rector: **Spring Boot es el único dueño de reglas, tiempos y consultas a la IA.** n8n solo orquesta mensajes y alertas. La IA solo informa; nunca decide un ingreso.

## 2. Stack

| Capa | Tecnología |
| --- | --- |
| Frontend | HTML estático servido por Spring, Tailwind (CDN), Alpine.js o JS simple, Chart.js (CDN), QR generado en el navegador |
| Backend | Java 21, Spring Boot 3.x, Spring Web, Spring Data JPA, Validation, Springdoc OpenAPI, Spring Async/Scheduling, Flyway |
| BD relacional | PostgreSQL |
| BD vectorial | Qdrant (plan B: pgvector en el mismo PostgreSQL) |
| Embeddings | Ollama con `nomic-embed-text` (768 dimensiones, distancia coseno) |
| LLM del RAG | Configurable por propiedades (Claude por API u Ollama) detrás de una interfaz |
| Extracción de PDF | Apache PDFBox — única dependencia nueva aprobada para la ingesta; Markdown se lee directo |
| Mapeo DTO | A mano o MapStruct; si se usa MapStruct, se declara en el `pom.xml` desde el primer corte |
| Automatización | n8n, Mailpit (SMTP de pruebas), Telegram |
| Infra | Docker Compose: `postgres`, `qdrant`, `n8n`, `mailpit` por defecto; `ollama` (perfil `rag`) y `app` (perfil `app`) solo cuando se piden |
| Pruebas | JUnit 5, Mockito, Testcontainers (PostgreSQL y Qdrant), AssertJ |

Zona horaria de todo el sistema: `America/Bogota`.

## 3. Comandos

```bash
docker compose up -d                 # levanta postgres, qdrant, n8n, mailpit
docker compose up -d ollama          # solo al usar el RAG (perfil rag): carga modelos en RAM
docker compose exec ollama ollama pull nomic-embed-text   # una sola vez
docker compose stop ollama           # al terminar con el RAG, para liberar RAM
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo    # reloj simulado + datos de demo
./mvnw test                          # unitarias
./mvnw verify                        # unitarias + integración (Testcontainers)
```

- Swagger: `http://localhost:8080/swagger-ui.html`
- Mailpit: `http://localhost:8025`
- n8n: `http://localhost:5678`
- Qdrant: `http://localhost:6333/dashboard`

En desarrollo la app corre **fuera** de Compose, así que n8n (que sí está dentro de Docker) la alcanza en `http://host.docker.internal:8080`, no en `localhost`. Esa URL base sale por propiedad (`PORTERIA_BASE_URL` en `.env`), nunca escrita a mano en los nodos de n8n.

Antes de dar una tarea por terminada: `./mvnw verify` en verde y probar el flujo en Swagger con Compose arriba.

## 4. Estructura del repositorio

```
/src/main/java/co/porteriaya
  /config          # Clock, Async, Scheduling, CORS, propiedades tipadas, seguridad por secreto
  /autorizacion    # entity, repository, service, controller, dto, mapper
  /porteria        # validación de códigos, ingresos, salidas, solicitudes no anunciadas
  /paquete
  /residente
  /nopermitido
  /bitacora
  /dashboard
  /evento          # EventoPublisher, tipos de evento, cliente webhook n8n
  /rag
    /documento     # entidad Documento, ingesta, chunking
    /embedding     # EmbeddingClient (interfaz) + OllamaEmbeddingClient
    /vector        # VectorStore (interfaz) + QdrantVectorStore
    /llm           # LlmClient (interfaz) + implementaciones
    /consulta      # RagService, ConsultaRag, feedback
  /demo            # solo perfil demo
  /common          # excepciones, manejo global de errores, utilidades
/src/main/resources
  /static          # residente.html, porteria.html, admin.html, js/, css/
  application.yml, application-dev.yml, application-demo.yml
  /db/migration    # migraciones versionadas (Flyway)
  /rag/seed        # documentos de ejemplo (reglamento corto, protocolo)
/n8n/workflows     # JSON exportados de los flujos
/docs              # README, capturas, guion de demo
docker-compose.yml, .env.example
```

Organiza por **dominio** (paquete por feature), no por capa técnica. Dentro de cada dominio: `entity`, `repository`, `service`, `controller`, `dto`.

## 5. Convenciones de código

- Controllers delgados: validan entrada y delegan en el service. Sin lógica de negocio en controllers ni en entidades JPA.
- **DTO obligatorios** en la API: nunca exponer entidades JPA. Usa `record` para requests y responses; mapea con MapStruct o a mano.
- Validación con Bean Validation (`@Valid`, `@NotBlank`, `@Size`…). Mensajes en español.
- Inyección por constructor (Lombok `@RequiredArgsConstructor` permitido); nada de `@Autowired` en campos.
- Transacciones en la capa service (`@Transactional`); lecturas con `readOnly = true`.
- Errores con un `@RestControllerAdvice` que devuelve un cuerpo uniforme: `status`, `codigo`, `mensaje`, `timestamp`. Códigos: 400 validación, 404 no existe, **409 transición de estado inválida o uso agotado**, 403 sin permiso por rol o PIN inválido, 413 subida demasiado grande, 429 límite de frecuencia superado.
- Enums con estados llevan su **mapa de transiciones permitidas**; todo cambio de estado pasa por un método que lo valida.
- Sin números ni textos mágicos: tiempos, umbrales y límites en `@ConfigurationProperties` (`porteria.*`, `rag.*`).
- Nunca usar `LocalDateTime.now()` ni `Instant.now()` directo: inyectar `Clock`.
- Logs con SLF4J; nunca registrar documentos, códigos completos, tokens ni secretos.
- Migraciones con Flyway; `spring.jpa.hibernate.ddl-auto=validate` fuera de pruebas.
- Código y nombres de dominio en español (Autorizacion, Ingreso, Paquete); nombres técnicos y de infraestructura en inglés.
- Commits pequeños, un corte vertical por commit, mensajes en imperativo (`feat:`, `fix:`, `test:`, `docs:`).

## 6. Reglas de negocio (contrato — no cambiar sin pedirlo)

### Tipos de autorización
Visita (1 o varios usos), Domiciliario o proveedor (pocas horas, 1 uso), Servicio recurrente (meses, días y horas fijos, usos ilimitados dentro de la franja), Mudanza o trabajo (varios días, ilimitados dentro de la franja).

### Validación de un código en portería — en este orden exacto
1. El código existe y la autorización no está cancelada.
2. Fecha y hora actuales dentro de la ventana; si es recurrente, coinciden día de la semana y franja horaria.
3. Quedan usos (ver «Consumo del uso» más abajo).
4. La persona no está en la lista de no permitidas. El criterio tiene **dos niveles**: coincidencia exacta niega; coincidencia solo por nombre avisa (ver «Coincidencia con la lista de no permitidos»).
5. La persona no figura ya como Dentro con la misma autorización; se avisa al vigilante antes de duplicar.

La respuesta de `/api/porteria/validar` es `permitido` o el **motivo del rechazo** (enum), más las **advertencias** que no impiden el ingreso pero el vigilante debe ver (§«Coincidencia con la lista de no permitidos»). No cambiar el orden: el motivo devuelto depende de él.

### Consumo del uso (decidido 2026-11-08)
La cadena de 5 pasos se evalúa **dos veces** y solo la segunda escribe:

- `POST /api/porteria/validar` **no escribe nada**: recorre los 5 pasos leyendo y devuelve `permitido` o el motivo. Es idempotente; validar nunca quema un uso, así que un vigilante puede consultar un código sin consecuencias.
- `POST /api/porteria/ingresos` **repite los 5 pasos** y solo ahí, en el paso 3, descuenta con **actualización atómica en BD**:
  ```sql
  UPDATE autorizacion SET usos_actuales = usos_actuales + 1
   WHERE id = ? AND (usos_max IS NULL OR usos_actuales < usos_max)
  ```
  Filas afectadas = 0 → **409 `USOS_AGOTADOS`**. Nada de leer-modificar-guardar.
- Los pasos 4 y 5 se comprueban **antes** del UPDATE, dentro de la misma transacción. Así no hay que revertir un uso ya descontado, y el orden de los motivos de rechazo se conserva igual.
- `usos_max IS NULL` significa **usos ilimitados** (servicio recurrente, mudanza o trabajo). El `IS NULL` del WHERE es obligatorio: sin él, la comparación con NULL nunca es verdadera y todo ingreso recurrente daría 409.
- El test de concurrencia de §15 apunta a `POST /api/porteria/ingresos`, no a `validar`.

#### Coincidencia con la lista de no permitidos (decidido 2026-11-08)
Como del visitante solo se guardan nombre y los últimos 4 dígitos, el criterio tiene dos niveles:

- **Nombre normalizado y `doc4` iguales** → se niega el ingreso, se registra en `Bitacora` y se emite `PERSONA_NO_PERMITIDA`. Motivo de rechazo: `PERSONA_NO_PERMITIDA`.
- **Solo el nombre normalizado igual** (incluido el caso en que el registro de la lista no tiene `doc4`) → **no se niega automáticamente**. Se devuelve la advertencia `POSIBLE_NO_PERMITIDO` con el motivo del registro para que el vigilante confirme o descarte. Si confirma, el ingreso se deniega y se emite `PERSONA_NO_PERMITIDA`.
- Normalizar es: minúsculas, sin acentos (descomponer a NFD y borrar diacríticos) y espacios colapsados. «José  Pérez» y «jose perez» coinciden.
- Solo cuentan los registros con `activo = true`.

Esto introduce **advertencias** en la respuesta de `validar`, distintas de un rechazo:

- `POST /api/porteria/validar` responde `permitido` (booleano), `motivo` (enum del rechazo, nulo si está permitido) y `advertencias` (lista de enums). Una coincidencia parcial es `permitido: true` con `advertencias: [POSIBLE_NO_PERMITIDO]`.
- El paso 5 usa el mismo mecanismo: ya figurar como Dentro con la misma autorización es la advertencia `YA_DENTRO`, no un rechazo.
- `POST /api/porteria/ingresos` **exige confirmación explícita** cuando hay advertencias (`confirmaAdvertencias: true` en el cuerpo). Sin ella responde **409** con las advertencias, para que nadie se salte el aviso llamando directo al endpoint de ingreso.

### Otras reglas
- Visita no anunciada: el vigilante la registra como **`SolicitudNoAnunciada`** (entidad propia, no un Ingreso) y se pide aprobación al residente. Tiempo límite de respuesta: 3 minutos (configurable). El enlace de aprobación usa **token aleatorio de un solo uso con vencimiento**.
- El `Ingreso` de una visita no anunciada **solo se crea si la solicitud queda Aprobada** (o Autorizada por vigilante tras Sin respuesta). Así la regla «si el residente rechaza, el vigilante no puede registrar el ingreso» se cumple por construcción. Solo la administración puede anular una decisión.
- Token de aprobación: se guarda **hasheado** (nunca en claro), con `vence_en` y `usado_en`. `GET /api/aprobacion/{token}` **no muta**: devuelve una página de confirmación. La decisión se aplica en `POST /api/aprobacion/{token}`, porque los clientes de correo pre-cargan enlaces y un prefetch quemaría el token.
- Permanencia: cada autorización define una duración esperada (`duracion_esperada_min`). El valor **por defecto depende del tipo** y vive en `porteria.autorizaciones.duracion-esperada-min-por-tipo` (visita 180, domiciliario 30, servicio recurrente 300, mudanza 480); el residente puede sobrescribirlo en el formulario. Superarla en más de 30 minutos emite `PERMANENCIA_EXCEDIDA` una sola vez.
- Paquetes: recordatorios a las 24 h y 72 h; escalamiento a administración a los 7 días (todo configurable); cada aviso con su bandera para no repetirse.
- Retiro de paquetes: con el código del correo o por una persona registrada como autorizada a retirar.
- Paquete para apartamento inexistente o inactivo: no se registra.

### Estados y transiciones
| Entidad | Estados |
| --- | --- |
| Autorizacion | Activa → Agotada \| Vencida \| Cancelada |
| Ingreso | Dentro → Salió \| Salida no registrada |
| SolicitudNoAnunciada | Pendiente → Aprobada \| Rechazada \| Sin respuesta; desde Sin respuesta el vigilante decide → Autorizada por vigilante \| Denegada |
| Paquete | Recibido → Entregado \| Devuelto (banderas: recordatorio 24 h, 72 h, escalado) |
| Documento | Subido → Indexando → Indexado \| Error; además **Error → Indexando** (reintento) e **Indexado → Indexando** (`POST /api/rag/reindexar` o versión nueva) |

Salto inválido → **409**, y eso incluye **repetir el estado actual**: cancelar dos veces la misma autorización es un error del cliente y se ve como 409, no como un 200 silencioso. Quien decide si toca cambiar de estado es el service; la entidad solo comprueba que el salto exista en el mapa.

Todo cambio relevante se escribe en `Bitacora`, que **no se puede editar ni borrar desde la API** (sin endpoints PUT/PATCH/DELETE y sin `@Setter` público en la entidad).

### Privacidad (Ley 1581 de 2012)
- Se guarda solo el **nombre y los últimos 4 dígitos** del documento. Nunca el documento completo.
- Nada de datos personales de visitantes en logs, vectores, prompts ni historial del chat.

## 7. Roles (sin login)

Residente (selector de torre y apartamento), Vigilante (selector de vigilante y turno), Administrador (selector de administrador). El rol viaja en un header (`X-Rol`, `X-Actor`) y se valida en servidor por endpoint; la UI no es la barrera. Todo endpoint de administrador rechaza otros roles con 403.

**PIN de administrador (decidido 2026-11-08).** Como `X-Rol` es autodeclarado, los endpoints de administrador exigen además `X-Admin-Pin`, que se compara contra `porteria.admin.pin` (valor en `.env`, nunca en el repositorio). PIN ausente o incorrecto → **403**. El administrador lo escribe una vez en el selector de la UI. Aplica a: `POST`/`DELETE /api/rag/documentos`, `POST /api/rag/reindexar`, `GET /api/bitacora`, `POST`/`DELETE /api/no-permitidos` y cualquier anulación. Ver la limitación conocida en §16.

## 8. API

Rutas del contrato:

- `POST/GET /api/autorizaciones`, `DELETE /api/autorizaciones/{id}` — el DELETE es **cancelación lógica**: pasa la autorización a `Cancelada`, nunca borra la fila (§12).
- `POST /api/porteria/validar`, `POST /api/porteria/ingresos`, `PATCH /api/porteria/ingresos/{id}/salida`, `GET /api/porteria/dentro`
- Visita no anunciada: `POST /api/porteria/solicitudes` (el vigilante la registra), `GET /api/porteria/solicitudes?estado=`, `PATCH /api/porteria/solicitudes/{id}/decision` (decisión del vigilante cuando quedó Sin respuesta). Esta última **reemplaza** a `PATCH /api/porteria/ingresos/{id}/decision` del contrato original, porque la solicitud es entidad propia y el Ingreso no existe todavía cuando se decide.
- `GET /api/aprobacion/{token}?accion=` → página de confirmación, **sin efecto**; `POST /api/aprobacion/{token}` → consume el token y aplica la decisión.
- `POST /api/paquetes`, `POST /api/paquetes/{id}/entregar`, `GET /api/paquetes?estado=&apto=`
- `GET /api/residentes` (alimenta el selector de torre y apartamento), `GET/POST/DELETE /api/residentes/{id}/autorizados-retiro`
- `GET/POST /api/no-permitidos`, `DELETE /api/no-permitidos/{id}` (desactiva, no borra)
- `GET /api/bitacora` (filtros fecha, actor, entidad)
- `GET /api/dashboard/stats`
- RAG: `POST/GET/DELETE /api/rag/documentos`, `POST /api/rag/preguntar`, `POST /api/rag/feedback`, `GET /api/rag/consultas?sinRespuesta=true` (panel de administración), `GET /api/rag/incidentes-similares?texto=`, `POST /api/rag/reindexar`
- Demo (solo perfil `demo`): `POST /api/demo/avanzar?minutos=`

Todo endpoint documentado en OpenAPI con ejemplos de request y response.

## 9. Tiempo (único dueño: Spring)

- Un `Clock` inyectable en todo el sistema; en perfil `demo` es un reloj con desfase configurable que `POST /api/demo/avanzar` adelanta.
- `@Scheduled` cada minuto ejecuta, de forma **idempotente**: solicitudes Pendientes vencidas → Sin respuesta y aviso al vigilante; ingresos Dentro que exceden duración + 30 min → `PERMANENCIA_EXCEDIDA` (una vez); ingresos Dentro al terminar la ventana o a medianoche, **lo que ocurra primero** → Salida no registrada + `SALIDA_NO_REGISTRADA`; paquetes → recordatorios 24 h y 72 h y `PAQUETE_ESCALADO` a los 7 días, cada uno con su bandera; autorizaciones con ventana terminada → Vencida.
- `POST /api/demo/avanzar` **ejecuta la pasada del scheduler al terminar de mover el reloj**. El disparo de `@Scheduled` sigue el reloj real, así que sin esto la demo esperaría hasta un minuto para ver los recordatorios. Como las tareas son idempotentes y se basan en estado, ejecutarlas de más es inofensivo.
- Los tests del scheduler usan un `Clock` fijo; **nunca `Thread.sleep`**.

## 10. Eventos hacia n8n

`AUTORIZACION_CREADA`, `INGRESO_REGISTRADO`, `VISITA_NO_ANUNCIADA`, `PAQUETE_RECIBIDO`, `PAQUETE_RECORDATORIO`, `PAQUETE_ESCALADO`, `PERSONA_NO_PERMITIDA`, `PERMANENCIA_EXCEDIDA`, `SALIDA_NO_REGISTRADA`, `DOCUMENTO_INDEXADO`, `RAG_SIN_RESPUESTA`.

- Envío con `@Async` y `try/catch`: **si n8n está caído, el registro de portería se guarda igual** y el fallo queda en logs.
- Header `X-Porteria-Secret` compartido; el secreto vive en `.env` y se lee por propiedad. Nunca en el repositorio. Es un secreto **distinto** del PIN de administrador de §7: el de n8n nunca llega al navegador.
- El payload lleva solo lo necesario para el mensaje; sin documentos completos.
- n8n no calcula reglas ni tiempos. Ante la duda sobre dónde va una lógica: en Spring.

## 11. RAG, embeddings y BD vectorial

### Reglas de arquitectura
- Interfaces `EmbeddingClient`, `VectorStore` y `LlmClient`; el resto del código no conoce Ollama, Qdrant ni el proveedor del LLM. Cambiar de proveedor = cambiar una implementación y una propiedad.
- PostgreSQL es la fuente de verdad (`Documento`, `ConsultaRag`); Qdrant es una copia derivada que `reindexar` puede reconstruir.
- Mismo modelo y misma dimensión (768) para indexar y consultar. Cambiar de modelo obliga a reindexar. Verificar la dimensión al arrancar.

### Ingesta
1. Subida de PDF o Markdown con tipo (reglamento, protocolo, circular, FAQ) y audiencia (residente, vigilante, administrador).
2. Extraer texto, calcular `hashContenido`; si ya existe con el mismo hash, no reindexar.
3. Chunking de 500 a 800 tokens con solape de 100, cortando por títulos y párrafos. **No se añade un tokenizador**: se aproxima a 4 caracteres por token (2000–3200 caracteres por chunk, 400 de solape), que es suficiente porque el límite real lo pone el contexto del modelo de embeddings.
4. Embedding por chunk (en lotes) y upsert en la colección `reglamento` con payload `documentoId`, `titulo`, `pagina`, `chunkIdx`, `audiencia`, `version`, `fecha`, `texto`.
5. Al subir una versión nueva: borrar primero los chunks anteriores por `documentoId`. Marcar el `Documento` Indexado y emitir `DOCUMENTO_INDEXADO`.
6. La indexación corre en segundo plano; si falla, el estado queda en Error con el motivo.

`nomic-embed-text` exige **prefijos de tarea**: `search_document: ` al indexar cada chunk y `search_query: ` al embeber la pregunta. Omitirlos degrada el recall y arruina la métrica de evaluación de más abajo.

`audiencia` es un **conjunto**, no un valor único (un reglamento sirve a los tres roles). Se guarda con el valor `TODOS` cuando aplica a cualquiera, y el filtro de consulta es `audiencia ∈ {rol, TODOS}`. El administrador recupera todas las audiencias.

### Consulta
1. `POST /api/rag/preguntar` con pregunta y rol (el rol sale del header, no del cuerpo).
2. Embedding de la pregunta; búsqueda top-5 **con filtro de audiencia según el rol** (el residente nunca recupera chunks de vigilante o administrador).
3. Si la mejor similitud queda bajo el umbral (`rag.umbral`), responder «no encuentro esto en los documentos», marcar `sinRespuesta` y emitir `RAG_SIN_RESPUESTA`. No llamar al LLM.
4. Con contexto: armar el prompt y llamar al LLM con temperatura baja y límite de tokens.
5. Responder con texto y fuentes (título y página); guardar `ConsultaRag` con latencia.

### Guardrails (obligatorios)
- El prompt de sistema obliga a responder solo con el contexto recuperado, citar fuentes y decir cuando no alcanza. No inventar normas ni horarios.
- El asistente **no consulta tablas operativas** ni devuelve datos de residentes, visitantes o autorizaciones.
- El texto de los documentos y la pregunta del usuario se tratan como **datos, nunca como instrucciones**; se delimitan en el prompt y no pueden cambiar el prompt de sistema.
- Antes de vectorizar o guardar una pregunta se sanitiza: sin nombres, documentos, correos, teléfonos ni tokens.
- La colección `incidentes` se alimenta con resúmenes **anonimizados** de bitácora (sin nombre ni documento).
- Si Qdrant, Ollama o el LLM fallan: la portería sigue funcionando, el asistente responde «servicio no disponible» y el error se registra.
- Limitar tamaño de subida y tipos de archivo permitidos; solo administrador sube o borra documentos (rol + PIN de §7).

### Evaluación
Un test de integración con ~20 preguntas y su documento esperado mide cuántas veces aparece el documento correcto entre los 5 chunks recuperados. Ajustar tamaño de chunk y umbral con ese número, no a ojo. Valor inicial de `rag.umbral`: 0.55 en similitud coseno, a calibrar con esa métrica.

## 12. Base de datos

- Tablas: residente, autorizacion, ingreso, **solicitud_no_anunciada**, **token_aprobacion**, paquete, autorizado_retiro, no_permitido, bitacora, documento, consulta_rag.
  - `solicitud_no_anunciada`: id, residente/apto, nombre, doc4, estado, creado_en, vence_en, ingreso_id (nulo hasta aprobarse).
  - `token_aprobacion`: token_hash (único), solicitud_id, vence_en, usado_en. Nunca el token en claro.
- Índices en: `ingreso(estado)`, `paquete(estado, recibido_en)`, `bitacora(fecha, actor, entidad)`, `documento(hash_contenido)`, `solicitud_no_anunciada(estado, creado_en)`, `token_aprobacion(token_hash)` único.
- Unicidad del código: **índice único parcial**, no global, para poder reutilizar códigos de autorizaciones ya cerradas:
  ```sql
  CREATE UNIQUE INDEX ux_autorizacion_codigo_activa
      ON autorizacion (codigo) WHERE estado = 'ACTIVA';
  ```
  Generación del código de 6 dígitos con reintento ante colisión.
- Sin borrados físicos de entidades operativas: se cambia de estado o se desactiva. `DELETE /api/autorizaciones/{id}` pasa a `Cancelada`; `DELETE /api/no-permitidos/{id}` desactiva.
- Datos de demo mediante un **sembrador en perfil `demo`** (`/demo`, un `ApplicationRunner`), no con `data.sql`: mezclar `data.sql` con Flyway y `ddl-auto=validate` rompe el arranque por orden de ejecución. Siembra ~30 apartamentos, autorizaciones de distintos tipos, paquetes de distinta antigüedad y una persona no permitida; más los documentos de ejemplo precargados e indexados.

## 13. Frontend

- Tres páginas: `residente.html`, `porteria.html` (pensada para tablet, botones grandes) y `admin.html`.
- Residente: formulario de autorización (tipo, ventana, usos, recurrencia y **duración esperada de permanencia**) que muestra código y QR; mis autorizaciones, paquetes, autorizados a retirar; chat «Pregunta al conjunto».
- Portería: Validar código, Visita no anunciada, Recibir paquete, Entregar paquete; quién está dentro y paquetes pendientes; chat de protocolos; incidentes similares. Validar y registrar el ingreso son dos pasos distintos: validar no consume el uso (§6).
- Administrador: selector con **PIN** (§7); tarjetas de métricas, gráfico de visitas por hora (Chart.js), paquetes por antigüedad, no permitidas, bitácora con filtros, gestión de documentos (estado de indexación) y preguntas sin respuesta.
- Chat: muestra fuentes como chips, indica claramente «no encuentro esto» y ofrece 👍/👎 (`/api/rag/feedback`).
- Escapar siempre el texto que viene de la API o del LLM (sin `innerHTML` con datos de usuario).
- Accesibilidad básica: contraste, tamaños táctiles grandes en portería, etiquetas en formularios. Manejo visible de errores 409 con el motivo.

## 14. Automatizaciones n8n

Flujos (JSON exportados en `/n8n/workflows`, sin credenciales embebidas):
1. Visitas — `AUTORIZACION_CREADA`, `INGRESO_REGISTRADO`: correo con código y QR; aviso de ingreso.
2. Aprobación en vivo — `VISITA_NO_ANUNCIADA`: correo o Telegram con botones Aprobar y Rechazar que llevan a `GET /api/aprobacion/{token}?accion=`, la página de confirmación; el botón de esa página hace el `POST` que decide.
3. Paquetes — `PAQUETE_RECIBIDO`, `PAQUETE_RECORDATORIO`, `PAQUETE_ESCALADO`.
4. Alertas de seguridad — `PERSONA_NO_PERMITIDA`, `PERMANENCIA_EXCEDIDA`, `SALIDA_NO_REGISTRADA`: Telegram inmediato a administración.
5. Ingesta de documentos — llama a `POST /api/rag/documentos`.
6. Bot de Telegram — llama a `POST /api/rag/preguntar`.
7. Resumen semanal con IA — cron semanal por correo (opcional).

Los flujos 5 a 7 solo transportan datos; la lógica de IA vive en Spring. Revisar a mano los nodos de credenciales antes de exportar.

## 15. Pruebas (no negociables)

Escribir primero o junto con el código:
- Cada paso de la validación del código, en su orden, en `validar` (sin efectos) y en `ingresos` (con consumo).
- **Uso simultáneo del mismo código de un solo uso** (varios hilos) contra `POST /api/porteria/ingresos`: solo uno pasa, el resto recibe 409. Este test **no puede ser `@Transactional`**: con la transacción del test envolviendo a los hilos, el UPDATE atómico no se ve y el test pasa en falso.
- Usos ilimitados (`usos_max IS NULL`): varios ingresos seguidos dentro de la franja, todos permitidos.
- Recurrencia por día de la semana y franja horaria (bordes: justo en `horaDesde`, justo en `horaHasta`, cambio de día).
- Todas las transiciones de estado válidas e inválidas (409), incluidas `Error → Indexando` e `Indexado → Indexando` de `Documento`.
- Lista de no permitidos: coincidencia exacta niega; coincidencia solo por nombre devuelve `POSIBLE_NO_PERMITIDO` sin negar; normalización de acentos, mayúsculas y espacios; registros inactivos no cuentan.
- `POST /api/porteria/ingresos` con advertencias y sin `confirmaAdvertencias` → 409.
- Escalamiento de paquetes (24 h, 72 h, 7 días) con `Clock` fijo y sin repetir avisos.
- Permanencia excedida una sola vez; cierre automático a medianoche.
- Token de aprobación: un solo uso, vencimiento, y que `GET /api/aprobacion/{token}` **no** lo consuma.
- Solicitud no anunciada: rechazada o denegada no crea `Ingreso`; aprobada sí.
- RAG: chunking, filtro por audiencia, umbral sin contexto, reemplazo de versión de documento, y evaluación de 20 preguntas.
- Integración con Testcontainers para PostgreSQL y Qdrant; los clientes de Ollama y del LLM se simulan en pruebas unitarias.

## 16. Seguridad

- Secretos solo en `.env` (con `.env.example` versionado, sin valores reales). `.env` en `.gitignore`.
- Validar `X-Porteria-Secret` en endpoints que n8n invoca; validar `X-Admin-Pin` en endpoints de administrador (§7).
- **Limitación conocida:** sin login, `X-Rol` y `X-Actor` son autodeclarados y cualquiera puede enviarlos. La validación por rol evita errores de uso, no es un control de autenticación; el PIN de administrador es lo único que protege de verdad las operaciones destructivas, y el límite de frecuencia por actor es evadible cambiando `X-Actor`. Está asumido por el alcance del proyecto y debe decirse así en el README.
- Sin SQL concatenado; usar parámetros.
- No exponer stack traces ni mensajes internos en respuestas.
- CORS restringido; límite de tamaño de peticiones y de subida de documentos (→ 413).
- Limitar la frecuencia de `/api/rag/preguntar` por actor para controlar costo y abuso (→ 429).

## 17. Forma de trabajar

1. Genera por **cortes verticales** (entidad → servicio → controller → test), no capa por capa.
2. Después de cada corte: levantar con Compose, probar en Swagger y hacer commit si funciona.
3. Antes de añadir una dependencia nueva, justifícala; prefiere lo que ya está en el stack.
4. No cambies contratos (rutas, nombres de eventos, orden de validación, estados) sin que se pida expresamente.
5. Ante ambigüedad en una regla de negocio, pregunta; no asumas. Lo que quede sin decidir va a §20, no a una suposición enterrada en el código.
6. Mantén README, `.env.example` y los flujos de n8n actualizados con cada cambio que los afecte.
7. Pide los flujos de n8n como JSON importable.

### Orden de construcción
1. Compose (postgres, qdrant, ollama, n8n, mailpit), esqueleto Spring, Flyway, datos semilla.
2. Entidades, autorizaciones con recurrencia, validación con uso atómico, bitácora y tests.
3. Ingresos y salidas, solicitud no anunciada con token, paquetes, `@Scheduled` con `Clock`, publicador de eventos.
4. No permitidas, `/dashboard/stats`, Swagger.
5. n8n: aprobación en vivo, paquetes, alertas de seguridad.
6. Interfaz de las tres vistas.
7. RAG: ingesta, embeddings, Qdrant, consulta con citas, evaluación.
8. Chat en la UI y n8n flujos 5 a 7.
9. README y capturas.

### Orden de recorte si falta tiempo
1. Resumen semanal con IA.
2. Colección `incidentes` e incidentes similares.
3. Bot de Telegram.
4. Autorizados a retirar paquetes.
5. Recurrencia por días y horas (dejar solo ventana de fechas).

**Nunca recortar:** validación del código, aprobación en vivo, escalamiento de paquetes y el ciclo mínimo del RAG (subir documento, indexar, preguntar con citas).

## 18. Definición de terminado

- [ ] Compila y `./mvnw verify` en verde.
- [ ] Probado en Swagger con Compose arriba.
- [ ] Sin entidades JPA expuestas; DTO validados.
- [ ] Cambios de estado validados y registrados en bitácora.
- [ ] Sin `Instant.now()`/`LocalDateTime.now()` directos; se usa `Clock`.
- [ ] Sin datos personales en logs, vectores ni prompts.
- [ ] Eventos con `@Async` y tolerancia a n8n caído.
- [ ] README, `.env.example` y OpenAPI actualizados.
- [ ] Commit pequeño con mensaje claro.

## 19. Guion de demo

Residente autoriza a un amigo y recibe el código → vigilante valida y el residente recibe el aviso de ingreso → llega alguien no anunciado y el residente aprueba desde el correo → llega un paquete → se adelanta el reloj y aparecen recordatorios y escalamiento → se intenta ingresar a una persona no permitida y suena la alerta → el residente pregunta por el horario de mudanzas y el asistente responde citando el reglamento; luego pregunta algo que no está en los documentos y recibe «no encuentro esto».

## 20. Decisiones pendientes

Pregunta antes de implementar cualquiera de estas; no las resuelvas por tu cuenta.

1. **Anulación por administración:** qué ruta la expone y qué efecto tiene sobre una solicitud ya Rechazada o Denegada (¿crea el ingreso, reabre la solicitud?).
2. **Una sola instancia de la app.** El `@Scheduled` no lleva bloqueo distribuido; con dos instancias las tareas se duplicarían. Asumido como una sola instancia; confirmar que basta para la entrega.
