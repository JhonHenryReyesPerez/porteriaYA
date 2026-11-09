package co.porteriaya.solicitud.repository;

import java.time.Instant;
import java.util.List;

import co.porteriaya.solicitud.entity.EstadoSolicitud;
import co.porteriaya.solicitud.entity.SolicitudNoAnunciada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SolicitudRepository extends JpaRepository<SolicitudNoAnunciada, Long> {

    @Query("""
            select s from SolicitudNoAnunciada s
             join fetch s.residente
             where s.estado = :estado
             order by s.creadoEn desc
            """)
    List<SolicitudNoAnunciada> buscarPorEstado(EstadoSolicitud estado);

    long countByEstado(EstadoSolicitud estado);

    /**
     * Pendientes cuyo plazo ya paso (§9). El filtro por estado es lo que hace
     * idempotente la pasada: una vez movida a SIN_RESPUESTA ya no vuelve a
     * salir, aunque el scheduler corra cada minuto.
     */
    @Query("""
            select s from SolicitudNoAnunciada s
             join fetch s.residente
             where s.estado = co.porteriaya.solicitud.entity.EstadoSolicitud.PENDIENTE
               and s.venceEn < :ahora
            """)
    List<SolicitudNoAnunciada> pendientesVencidas(Instant ahora);
}
