package com.reparaciones.servidor.security;

import com.reparaciones.servidor.model.EvaluacionPassword;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * La regla de las contraseñas que elige una persona (alta ya no: la genera el servidor). La aplican el cambio de
 * contraseña (normal y obligatorio) al guardar y {@code /api/auth/evaluar-password} mientras se escribe, así que la barra
 * de la web y el rechazo al guardar no pueden discrepar. Comprueba en orden y se para en la primera que falla: vacía,
 * menos de 10 caracteres, más de 64 o de 72 bytes (BCrypt solo usa los 72 primeros), igual que la actual y nota por
 * debajo del umbral del rol (3, o 4 para ADMIN). La nota se calcula siempre que se pueda, para que la barra la enseñe
 * aunque falle otra regla.
 */
@Component
public class PoliticaPassword {

    public static final int MIN_CARACTERES    = 10;
    public static final int MAX_CARACTERES    = 64;
    public static final int MAX_BYTES         = 72;
    public static final int NOTA_MINIMA       = 3;
    public static final int NOTA_MINIMA_ADMIN = 4;
    /** Por encima ni se mide: ya es demasiado larga y medir cadenas enormes solo gasta CPU. */
    private static final int MAX_A_MEDIR = 200;

    public static final String MSG_RELLENA     = "Rellena todos los campos.";
    public static final String MSG_CORTA       = "La contraseña debe tener al menos 10 caracteres.";
    public static final String MSG_LARGA       = "La contraseña es demasiado larga (máximo 64 caracteres).";
    public static final String MSG_IGUAL       = "La nueva contraseña tiene que ser distinta de la actual.";
    public static final String MSG_POCO_SEGURA = "La contraseña es poco segura.";

    /** Palabras del oficio y de contraseñas típicas en español que el diccionario (en inglés) de zxcvbn no conoce. Las
     *  propias de la empresa no van aquí (el repo es público): llegan por {@code politica.password.palabras-propias}. */
    static final List<String> PALABRAS_GENERICAS = List.of(
            "taller", "reparaciones", "reparacion", "reparación", "tecnico", "técnico", "usuario", "admin",
            "administrador", "contraseña", "contrasena", "clave", "password", "iphone", "apple", "samsung", "movil",
            "móvil", "telefono", "teléfono", "pantalla", "bateria", "batería", "tienda", "hola", "teamo", "madrid",
            "barcelona", "españa", "espana", "futbol", "fútbol", "qwerty");

    private final MedidorFuerza medidor;
    private final List<String> palabrasPropias;

    public PoliticaPassword(MedidorFuerza medidor,
                            @Value("${politica.password.palabras-propias:}") String palabrasPropias) {
        this.medidor = medidor;
        this.palabrasPropias = Arrays.stream(palabrasPropias == null ? new String[0] : palabrasPropias.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * @param actual        la contraseña actual escrita por la persona, o null si no se conoce (la barra)
     * @param nombreTecnico nombre visible del técnico, o null si la cuenta no tiene técnico
     * @param rol           ADMIN, SUPERTECNICO o TECNICO
     */
    public EvaluacionPassword evaluar(String password, String actual, String nombreUsuario, String nombreTecnico,
                                      String rol) {
        if (password == null || password.isEmpty()) return new EvaluacionPassword(0, false, MSG_RELLENA);

        MedidorFuerza.Medida medida = password.length() > MAX_A_MEDIR
                ? new MedidorFuerza.Medida(0, List.of())
                : medidor.medir(password, palabras(nombreUsuario, nombreTecnico));
        int nota = medida.nota();

        String mensaje;
        if (password.length() < MIN_CARACTERES) {
            mensaje = MSG_CORTA;
        } else if (password.length() > MAX_CARACTERES
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            mensaje = MSG_LARGA;
        } else if (actual != null && password.equals(actual)) {
            mensaje = MSG_IGUAL;
        } else if (nota < umbral(rol)) {
            mensaje = medida.consejos().isEmpty() ? MSG_POCO_SEGURA : MSG_POCO_SEGURA + " " + medida.consejos().get(0);
        } else {
            mensaje = null;
        }
        return new EvaluacionPassword(nota, mensaje == null, mensaje);
    }

    private static int umbral(String rol) {
        return "ADMIN".equalsIgnoreCase(rol) ? NOTA_MINIMA_ADMIN : NOTA_MINIMA;
    }

    /** Lo que zxcvbn trata como "datos del usuario": el nombre de usuario, el del técnico y cada una de sus palabras, las
     *  genéricas y las propias. En minúsculas y sin repetir. */
    private List<String> palabras(String nombreUsuario, String nombreTecnico) {
        Set<String> todas = new LinkedHashSet<>();
        anadir(todas, nombreUsuario);
        anadir(todas, nombreTecnico);
        if (nombreTecnico != null) {
            for (String trozo : nombreTecnico.trim().split("\\s+")) {
                if (trozo.length() >= 3) anadir(todas, trozo);
            }
        }
        todas.addAll(PALABRAS_GENERICAS);
        todas.addAll(palabrasPropias);
        return new ArrayList<>(todas);
    }

    private static void anadir(Set<String> destino, String palabra) {
        if (palabra == null) return;
        String limpia = palabra.trim().toLowerCase(Locale.ROOT);
        if (!limpia.isEmpty()) destino.add(limpia);
    }
}
