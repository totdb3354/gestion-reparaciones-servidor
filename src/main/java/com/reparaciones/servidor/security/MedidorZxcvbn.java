package com.reparaciones.servidor.security;

import com.nulabinc.zxcvbn.Feedback;
import com.nulabinc.zxcvbn.Strength;
import com.nulabinc.zxcvbn.Zxcvbn;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * {@link MedidorFuerza} con zxcvbn4j (el algoritmo zxcvbn de Dropbox): estima los intentos que haría falta buscando
 * contraseñas comunes, palabras de diccionario, patrones de teclado, series, fechas, sustituciones típicas y las
 * palabras del usuario. Una instancia para todo el proceso; {@code medir} va sincronizado porque la librería no dice si
 * es segura entre hilos, y a esta escala (unas pocas personas cambiando la contraseña) no cuesta nada.
 */
@Component
public class MedidorZxcvbn implements MedidorFuerza {

    /** Nombre base comprobado en el jar (Task 1, Step 2). */
    private static final String PAQUETE_MENSAJES = "com/nulabinc/zxcvbn/messages";
    private static final ResourceBundle MENSAJES = ResourceBundle.getBundle(PAQUETE_MENSAJES, Locale.forLanguageTag("es"));

    private final Zxcvbn zxcvbn = new Zxcvbn();

    @Override
    public synchronized Medida medir(String password, List<String> palabrasDelUsuario) {
        Strength s = zxcvbn.measure(password, palabrasDelUsuario);
        Feedback f = s.getFeedback().withResourceBundle(MENSAJES);
        List<String> consejos = new ArrayList<>();
        if (f.getWarning() != null && !f.getWarning().isBlank()) consejos.add(f.getWarning());
        for (String c : f.getSuggestions()) {
            if (c != null && !c.isBlank()) consejos.add(c);
        }
        return new Medida(s.getScore(), List.copyOf(consejos));
    }
}
