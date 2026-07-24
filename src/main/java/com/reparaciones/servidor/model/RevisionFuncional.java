package com.reparaciones.servidor.model;

/** Campos de la parte funcional de una revisión (check marcado = defecto). */
public record RevisionFuncional(Integer bateriaPct, boolean pantTactil, boolean pantQuemada,
                                boolean pantMal, boolean camMancha, boolean camLente,
                                boolean altSup, boolean altInf, boolean mic, boolean faceId,
                                boolean ms, String msTexto, boolean bloqueoOp, String observacion) {}
