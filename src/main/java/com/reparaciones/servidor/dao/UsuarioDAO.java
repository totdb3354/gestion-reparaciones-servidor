package com.reparaciones.servidor.dao;

import com.reparaciones.servidor.model.Usuario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class UsuarioDAO {

    private final JdbcTemplate    jdbc;
    private final PasswordEncoder passwordEncoder;

    // Usuario.ID_TEC es la FK → Tecnico. ACTIVO está solo en Tecnico.
    private static final RowMapper<Usuario> MAPPER = (rs, row) -> {
        int     idUsu         = rs.getInt("ID_USU");
        String  nombreUsuario = rs.getString("NOMBRE_USUARIO");
        String  rol           = rs.getString("ROL");
        int     idTecRaw      = rs.getInt("ID_TEC");
        Integer idTec         = rs.wasNull() ? null : idTecRaw;
        String  nombreTecnico = rs.getString("NOMBRE_TECNICO");
        boolean activo        = rs.getBoolean("ACTIVO");
        return new Usuario(idUsu, nombreUsuario, rol, idTec, nombreTecnico, activo);
    };

    public UsuarioDAO(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc            = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Usuario> getUsuariosTecnicos() {
        String sql = """
                SELECT u.ID_USU, u.NOMBRE_USUARIO, u.ROL,
                       u.ID_TEC, t.NOMBRE AS NOMBRE_TECNICO, t.ACTIVO
                FROM Usuario u
                JOIN Tecnico t ON u.ID_TEC = t.ID_TEC
                WHERE u.ROL IN ('TECNICO', 'SUPERTECNICO')
                ORDER BY t.NOMBRE
                """;
        return jdbc.query(sql, MAPPER);
    }

    @Transactional
    public void registrarTecnico(String nombreTecnico, String nombreUsuario, String password, String rol) {
        if (!rol.equals("TECNICO") && !rol.equals("SUPERTECNICO"))
            throw new IllegalArgumentException("Rol no permitido: " + rol);
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO Tecnico (NOMBRE, ACTIVO) VALUES (?, 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, nombreTecnico);
            return ps;
        }, kh);
        int    idTec = kh.getKey().intValue();
        String hash  = passwordEncoder.encode(password);
        jdbc.update(
                "INSERT INTO Usuario (NOMBRE_USUARIO, PASSWORD, ROL, ID_TEC) VALUES (?, ?, ?, ?)",
                nombreUsuario, hash, rol, idTec);
    }

    // Solo actualiza Tecnico.ACTIVO — no hay ACTIVO en Usuario
    public void activarTecnico(int idTec) {
        jdbc.update("UPDATE Tecnico SET ACTIVO = 1 WHERE ID_TEC = ?", idTec);
    }

    public void desactivarTecnico(int idTec) {
        jdbc.update("UPDATE Tecnico SET ACTIVO = 0 WHERE ID_TEC = ?", idTec);
    }

    // Solo actualiza Tecnico.ES_ESTADISTICA — espejo de activar/desactivar
    public void excluirEstadisticas(int idTec) {
        jdbc.update("UPDATE Tecnico SET ES_ESTADISTICA = 0 WHERE ID_TEC = ?", idTec);
    }

    public void incluirEstadisticas(int idTec) {
        jdbc.update("UPDATE Tecnico SET ES_ESTADISTICA = 1 WHERE ID_TEC = ?", idTec);
    }

    /** Las columnas que apuntan a Tecnico.ID_TEC con FK (sql/crear_bd.sql :180-183). */
    static final List<String> REFERENCIAS_TECNICO = List.of(
            "SELECT EXISTS(SELECT 1 FROM Reparacion WHERE ID_TEC = ?)",
            "SELECT EXISTS(SELECT 1 FROM Reparacion WHERE ID_TEC_ASIGNA = ?)",
            "SELECT EXISTS(SELECT 1 FROM Reparacion WHERE ENTREGADO_POR = ?)");

    /** Las columnas que apuntan a Usuario.ID_USU con FK (sql/crear_bd.sql :130-131, :144, :160, :316, :346), salvo
     *  Log_Actividad, que eliminarTecnico borra a propósito (calco, spec 6 G8). */
    static final List<String> REFERENCIAS_USUARIO = List.of(
            "SELECT EXISTS(SELECT 1 FROM Revision WHERE EST_ID_USU = ?)",
            "SELECT EXISTS(SELECT 1 FROM Revision WHERE FUN_ID_USU = ?)",
            "SELECT EXISTS(SELECT 1 FROM Envio WHERE ID_USU = ?)",
            "SELECT EXISTS(SELECT 1 FROM Envio_Telefono WHERE ID_USU_DEVOLUCION = ?)",
            "SELECT EXISTS(SELECT 1 FROM Solicitud_Stock WHERE ID_USU = ?)",
            "SELECT EXISTS(SELECT 1 FROM Movimiento_telefono WHERE ID_USU = ?)");

    public boolean existeTecnico(int idTec) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM Tecnico WHERE ID_TEC = ?", Integer.class, idTec);
        return n != null && n > 0;
    }

    /** El usuario de un técnico, o null si el técnico no tiene fila en Usuario. */
    public Integer getIdUsuByIdTec(int idTec) {
        List<Integer> ids = jdbc.queryForList("SELECT ID_USU FROM Usuario WHERE ID_TEC = ?", Integer.class, idTec);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** true si el técnico o su usuario aparecen en cualquiera de las nueve columnas con FK hacia ellos (spec 6 §4.2):
     *  lo que haría fallar el borrado por integridad. Una consulta por referencia, parando en la primera que exista. */
    public boolean tieneReferencias(int idTec) {
        for (String sql : REFERENCIAS_TECNICO) {
            if (existe(sql, idTec)) return true;
        }
        Integer idUsu = getIdUsuByIdTec(idTec);
        if (idUsu == null) return false;
        for (String sql : REFERENCIAS_USUARIO) {
            if (existe(sql, idUsu)) return true;
        }
        return false;
    }

    private boolean existe(String sql, int id) {
        Integer n = jdbc.queryForObject(sql, Integer.class, id);
        return n != null && n > 0;
    }

    /** Se conserva por compatibilidad: desde el sub-proyecto 6 mira todas las referencias, no solo Reparacion.ID_TEC. */
    public boolean tieneReparaciones(int idTec) {
        return tieneReferencias(idTec);
    }

    @Transactional
    public void eliminarTecnico(int idTec, int idUsu) {
        jdbc.update("DELETE FROM Log_Actividad WHERE ID_USU = ?", idUsu);
        jdbc.update("DELETE FROM Usuario WHERE ID_USU = ?", idUsu);
        jdbc.update("DELETE FROM Tecnico WHERE ID_TEC = ?", idTec);
    }

    @Transactional
    public void cambiarPassword(int idUsu, String passwordActual, String passwordNueva) {
        String hashActual;
        try {
            hashActual = jdbc.queryForObject(
                    "SELECT PASSWORD FROM Usuario WHERE ID_USU = ?", String.class, idUsu);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            // Mismo mensaje que contraseña incorrecta para no revelar si el usuario existe
            throw new IllegalArgumentException("Contraseña actual incorrecta.");
        }
        if (!passwordEncoder.matches(passwordActual, hashActual))
            throw new IllegalArgumentException("Contraseña actual incorrecta.");
        String hashNuevo = passwordEncoder.encode(passwordNueva);
        jdbc.update("UPDATE Usuario SET PASSWORD = ? WHERE ID_USU = ?", hashNuevo, idUsu);
    }

    public String getNombreByIdTec(int idTec) {
        return jdbc.queryForObject(
                "SELECT NOMBRE FROM Tecnico WHERE ID_TEC = ?", String.class, idTec);
    }

    public boolean existeNombreTecnico(String nombre) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Tecnico WHERE LOWER(TRIM(NOMBRE)) = LOWER(TRIM(?))",
                Integer.class, nombre);
        return n != null && n > 0;
    }

    public boolean existeNombreUsuario(String nombreUsuario) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Usuario WHERE LOWER(TRIM(NOMBRE_USUARIO)) = LOWER(TRIM(?))",
                Integer.class, nombreUsuario);
        return n != null && n > 0;
    }
}
