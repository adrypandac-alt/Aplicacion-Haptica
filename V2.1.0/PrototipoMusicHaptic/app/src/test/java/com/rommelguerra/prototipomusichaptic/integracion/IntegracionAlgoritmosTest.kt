package com.rommelguerra.prototipomusichaptic.integracion

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class IntegracionAlgoritmosTest {
    private val patron = PatronHaptico(listOf(0L, 120L))

    @Test fun `los algoritmos pendientes no generan resultados ficticios`() = runBlocking {
        val audio = BloqueAudio(FloatArray(2048), 16000, 0)
        val musica = RegistroAlgoritmos.crearMusica()
        val cotidiano = AlgoritmoCotidianoPendiente()
        assertFalse(musica.disponible)
        assertFalse(cotidiano.disponible)
        assertTrue(musica.procesar(audio).isEmpty())
        assertNull(cotidiano.detectar(audio))
    }

    @Test fun `una implementacion musical se conecta con la linea de tiempo`() = runBlocking {
        val algoritmo = object : AlgoritmoMusica {
            override suspend fun procesar(bloque: BloqueAudio) = listOf(EventoMusical(bloque.tiempoInicioMs, "DO", patron))
        }
        val linea = SecuenciadorMusical(algoritmo.procesar(BloqueAudio(floatArrayOf(0.5f), 44100, 500)))
        assertNull(linea.avanzar(499))
        assertEquals("DO", linea.avanzar(500)?.nota)
        assertNull(linea.avanzar(550)) // No repetir vibración en cada actualización visual.
    }

    @Test fun `buscar evita notas anteriores y permite volver a reproducir`() {
        val linea = SecuenciadorMusical(listOf(EventoMusical(100, "DO", patron), EventoMusical(900, "RE", patron)))
        linea.buscar(500)
        assertNull(linea.avanzar(600))
        assertEquals("RE", linea.avanzar(950)?.nota)
        linea.buscar(0)
        assertEquals("DO", linea.avanzar(100)?.nota)
        linea.buscar(2000)
        assertNull(linea.avanzar(3000))
    }

    @Test fun `rechaza patrones invalidos y confianza fuera del contrato`() {
        listOf(listOf(-1L, 100L), listOf(0L, 6000L), listOf(0L, 0L)).forEach {
            assertTrue(runCatching { PatronHaptico(it) }.isFailure)
        }
        assertTrue(runCatching { PatronHaptico(listOf(0L, 100L), 256) }.isFailure)
        assertTrue(runCatching { DeteccionCotidiana(SonidoCotidiano.TIMBRE, Float.NaN, patron) }.isFailure)
        assertTrue(runCatching { DeteccionCotidiana(SonidoCotidiano.TIMBRE, 1.5f, patron) }.isFailure)
    }
}

