/*
 * Capa compartida de las tres vistas.
 *
 * Dos cosas viven aquí porque no deben repetirse en cada página:
 *
 * 1. `esc()`. Todo texto que venga de la API o del LLM pasa por aquí antes de
 *    tocar el DOM (§13). No se usa innerHTML con datos de usuario en ninguna
 *    página: el nombre de un visitante lo teclea un vigilante y la respuesta
 *    del asistente la redacta un modelo, así que ninguno de los dos es de fiar.
 *
 * 2. La sesión. El rol y el actor viajan en cabeceras, no en una cookie, y se
 *    guardan en localStorage para que recargar la página no obligue a volver a
 *    elegir. Recuerda que esto NO es autenticación: la UI no es la barrera, el
 *    servidor valida cada endpoint (§7, §16).
 */

const Sesion = {
  guardar(rol, actor, pin) {
    localStorage.setItem('porteriaya.rol', rol);
    localStorage.setItem('porteriaya.actor', actor);
    if (pin !== undefined && pin !== null) {
      localStorage.setItem('porteriaya.pin', pin);
    }
  },
  rol() {
    return localStorage.getItem('porteriaya.rol');
  },
  actor() {
    return localStorage.getItem('porteriaya.actor');
  },
  pin() {
    return localStorage.getItem('porteriaya.pin') || '';
  },
  limpiar() {
    localStorage.removeItem('porteriaya.rol');
    localStorage.removeItem('porteriaya.actor');
    localStorage.removeItem('porteriaya.pin');
  },
  /** Manda al selector si falta el rol esperado por la página. */
  exigir(rolEsperado) {
    if (this.rol() !== rolEsperado) {
      window.location.href = '/index.html';
      return false;
    }
    return true;
  },
};

/** Escapa texto para insertarlo en HTML. Nada va al DOM sin pasar por aquí. */
function esc(valor) {
  if (valor === null || valor === undefined) {
    return '';
  }
  return String(valor)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

/**
 * Error de la API con el cuerpo uniforme de §5 ya interpretado.
 * El `codigo` es lo que la UI usa para explicar un 409 con su motivo.
 */
class ErrorApi extends Error {
  constructor(status, codigo, mensaje) {
    super(mensaje || 'Error inesperado');
    this.status = status;
    this.codigo = codigo || 'ERROR';
  }
}

async function pedir(metodo, ruta, cuerpo, opciones = {}) {
  const cabeceras = { 'X-Rol': Sesion.rol() || '', 'X-Actor': Sesion.actor() || '' };
  if (Sesion.pin()) {
    cabeceras['X-Admin-Pin'] = Sesion.pin();
  }
  if (cuerpo !== undefined && !(cuerpo instanceof FormData)) {
    cabeceras['Content-Type'] = 'application/json';
  }

  const respuesta = await fetch(ruta, {
    method: metodo,
    headers: cabeceras,
    body: cuerpo instanceof FormData ? cuerpo : (cuerpo === undefined ? undefined : JSON.stringify(cuerpo)),
  });

  if (respuesta.status === 204) {
    return null;
  }

  const tipo = respuesta.headers.get('content-type') || '';
  const datos = tipo.includes('application/json') ? await respuesta.json() : await respuesta.text();

  if (!respuesta.ok) {
    if (datos && typeof datos === 'object') {
      throw new ErrorApi(respuesta.status, datos.codigo, datos.mensaje);
    }
    throw new ErrorApi(respuesta.status, 'ERROR', String(datos));
  }
  return datos;
}

const Api = {
  get: (ruta) => pedir('GET', ruta),
  post: (ruta, cuerpo) => pedir('POST', ruta, cuerpo),
  patch: (ruta, cuerpo) => pedir('PATCH', ruta, cuerpo),
  del: (ruta) => pedir('DELETE', ruta),
  subir: (ruta, formData) => pedir('POST', ruta, formData),
};

/** Avisos en pantalla. Los 409 se muestran con su motivo, que es lo que §13 pide. */
function avisar(contenedorId, texto, tipo = 'error') {
  const colores = {
    error: 'bg-red-50 text-red-800 border-red-200',
    ok: 'bg-green-50 text-green-800 border-green-200',
    aviso: 'bg-amber-50 text-amber-900 border-amber-200',
  };
  const caja = document.getElementById(contenedorId);
  if (!caja) return;
  caja.className = `border rounded-lg px-4 py-3 text-sm ${colores[tipo]}`;
  caja.textContent = texto;
  caja.classList.remove('hidden');
}

function limpiarAviso(contenedorId) {
  const caja = document.getElementById(contenedorId);
  if (caja) caja.classList.add('hidden');
}

/** Un error de la API como texto para la persona, con el código visible. */
function textoDeError(e) {
  if (e instanceof ErrorApi) {
    return `${e.message} (${e.codigo})`;
  }
  return 'No se pudo conectar con el servidor';
}

function fecha(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('es-CO', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'America/Bogota',
  });
}

function hora(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleTimeString('es-CO', {
    timeStyle: 'short',
    timeZone: 'America/Bogota',
  });
}

/**
 * Chat del asistente, compartido entre la vista de residente y la de portería.
 * Monta el hilo, pinta las fuentes como chips y los botones de voto (§13).
 */
function montarChat({ hiloId, formId, entradaId }) {
  const hilo = document.getElementById(hiloId);
  const form = document.getElementById(formId);
  const entrada = document.getElementById(entradaId);
  if (!hilo || !form) return;

  function burbuja(clases, contenido) {
    const div = document.createElement('div');
    div.className = clases;
    div.innerHTML = contenido;
    hilo.appendChild(div);
    hilo.scrollTop = hilo.scrollHeight;
    return div;
  }

  form.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const pregunta = entrada.value.trim();
    if (!pregunta) return;

    burbuja('ml-auto max-w-[85%] bg-slate-800 text-white rounded-2xl rounded-br-sm px-4 py-2 text-sm',
      esc(pregunta));
    entrada.value = '';
    const pensando = burbuja('max-w-[85%] bg-slate-100 rounded-2xl rounded-bl-sm px-4 py-2 text-sm text-slate-500',
      'Buscando en los documentos…');

    try {
      const r = await Api.post('/api/rag/preguntar', { pregunta });
      pensando.remove();

      // El aviso de "no encuentro esto" tiene que verse distinto de una
      // respuesta normal: §13 pide que quede claro cuándo el asistente no sabe.
      const estilo = r.sinRespuesta
        ? 'max-w-[85%] bg-amber-50 border border-amber-200 rounded-2xl rounded-bl-sm px-4 py-3 text-sm'
        : 'max-w-[85%] bg-slate-100 rounded-2xl rounded-bl-sm px-4 py-3 text-sm';

      let html = `<p class="whitespace-pre-line">${esc(r.texto)}</p>`;

      if (r.fuentes && r.fuentes.length) {
        html += '<div class="flex flex-wrap gap-1 mt-3">';
        for (const f of r.fuentes) {
          html += `<span class="text-xs bg-white border border-slate-300 rounded-full px-2 py-0.5">${esc(f)}</span>`;
        }
        html += '</div>';
      }

      if (r.consultaId) {
        html += `<div class="flex gap-2 mt-3 text-base" data-voto="${esc(r.consultaId)}">
            <button type="button" data-valor="1" class="hover:scale-110" aria-label="Respuesta útil">👍</button>
            <button type="button" data-valor="-1" class="hover:scale-110" aria-label="Respuesta no útil">👎</button>
          </div>`;
      }

      const caja = burbuja(estilo, html);
      caja.querySelectorAll('[data-voto] button').forEach((boton) => {
        boton.addEventListener('click', async () => {
          const grupo = boton.closest('[data-voto]');
          try {
            await Api.post('/api/rag/feedback', {
              consultaId: Number(grupo.dataset.voto),
              valor: Number(boton.dataset.valor),
            });
            grupo.textContent = '¡Gracias!';
            grupo.className = 'mt-3 text-xs text-slate-500';
          } catch (e) {
            grupo.textContent = textoDeError(e);
          }
        });
      });
    } catch (e) {
      pensando.remove();
      burbuja('max-w-[85%] bg-red-50 border border-red-200 rounded-2xl rounded-bl-sm px-4 py-3 text-sm',
        esc(textoDeError(e)));
    }
  });
}
