package co.porteriaya.rag.documento;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import co.porteriaya.common.Rol;

/**
 * A quien va dirigido un documento (§11).
 *
 * Es un CONJUNTO, no un valor unico: un reglamento sirve a los tres roles. Se
 * guarda como lista separada por comas y en el payload de Qdrant como array,
 * para poder filtrar por «cualquiera de estas».
 */
public enum Audiencia {

    TODOS,
    RESIDENTE,
    VIGILANTE,
    ADMINISTRADOR;

    /** Las audiencias que un rol puede recuperar: la suya y la de todos. */
    public static Set<Audiencia> visiblesPara(Rol rol) {
        if (rol == Rol.ADMINISTRADOR) {
            // Administracion ve todo, incluidos los protocolos de vigilancia
            return EnumSet.allOf(Audiencia.class);
        }
        return EnumSet.of(TODOS, Audiencia.valueOf(rol.name()));
    }

    public static Set<Audiencia> parsear(String separadoPorComas) {
        if (separadoPorComas == null || separadoPorComas.isBlank()) {
            return EnumSet.of(TODOS);
        }
        return Arrays.stream(separadoPorComas.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Audiencia::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Audiencia.class)));
    }

    public static String serializar(Set<Audiencia> audiencias) {
        return audiencias.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    public static List<String> nombres(Set<Audiencia> audiencias) {
        return audiencias.stream().sorted().map(Enum::name).toList();
    }
}
