package co.porteriaya.evento;

/**
 * Eventos que la portería emite hacia n8n (§10). La lista es contrato: n8n
 * enruta por este nombre, asi que no se renombran ni se quitan sin pedirlo.
 */
public enum TipoEvento {

    AUTORIZACION_CREADA,
    INGRESO_REGISTRADO,
    VISITA_NO_ANUNCIADA,
    PAQUETE_RECIBIDO,
    PAQUETE_RECORDATORIO,
    PAQUETE_ESCALADO,
    PERSONA_NO_PERMITIDA,
    PERMANENCIA_EXCEDIDA,
    SALIDA_NO_REGISTRADA,
    DOCUMENTO_INDEXADO,
    RAG_SIN_RESPUESTA
}
