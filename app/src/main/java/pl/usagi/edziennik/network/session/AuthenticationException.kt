package pl.usagi.edziennik.network.session

sealed class AuthenticationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class LoginFormNotFound : AuthenticationException("Login form was not found.")
    class UnsafeFederationDestination : AuthenticationException("Untrusted authentication destination.")
    class WsFederationCycleDetected : AuthenticationException("WS-Federation cycle detected.")

    class InvalidCredentials : AuthenticationException("Login credentials were rejected.")

    class WsFederationFormNotFound(
        statusCode: Int,
        url: String,
    ) : AuthenticationException("WS-Federation form was not found. Status: $statusCode, URL: $url")

    class WsFederationStepLimitExceeded(
        targetHost: String,
    ) : AuthenticationException("WS-Federation step limit exceeded for $targetHost.")

    class MainApplicationAuthenticationFailed(
        host: String?,
    ) : AuthenticationException("Main UONET+ authentication failed. Final host: $host.")

    class SessionNotActive : AuthenticationException("Login finished but UONET+ session is not active.")
    class SessionVerificationUnavailable : AuthenticationException("Session verification is temporarily unavailable.")

    class Network(
        cause: Throwable,
    ) : AuthenticationException("Network request failed.", cause)

    class Http(
        val statusCode: Int,
        url: String,
    ) : AuthenticationException("HTTP request failed. Status: $statusCode, URL: $url")
}
