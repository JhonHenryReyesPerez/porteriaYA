package co.porteriaya.residente.controller;

import java.util.List;

import co.porteriaya.residente.dto.RespuestaResidente;
import co.porteriaya.residente.repository.ResidenteRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Alimenta los selectores de la interfaz (§7). */
@RestController
@RequestMapping("/api/residentes")
@RequiredArgsConstructor
@Tag(name = "Residentes", description = "Apartamentos activos para los selectores")
public class ResidenteController {

    private final ResidenteRepository repositorio;

    @GetMapping
    @Operation(summary = "Listar apartamentos activos",
            description = """
                    Sin comprobacion de rol: es la lista que necesita el selector de la
                    pantalla de inicio, antes de que haya rol alguno. Por eso no devuelve
                    correo ni telefono, que si son datos de contacto de los vecinos.
                    """)
    public List<RespuestaResidente> listar() {
        return repositorio.findByActivoTrueOrderByTorreAscApartamentoAsc().stream()
                .map(RespuestaResidente::de)
                .toList();
    }
}
