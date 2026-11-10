package app.betternotes.ui.library

import app.betternotes.core.template.CoverDesign
import java.util.Locale

/** The Spanish names of the covers, by design id. The design's own name is English. */
private val SPANISH_NAMES = mapOf(
    "custom" to "Personalizada",
    "simple" to "Simple",
    "simple-yellow" to "Simple",
    "simple-allblack" to "Simple todo negro",
    "simple-black" to "Simple negro",
    "simple-blue" to "Simple azul",
    "simple-green" to "Simple verde",
    "simple-pink" to "Simple rosa",
    "simple-red" to "Simple rojo",
    "solid-babyblue" to "Liso celeste",
    "solid-blue" to "Liso azul",
    "solid-green" to "Liso verde",
    "solid-gray" to "Liso gris",
    "solid-pink" to "Liso rosa",
    "solid-red" to "Liso rojo",
    "solid-teal" to "Liso turquesa",
    "solid-white" to "Liso blanco",
    "solid-yellow" to "Liso amarillo",
    "enclosed-1" to "Cerrada 1",
    "enclosed-2" to "Cerrada 2",
    "enclosed-3" to "Cerrada 3",
    "enclosed-4" to "Cerrada 4",
    "enclosed-5" to "Cerrada 5",
    "circle-and-dots" to "Círculo y puntos",
    "geometric" to "Geométrica",
    "line-waves" to "Ondas de líneas",
    "line-waves-black" to "Ondas de líneas negra",
    "letters" to "Letras",
    "brown-dots" to "Puntos",
    "brown-sticks" to "Palitos",
    "brown-waves" to "Ondas",
    "brown-zigzag" to "Zigzag",
    "brown-checkbox" to "Cuadros",
    "plain-white" to "Plana blanca",
    "plain-sand" to "Plana arena",
    "plain-charcoal" to "Plana carbón",
    "plain-sage" to "Plana salvia",
    "plain-gray" to "Plana gris",
    "koi" to "Koi",
    "fluid" to "Fluida",
    "goood" to "GOOOD",
    "liquid" to "Líquida",
    "lotus" to "Loto",
)

/** The cover's name in the app's language. */
internal fun CoverDesign.displayName(): String =
    if (Locale.getDefault().language == "es") SPANISH_NAMES[id] ?: name else name
