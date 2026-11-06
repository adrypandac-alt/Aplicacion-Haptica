package com.rommelguerra.prototipomusichaptic.integracion

import org.junit.Assert.*
import org.junit.Test

class CatalogoEntornoTest {
    @Test fun `separa perfiles y nunca confunde voz con nombre propio`() {
        assertEquals(SonidoCotidiano.PERRO, CatalogoEntorno.clasificar("Bark", PerfilEntorno.SOCIAL))
        assertEquals(SonidoCotidiano.BEBE, CatalogoEntorno.clasificar("Baby cry, infant cry", PerfilEntorno.SOCIAL))
        assertNull(CatalogoEntorno.clasificar("Speech", PerfilEntorno.SOCIAL))
        assertNull(CatalogoEntorno.clasificar("Siren", PerfilEntorno.SOCIAL))
        assertNull(CatalogoEntorno.clasificar("Bark", PerfilEntorno.VIAL))
        assertEquals(SonidoCotidiano.SIRENA, CatalogoEntorno.clasificar("Siren", PerfilEntorno.VIAL))
        assertEquals(SonidoCotidiano.BOCINA, CatalogoEntorno.clasificar("Vehicle horn, car horn, honking", PerfilEntorno.VIAL))
    }
    @Test fun `filtra scores invalidos y elige alerta mas confiable del perfil`() {
        val scores = listOf("Siren" to 0.4f, "Car alarm" to 0.8f, "Bark" to 0.99f, "Air horn, truck horn" to Float.NaN)
        assertEquals(SonidoCotidiano.ALARMA, CatalogoEntorno.elegir(scores, PerfilEntorno.VIAL)?.sonido)
        assertNull(CatalogoEntorno.elegir(listOf("Siren" to 0.34f), PerfilEntorno.VIAL))
        assertNull(CatalogoEntorno.elegir(listOf("Siren" to 1.2f), PerfilEntorno.VIAL))
    }
    @Test fun `patrones y comandos conservan contratos de firmware`() {
        assertEquals(listOf("DOG", "BABY", "NAME"), CatalogoEntorno.sonidos(PerfilEntorno.SOCIAL).map(CatalogoEntorno::comando))
        assertEquals(listOf("SIREN", "HORN", "ALARM"), CatalogoEntorno.sonidos(PerfilEntorno.VIAL).map(CatalogoEntorno::comando))
        PerfilEntorno.entries.flatMap(CatalogoEntorno::sonidos).forEach {
            assertTrue(CatalogoEntorno.patron(it).tiemposMs.sum() in 1..5000)
        }
        assertEquals(1860L, CatalogoEntorno.patron(SonidoCotidiano.PERRO).tiemposMs.sum())
    }
}
