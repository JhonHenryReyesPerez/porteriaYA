package co.porteriaya.solicitud.controller;

import co.porteriaya.solicitud.dto.AccionAprobacion;
import co.porteriaya.solicitud.dto.RespuestaSolicitud;
import co.porteriaya.solicitud.service.SolicitudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * El enlace que el residente recibe por correo o Telegram (§8).
 *
 * El GET NO muta nada: devuelve una pagina de confirmacion y la decision se
 * aplica con el POST. Es la unica forma de que esto sea seguro: Gmail, Outlook
 * y Telegram pre-cargan los enlaces de un mensaje, y con un GET que decidiera,
 * ese prefetch quemaria el token de un solo uso y aprobaria o rechazaria la
 * visita sin que el residente toque nada. En mitad de una demostracion eso se
 * ve como un «sin respuesta» inexplicable.
 */
@RestController
@RequestMapping("/api/aprobacion")
@RequiredArgsConstructor
@Tag(name = "Aprobacion en vivo", description = "Enlace de aprobacion para el residente")
public class AprobacionController {

    private final SolicitudService servicio;

    @GetMapping(value = "/{token}", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Pagina de confirmacion (sin efecto)",
            description = """
                    Devuelve HTML con un boton que hace el POST. No consume el token ni
                    cambia el estado de la solicitud, asi que un prefetch del cliente de
                    correo es inofensivo.
                    """)
    public String paginaDeConfirmacion(@PathVariable String token,
                                       @RequestParam AccionAprobacion accion) {
        RespuestaSolicitud solicitud = servicio.consultarPorToken(token);
        return pagina(token, accion, solicitud);
    }

    @PostMapping(value = "/{token}", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Aplicar la decision",
            description = """
                    Consume el token y resuelve la solicitud. Un segundo intento con el
                    mismo enlace responde 409 TOKEN_YA_USADO, y uno vencido 409
                    TOKEN_VENCIDO. Si la solicitud ya paso a SIN_RESPUESTA, el mapa de
                    transiciones lo impide y tambien es 409.
                    """)
    public String aplicarDecision(@PathVariable String token,
                                  @RequestParam AccionAprobacion accion) {
        RespuestaSolicitud solicitud = servicio.decidirConToken(token, accion);
        return paginaDeResultado(solicitud, accion);
    }

    // ------------------------------------------------------------------ HTML
    // Todo lo que viene de datos va escapado (§13): el nombre del visitante lo
    // teclea el vigilante y no es de fiar.

    private String pagina(String token, AccionAprobacion accion, RespuestaSolicitud solicitud) {
        String verbo = accion == AccionAprobacion.APROBAR ? "aprobar" : "rechazar";
        String color = accion == AccionAprobacion.APROBAR ? "#15803d" : "#b91c1c";

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="robots" content="noindex, nofollow">
                  <title>Confirmar visita</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 0; padding: 24px;
                           background: #f8fafc; color: #0f172a; }
                    .tarjeta { max-width: 420px; margin: 32px auto; background: #fff;
                               border-radius: 12px; padding: 24px;
                               box-shadow: 0 1px 3px rgba(0,0,0,.12); }
                    h1 { font-size: 20px; margin: 0 0 16px; }
                    dl { margin: 0 0 24px; }
                    dt { font-size: 12px; text-transform: uppercase; color: #64748b;
                         letter-spacing: .04em; margin-top: 12px; }
                    dd { margin: 2px 0 0; font-size: 16px; }
                    button { width: 100%%; padding: 16px; font-size: 17px; font-weight: 600;
                             color: #fff; background: %s; border: 0; border-radius: 10px;
                             cursor: pointer; }
                    .nota { margin-top: 16px; font-size: 13px; color: #64748b; }
                  </style>
                </head>
                <body>
                  <div class="tarjeta">
                    <h1>&iquest;Confirmas %s esta visita?</h1>
                    <dl>
                      <dt>Visitante</dt><dd>%s</dd>
                      <dt>Apartamento</dt><dd>%s</dd>
                      <dt>Motivo</dt><dd>%s</dd>
                    </dl>
                    <form method="post" action="/api/aprobacion/%s?accion=%s">
                      <button type="submit">S&iacute;, %s</button>
                    </form>
                    <p class="nota">Este enlace sirve una sola vez.</p>
                  </div>
                </body>
                </html>
                """.formatted(
                color,
                escapar(verbo),
                escapar(solicitud.visitanteNombre()),
                escapar(solicitud.apartamento()),
                escapar(solicitud.motivo() == null ? "No indicado" : solicitud.motivo()),
                escapar(token),
                accion.name(),
                escapar(verbo));
    }

    private String paginaDeResultado(RespuestaSolicitud solicitud, AccionAprobacion accion) {
        String titulo = accion == AccionAprobacion.APROBAR
                ? "Visita aprobada"
                : "Visita rechazada";
        String mensaje = accion == AccionAprobacion.APROBAR
                ? "Ya puede subir. La porteria lo acaba de registrar."
                : "La porteria ya lo sabe y no se le permitira el ingreso.";

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="robots" content="noindex, nofollow">
                  <title>%s</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 0; padding: 24px;
                           background: #f8fafc; color: #0f172a; }
                    .tarjeta { max-width: 420px; margin: 32px auto; background: #fff;
                               border-radius: 12px; padding: 24px; text-align: center;
                               box-shadow: 0 1px 3px rgba(0,0,0,.12); }
                    h1 { font-size: 20px; margin: 0 0 8px; }
                    p { margin: 0; color: #475569; }
                  </style>
                </head>
                <body>
                  <div class="tarjeta">
                    <h1>%s</h1>
                    <p>%s</p>
                    <p style="margin-top:16px">%s &middot; %s</p>
                  </div>
                </body>
                </html>
                """.formatted(
                escapar(titulo),
                escapar(titulo),
                escapar(mensaje),
                escapar(solicitud.visitanteNombre()),
                escapar(solicitud.apartamento()));
    }

    private String escapar(String valor) {
        return HtmlUtils.htmlEscape(valor == null ? "" : valor);
    }
}
