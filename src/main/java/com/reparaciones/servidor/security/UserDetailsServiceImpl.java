package com.reparaciones.servidor.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final JdbcTemplate jdbc;

    public UserDetailsServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // ACTIVO está en Tecnico, no en Usuario. Los admins (ID_TEC IS NULL) siempre pueden entrar.
        String sql = """
                SELECT u.ID_USU, u.NOMBRE_USUARIO, u.PASSWORD, u.ROL, u.ID_TEC
                FROM Usuario u
                LEFT JOIN Tecnico t ON u.ID_TEC = t.ID_TEC
                WHERE u.NOMBRE_USUARIO = ?
                  AND (u.ID_TEC IS NULL OR t.ACTIVO = 1)
                """;

        return jdbc.query(sql, rs -> {
            if (!rs.next()) throw new UsernameNotFoundException("Usuario no encontrado: " + username);
            int     idUsu         = rs.getInt("ID_USU");
            String  nombreUsuario = rs.getString("NOMBRE_USUARIO");
            String  password      = rs.getString("PASSWORD");
            String  rol           = rs.getString("ROL");
            int     idTecRaw      = rs.getInt("ID_TEC");
            Integer idTec         = rs.wasNull() ? null : idTecRaw;
            return new UsuarioPrincipal(idUsu, nombreUsuario, password, rol, idTec);
        }, username);
    }
}
