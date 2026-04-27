package com.reparaciones.servidor.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UsuarioPrincipal implements UserDetails {

    private final int idUsu;
    private final String nombreUsuario;
    private final String password;
    private final String rol;
    private final Integer idTec;

    public UsuarioPrincipal(int idUsu, String nombreUsuario, String password, String rol, Integer idTec) {
        this.idUsu         = idUsu;
        this.nombreUsuario = nombreUsuario;
        this.password      = password;
        this.rol           = rol;
        this.idTec         = idTec;
    }

    public int     getIdUsu() { return idUsu; }
    public String  getRol()   { return rol; }
    public Integer getIdTec() { return idTec; }

    @Override public String  getUsername()               { return nombreUsuario; }
    @Override public String  getPassword()               { return password; }
    @Override public boolean isEnabled()                 { return true; }
    @Override public boolean isAccountNonExpired()       { return true; }
    @Override public boolean isAccountNonLocked()        { return true; }
    @Override public boolean isCredentialsNonExpired()   { return true; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol));
    }
}
