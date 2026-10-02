package tachiyomi.source.local.di

import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XML

interface LocalSourceGraph {
    val json: Json
    val xml: XML
}
