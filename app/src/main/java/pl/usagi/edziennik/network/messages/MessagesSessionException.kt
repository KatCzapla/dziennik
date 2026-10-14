package pl.usagi.edziennik.network.messages

sealed class MessagesSessionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    data object MainSessionInvalid : MessagesSessionException("Main UONET+ session is not active.")

    data object MissingMessagesUrl : MessagesSessionException("UONET+ main app does not contain messages URL.")

    class InvalidMessagesUrl(cause: Throwable? = null) :
        MessagesSessionException("Messages URL from UONET+ main app is invalid.", cause)

    class UrlSourceRequestFailed(cause: Throwable) :
        MessagesSessionException("Could not resolve Messages URL from UONET+ main app.", cause)

    class WsFederation(cause: Throwable) : MessagesSessionException("Messages WS-Federation failed.", cause)

    class MissingBootstrapData(name: String) :
        MessagesSessionException("Messages bootstrap data is missing: $name.")

    class Http(val statusCode: Int) : MessagesSessionException("Messages API request failed with HTTP $statusCode.")

    class Network(cause: Throwable) : MessagesSessionException("Could not connect to Messages API.", cause)

    class UnexpectedResponse(cause: Throwable? = null) :
        MessagesSessionException("Messages API response did not match the expected contract.", cause)
}
