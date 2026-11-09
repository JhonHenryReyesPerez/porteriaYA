package co.porteriaya.bitacora.repository;

import co.porteriaya.bitacora.entity.Bitacora;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Append-only: solo alta y consulta. No se expone ningun metodo de borrado ni
 * actualizacion, y la entidad no tiene setters (§6).
 */
public interface BitacoraRepository extends JpaRepository<Bitacora, Long> {
}
