package com.rommelguerra.prototipomusichaptic.integracion

/** ÚNICO LUGAR DONDE SE ELIGEN LAS IMPLEMENTACIONES.
 * No es necesario modificar Compose, el reproductor ni la captura del micrófono.
 * Cree instancias nuevas: los algoritmos pueden acumular estado por sesión.
 */
object RegistroAlgoritmos {
    // TODO(INTEGRAR_MUSICA): por ejemplo, return AlgoritmoMusicaDelEquipo()
    fun crearMusica(): AlgoritmoMusica = AlgoritmoMusicaPendiente()

    fun crearCotidiano(context: android.content.Context, perfil: PerfilEntorno): AlgoritmoCotidiano =
        AlgoritmoEntornoYamnet(context, perfil)
}
