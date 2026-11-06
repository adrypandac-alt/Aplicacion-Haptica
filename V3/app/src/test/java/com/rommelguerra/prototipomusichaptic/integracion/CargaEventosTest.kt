package com.rommelguerra.prototipomusichaptic.integracion

import org.junit.Assert.*
import org.junit.Test

class CargaEventosTest {
    private val patron = PatronHaptico(listOf(0L, 100L))

    @Test(timeout = 10000) fun `veinte mil eventos y cien mil busquedas conservan el resultado`() {
        val eventos = List(20000) { EventoMusical(it * 10L, "N$it", patron) }
        val linea = SecuenciadorMusical(eventos)
        val inicio = System.nanoTime()
        repeat(100000) { paso ->
            val indice = (paso * 7919L % eventos.size).toInt()
            linea.buscar(indice * 10L)
            assertEquals("N$indice", linea.avanzar(indice * 10L)?.nota)
            assertNull(linea.avanzar(indice * 10L))
        }
        println("CARGA: 20000 eventos / 100000 búsquedas y avances: ${(System.nanoTime() - inicio) / 1000000} ms (JVM de escritorio)")
    }

    @Test fun `exceso de eventos se rechaza sin copiar ni alterar resultados previos`() {
        val acumulador = AcumuladorEventos()
        val evento = EventoMusical(0, "DO", patron)
        acumulador.agregar(listOf(evento))
        assertTrue(runCatching { acumulador.agregar(List(20000) { evento }) }.isFailure)
        assertEquals(1, acumulador.cantidad)
        acumulador.agregar(List(19999) { evento })
        assertEquals(20000, acumulador.cantidad)
        assertTrue(runCatching { acumulador.agregar(listOf(evento)) }.isFailure)
        assertEquals(20000, acumulador.cantidad)
    }

    @Test fun `salto masivo y notas simultaneas no repiten vibracion`() {
        val linea = SecuenciadorMusical(List(20000) { EventoMusical(0, "N$it", patron) })
        assertEquals("N19999", linea.avanzar(500000)?.nota)
        assertNull(linea.avanzar(500001))
        linea.buscar(0)
        assertEquals("N19999", linea.avanzar(0)?.nota)
    }
}
