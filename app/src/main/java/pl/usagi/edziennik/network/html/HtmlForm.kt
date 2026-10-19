package pl.usagi.edziennik.network.html

data class HtmlForm(
    val actionUrl: String,
    val fields: Map<String, String>,
    val method: String = "POST",
)
