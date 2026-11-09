package co.porteriaya.solicitud.repository;

import java.util.Optional;

import co.porteriaya.solicitud.entity.TokenAprobacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TokenAprobacionRepository extends JpaRepository<TokenAprobacion, Long> {

    /** Se busca por hash: el token en claro nunca se guarda (§6). */
    @Query("""
            select t from TokenAprobacion t
             join fetch t.solicitud s
             join fetch s.residente
             where t.tokenHash = :tokenHash
            """)
    Optional<TokenAprobacion> buscarPorHash(String tokenHash);
}
