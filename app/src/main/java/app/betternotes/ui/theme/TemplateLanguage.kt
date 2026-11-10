package app.betternotes.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.betternotes.core.format.DocumentStore
import java.util.Locale

/** The language of the words printed on built-in templates (semester, mind map, week planner). */
enum class TemplateLanguage(val key: String, val code: String?) {
    /** Follows the app's language. */
    AUTO("auto", null),
    ENGLISH("en", "en"),
    SPANISH("es", "es");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/**
 * What the template painters ask for. [chosen] is the app-wide choice, which new notebooks start with. Each notebook keeps
 * its own language beside its file, so changing the app's choice later does not touch notebooks already made; the notebook
 * that is open sets [documentLanguage], which wins while it is open. Observable, so previews follow a change at once.
 */
object TemplateText {
    var chosen by mutableStateOf(TemplateLanguage.AUTO)

    /** The open notebook's language, or null when none is open. */
    var documentLanguage by mutableStateOf<String?>(null)

    private fun appCode(): String = chosen.code ?: Locale.getDefault().language.let { if (it == "es") "es" else "en" }

    /** "es" or "en". */
    fun code(): String = documentLanguage ?: appCode()

    /** The app-wide choice for a new notebook, whatever notebook is open. */
    fun newNotebookCode(): String = appCode()
}

/**
 * The notebook's language. A notebook made before it had one takes the app's choice the first time it is looked at, and keeps
 * that from then on, so a later change in the app does not alter the words on its pages.
 */
fun DocumentStore.templateLanguage(): String =
    readTemplateLanguage() ?: TemplateText.newNotebookCode().also { writeTemplateLanguage(it, touch = false) }
