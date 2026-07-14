package com.reparaciones.servidor.model;

import java.time.LocalDateTime;
import java.util.List;

/** Fila del inventario completo de teléfonos (vista IMEIs evolucionada, F2a). */
public class TelefonoInventario {
    private String imei;
    private String modelo;
    private Integer storageGb;
    private String color;
    private String gradoProveedor;
    private String gradoPropio;
    private boolean esEsim;
    private String estado;           // almacenado (null = histórico)
    private String estadoEfectivo;   // derivado (EN_REPARACION si trabajo abierto)
    private String ubicacion;        // derivada; null = fuera del ciclo
    private List<String> subUbicaciones;
    private boolean esDevolucion;
    private String observacion;
    private Integer idCli;
    private String cliente;
    private Integer idLote;
    private String batchNumber;
    private String proveedor;
    private boolean revisionLogistica;
    private LocalDateTime telefonoUpdatedAt;
    private int repHechas;
    private int glassHechas;
    private int pulHechos;
    private int pulAbiertos;
    private int glassAbiertos;
    private int normalAbiertos;
    private int incAbiertas;
    private int solicitudesPendientes;
    private LocalDateTime ultimaActividad;

    public TelefonoInventario() {}

    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }

    public String getModelo() { return modelo; }
    public void setModelo(String modelo) { this.modelo = modelo; }

    public Integer getStorageGb() { return storageGb; }
    public void setStorageGb(Integer storageGb) { this.storageGb = storageGb; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getGradoProveedor() { return gradoProveedor; }
    public void setGradoProveedor(String gradoProveedor) { this.gradoProveedor = gradoProveedor; }

    public String getGradoPropio() { return gradoPropio; }
    public void setGradoPropio(String gradoPropio) { this.gradoPropio = gradoPropio; }

    public boolean isEsEsim() { return esEsim; }
    public void setEsEsim(boolean esEsim) { this.esEsim = esEsim; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getEstadoEfectivo() { return estadoEfectivo; }
    public void setEstadoEfectivo(String estadoEfectivo) { this.estadoEfectivo = estadoEfectivo; }

    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }

    public List<String> getSubUbicaciones() { return subUbicaciones; }
    public void setSubUbicaciones(List<String> subUbicaciones) { this.subUbicaciones = subUbicaciones; }

    public boolean isEsDevolucion() { return esDevolucion; }
    public void setEsDevolucion(boolean esDevolucion) { this.esDevolucion = esDevolucion; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }

    public Integer getIdCli() { return idCli; }
    public void setIdCli(Integer idCli) { this.idCli = idCli; }

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }

    public Integer getIdLote() { return idLote; }
    public void setIdLote(Integer idLote) { this.idLote = idLote; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public String getProveedor() { return proveedor; }
    public void setProveedor(String proveedor) { this.proveedor = proveedor; }

    public boolean isRevisionLogistica() { return revisionLogistica; }
    public void setRevisionLogistica(boolean revisionLogistica) { this.revisionLogistica = revisionLogistica; }

    public LocalDateTime getTelefonoUpdatedAt() { return telefonoUpdatedAt; }
    public void setTelefonoUpdatedAt(LocalDateTime telefonoUpdatedAt) { this.telefonoUpdatedAt = telefonoUpdatedAt; }

    public int getRepHechas() { return repHechas; }
    public void setRepHechas(int repHechas) { this.repHechas = repHechas; }

    public int getGlassHechas() { return glassHechas; }
    public void setGlassHechas(int glassHechas) { this.glassHechas = glassHechas; }

    public int getPulHechos() { return pulHechos; }
    public void setPulHechos(int pulHechos) { this.pulHechos = pulHechos; }

    public int getPulAbiertos() { return pulAbiertos; }
    public void setPulAbiertos(int pulAbiertos) { this.pulAbiertos = pulAbiertos; }

    public int getGlassAbiertos() { return glassAbiertos; }
    public void setGlassAbiertos(int glassAbiertos) { this.glassAbiertos = glassAbiertos; }

    public int getNormalAbiertos() { return normalAbiertos; }
    public void setNormalAbiertos(int normalAbiertos) { this.normalAbiertos = normalAbiertos; }

    public int getIncAbiertas() { return incAbiertas; }
    public void setIncAbiertas(int incAbiertas) { this.incAbiertas = incAbiertas; }

    public int getSolicitudesPendientes() { return solicitudesPendientes; }
    public void setSolicitudesPendientes(int solicitudesPendientes) { this.solicitudesPendientes = solicitudesPendientes; }

    public LocalDateTime getUltimaActividad() { return ultimaActividad; }
    public void setUltimaActividad(LocalDateTime ultimaActividad) { this.ultimaActividad = ultimaActividad; }
}
