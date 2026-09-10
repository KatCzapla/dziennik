package pl.katcz.edziennik.network.html

data class HtmlForm(
    val actionUrl: String,
    val fields: Map<String, String>,
)
