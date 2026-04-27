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
                WHERE u.ROL = 'TECNICO'
                ORDER BY t.NOMBRE
                """;
        return jdbc.query(sql, MAPPER);
    }

    @Transactional
    public void registrarTecnico(String nombreTecnico, String nombreUsuario, String password) {
        // Tecnico primero (AUTO_INCREMENT), luego Usuario con el ID_TEC generado
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
                "INSERT INTO Usuario (NOMBRE_USUARIO, PASSWORD, ROL, ID_TEC) VALUES (?, ?, 'TECNICO', ?)",
                nombreUsuario, hash, idTec);
    }

    // Solo actualiza Tecnico.ACTIVO — no hay ACTIVO en Usuario
    public void activarTecnico(int idTec) {
        jdbc.update("UPDATE Tecnico SET ACTIVO = 1 WHERE ID_TEC = ?", idTec);
    }

    public void desactivarTecnico(int idTec) {
        jdbc.update("UPDATE Tecnico SET ACTIVO = 0 WHERE ID_TEC = ?", idTec);
    }

    public boolean tieneReparaciones(int idTec) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM Reparacion WHERE ID_TEC = ?",
                Integer.class, idTec);
        return count != null && count > 0;
    }

    @Transactional
    public void eliminarTecnico(int idTec, int idUsu) {
        // Usuario primero (tiene FK → Tecnico), luego Tecnico
        jdbc.update("DELETE FROM Usuario WHERE ID_USU = ?", idUsu);
        jdbc.update("DELETE FROM Tecnico WHERE ID_TEC = ?", idTec);
    }

}
