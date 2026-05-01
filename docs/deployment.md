# Guía de despliegue — Gestión Reparaciones (servidor)

Pasos para desplegar el servidor Spring Boot en una VM Linux con MariaDB.

---

## Requisitos previos (en la VM)

- Java 17 JRE: `sudo apt install openjdk-17-jre-headless`
- MariaDB 10.6+: `sudo apt install mariadb-server`
- Puerto 8080 abierto en el firewall de la VM (o 443 con HTTPS)

---

## 1. Preparar la base de datos

```sql
-- Conectar como root: sudo mariadb

-- Crear usuario dedicado para la aplicación
CREATE USER 'reparaciones'@'localhost' IDENTIFIED BY 'contraseña-segura';
GRANT ALL PRIVILEGES ON reparaciones.* TO 'reparaciones'@'localhost';
FLUSH PRIVILEGES;
```

Ejecutar el script del esquema (está en el repo cliente):

```bash
mariadb -u root < /ruta/al/crear_bd.sql
```

Insertar el usuario admin inicial (sustituir el hash por uno generado con BCrypt):

```sql
USE reparaciones;
INSERT INTO Tecnico (NOMBRE) VALUES ('Administrador');
INSERT INTO Usuario (NOMBRE_USUARIO, PASSWORD, ROL, ID_TEC)
  VALUES ('admin', '$2a$10$<hash-bcrypt>', 'ADMIN', LAST_INSERT_ID());
```

Para generar el hash BCrypt arrancar el servidor una vez y acceder a:
`GET http://localhost:8080/api/auth/setup-hash?raw=<contraseña>`  
> Este endpoint temporal solo debe añadirse durante el arranque inicial y eliminarse después.

---

## 2. Construir el JAR

En la máquina de desarrollo (o en la VM si tiene Maven):

```bash
cd gestion-reparaciones-servidor
mvn clean package -DskipTests
```

El JAR se genera en `target/gestion-reparaciones-servidor-<version>.jar`.

---

## 3. Configurar `application.properties` para producción

Editar `src/main/resources/application.properties` **antes** de construir el JAR, o sobreescribir propiedades al arrancar (ver paso 4).

```properties
# BD: apuntar a la IP/socket de la VM
spring.datasource.url=jdbc:mariadb://localhost:3306/reparaciones
spring.datasource.username=reparaciones
spring.datasource.password=contraseña-segura

# JWT: cadena aleatoria, mínimo 32 caracteres
# Generar con: openssl rand -base64 48
jwt.secret=reemplazar-esto-con-cadena-aleatoria-larga-minimo-32-chars
jwt.expiration=86400000
```

Para no recompilar si solo cambian las propiedades, crear un fichero externo y referenciarlo al arrancar:

```bash
java -jar app.jar --spring.config.location=file:/etc/reparaciones/application.properties
```

---

## 4. Arrancar el servidor

Prueba manual:

```bash
java -jar target/gestion-reparaciones-servidor-*.jar
```

Verificar que responde:

```bash
curl http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usuario":"admin","password":"admin123"}'
```

---

## 5. Configurar como servicio systemd

Crear `/etc/systemd/system/reparaciones.service`:

```ini
[Unit]
Description=Gestion Reparaciones - Servidor Spring Boot
After=network.target mariadb.service

[Service]
Type=simple
User=reparaciones
ExecStart=/usr/bin/java -jar /opt/reparaciones/gestion-reparaciones-servidor.jar \
  --spring.config.location=file:/etc/reparaciones/application.properties
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Activar y arrancar:

```bash
sudo systemctl daemon-reload
sudo systemctl enable reparaciones
sudo systemctl start reparaciones
sudo systemctl status reparaciones
```

Logs:

```bash
sudo journalctl -u reparaciones -f
```

---

## 6. Firewall

Con UFW (Ubuntu):

```bash
sudo ufw allow 8080/tcp    # HTTP directo
# o si usas HTTPS con Nginx:
sudo ufw allow 443/tcp
sudo ufw allow 80/tcp
```

Restringir por IP si el acceso solo es desde IPs conocidas:

```bash
sudo ufw allow from <ip-del-taller> to any port 8080
```

---

## 7. HTTPS con Nginx (recomendado para producción)

Instalar Nginx y configurarlo como proxy inverso:

```nginx
server {
    listen 443 ssl;
    server_name <ip-o-dominio>;

    ssl_certificate     /etc/ssl/certs/reparaciones.crt;
    ssl_certificate_key /etc/ssl/private/reparaciones.key;

    location / {
        proxy_pass         http://localhost:8080;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
    }
}
```

Actualizar `api.url` en `config.properties` del cliente a `https://<ip-o-dominio>`.

---

## 8. Actualizar el cliente

En `gestion-reparaciones-cliente/src/main/resources/config.properties`:

```properties
# Cambiar a la IP/dominio de la VM
api.url=https://<ip-vm>
```

Recompilar y distribuir el JAR del cliente a cada PC del taller.

---

## Checklist de arranque en producción

- [ ] MariaDB arrancado y `reparaciones` DB creada
- [ ] `application.properties` con credenciales reales
- [ ] `jwt.secret` con cadena aleatoria (≥ 32 chars)
- [ ] Usuario admin inicial insertado con hash BCrypt correcto
- [ ] Servicio systemd activo y arranca con el sistema
- [ ] Firewall configurado (solo IPs del taller o VPN)
- [ ] HTTPS configurado (Nginx o certificado en Spring Boot)
- [ ] Cliente con `api.url` apuntando a la VM
- [ ] Login verificado desde un PC del taller
