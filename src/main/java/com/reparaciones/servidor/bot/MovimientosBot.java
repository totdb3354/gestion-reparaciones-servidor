package com.reparaciones.servidor.bot;

import java.util.List;

/** POST /api/bot/movimientos (spec §6.2): la vuelta del bot y lo que movio en ella. */
public record MovimientosBot(String run, List<MovimientoBot> movimientos) {}
