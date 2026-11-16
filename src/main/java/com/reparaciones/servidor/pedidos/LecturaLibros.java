package com.reparaciones.servidor.pedidos;

import com.reparaciones.servidor.nsys.ClienteDrive;
import com.reparaciones.servidor.nsys.ImportadorNsys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** El lector de los dos libros de ventas de Amazon (spec «asociación de IMEI y pedido en Amazon», «Lector de los libros»): en cada
 *  vuelta, por canal con libro configurado, pide a Drive la fecha de modificacion del fichero; si es la de la ultima lectura buena de ese
 *  canal no hace nada; si cambio, lo descarga a un temporal, lee en streaming la pestaña «Paso 1. Meter IMEI» ({@link LibroVentas}) y
 *  manda a {@link AsociacionAmazon} los pares de los ultimos 30 dias, guardando en la pasada la fecha del libro leido. Si algo falla (Drive,
 *  pestaña o cabecera que faltan, libro ilegible) no toca nada: apunta el error en la pasada del canal y se reintenta en la vuelta
 *  siguiente. Usa la cuenta de servicio de Drive del Items de NSYS ({@link ClienteDrive}). */
@Service
public class LecturaLibros {

    private static final Logger log = LoggerFactory.getLogger(LecturaLibros.class);
    static final int DIAS = 30;
    static final int MAX_ERROR = 500;

    private final ClienteDrive drive;
    private final AsociacionAmazon asociacion;
    private final Map<Canal, String> libros = new LinkedHashMap<>();
    private final Clock clock;

    @Autowired
    public LecturaLibros(ClienteDrive drive, AsociacionAmazon asociacion, @Value("${pedidos.libro.amazon:}") String libroAmazon,
                         @Value("${pedidos.libro.stockroom:}") String libroStockroom) {
        this(drive, asociacion, libroAmazon, libroStockroom, Clock.system(ImportadorNsys.MADRID));
    }

    LecturaLibros(ClienteDrive drive, AsociacionAmazon asociacion, String libroAmazon, String libroStockroom, Clock clock) {
        this.drive = drive;
        this.asociacion = asociacion;
        this.clock = clock;
        if (libroAmazon != null && !libroAmazon.isBlank()) libros.put(Canal.AMAZON, libroAmazon.strip());
        if (libroStockroom != null && !libroStockroom.isBlank()) libros.put(Canal.AMAZON_STOCKROOM, libroStockroom.strip());
    }

    /** Una vuelta: cada canal con libro, por separado (el error de uno no para al otro). */
    public void pasada() {
        for (Map.Entry<Canal, String> libro : libros.entrySet()) {
            if (Thread.currentThread().isInterrupted()) return;
            leer(libro.getKey(), libro.getValue());
        }
    }

    private void leer(Canal canal, String id) {
        try {
            LocalDateTime modificado = LocalDateTime.ofInstant(drive.metadatos(id).modificado(), ImportadorNsys.MADRID)
                    .truncatedTo(ChronoUnit.SECONDS);   // DATETIME guarda segundos
            if (modificado.equals(asociacion.libroLeido(canal))) return;
            List<PeticionAsociaciones.Par> pares;
            Path temporal = Files.createTempFile("libro-ventas-", ".xlsm");
            try {
                drive.descargarA(id, temporal);
                pares = LibroVentas.leer(temporal, LocalDate.now(clock).minusDays(DIAS));
            } finally {
                Files.deleteIfExists(temporal);
            }
            RespuestaAsociaciones r = asociacion.aplicarLibro(canal, pares, LocalDateTime.now(clock), modificado);
            log.info("Libros de ventas: {} leido (modificado el {}): {} pares; {} (ajenos = pedidos sin filas en el ERP, fuera de la lista)", canal, modificado, pares.size(), r);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            String mensaje = mensaje(e);
            log.warn("Libros de ventas: no se pudo leer el de {}: {}", canal, mensaje);
            try {
                asociacion.apuntarError(canal, mensaje, LocalDateTime.now(clock));
            } catch (RuntimeException alApuntar) {
                log.warn("Libros de ventas: no se pudo apuntar el error de {}: {}", canal, alApuntar.getMessage());
            }
        }
    }

    /** El texto que se guarda y enseña la web: el de {@link LibroVentasException} tal cual; los de Drive, con lo que fallo. */
    static String mensaje(Exception e) {
        String texto = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (e instanceof IOException) texto = "no se pudo leer el libro de Drive: " + texto;
        else if (!(e instanceof LibroVentasException)) texto = "fallo al leer o aplicar el libro: " + texto;
        return texto.length() > MAX_ERROR ? texto.substring(0, MAX_ERROR) : texto;
    }
}
