package co.porteriaya.rag.documento;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import co.porteriaya.common.SolicitudInvalidaException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Extrae el texto de un PDF o de un Markdown (§11, paso 2 de la ingesta).
 *
 * Devuelve bloques y no una cadena unica porque los PDF se extraen pagina a
 * pagina: asi cada chunk sabe de que pagina sale y la cita que ve el residente
 * puede decir «Reglamento, p. 7» en vez de solo «Reglamento».
 */
@Component
public class ExtractorTexto {

    /** Un trozo de documento con su pagina, nula si el formato no tiene paginas. */
    public record BloqueTexto(Integer pagina, String texto) {
    }

    public List<BloqueTexto> extraer(MultipartFile archivo) {
        String nombre = archivo.getOriginalFilename() == null
                ? "" : archivo.getOriginalFilename().toLowerCase();
        try {
            if (nombre.endsWith(".pdf")) {
                return extraerDePdf(archivo.getBytes());
            }
            if (nombre.endsWith(".md") || nombre.endsWith(".markdown") || nombre.endsWith(".txt")) {
                return List.of(new BloqueTexto(null,
                        new String(archivo.getBytes(), StandardCharsets.UTF_8)));
            }
            throw new SolicitudInvalidaException(
                    "Solo se aceptan PDF, Markdown o texto plano. Recibido: " + nombre);
        } catch (IOException e) {
            throw new SolicitudInvalidaException("No se pudo leer el archivo: " + e.getMessage());
        }
    }

    /** Markdown desde el classpath, para la semilla de demo. */
    public List<BloqueTexto> desdeTexto(String contenido) {
        return List.of(new BloqueTexto(null, contenido));
    }

    private List<BloqueTexto> extraerDePdf(byte[] bytes) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            List<BloqueTexto> bloques = new ArrayList<>();
            PDFTextStripper extractor = new PDFTextStripper();
            extractor.setSortByPosition(true);

            for (int pagina = 1; pagina <= pdf.getNumberOfPages(); pagina++) {
                extractor.setStartPage(pagina);
                extractor.setEndPage(pagina);
                String texto = extractor.getText(pdf);
                if (texto != null && !texto.isBlank()) {
                    bloques.add(new BloqueTexto(pagina, texto));
                }
            }
            return bloques;
        }
    }
}
