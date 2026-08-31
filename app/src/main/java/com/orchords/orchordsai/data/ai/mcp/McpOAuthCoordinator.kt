package com.orchords.orchordsai.data.ai.mcp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.orchords.oauth.OAuthAuthorizationLauncher
import com.orchords.oauth.OAuthHttpClient
import com.orchords.oauth.OAuthLoopbackCallbackServer
import com.orchords.orchordsai.AppScope
import com.orchords.orchordsai.data.datastore.SettingsStore
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private const val TAG = "McpOAuthCoordinator"
private const val TOKEN_REFRESH_LEEWAY_MS = 60_000L
internal const val MCP_OAUTH_CALLBACK_PORT = 52_134
internal const val MCP_OAUTH_CALLBACK_PATH = "/oauth/callback"
internal const val MCP_OAUTH_REDIRECT_URI =
    "http://127.0.0.1:$MCP_OAUTH_CALLBACK_PORT$MCP_OAUTH_CALLBACK_PATH"
private val OAUTH_CALLBACK_TIMEOUT = 5.minutes

/**
 *
 */
internal class McpOAuthCoordinator(
    private val settingsStore: SettingsStore,
    private val appScope: AppScope,
    private val oauthClient: OAuthHttpClient,
    private val discoveryClient: McpOAuthDiscoveryClient,
    private val callbackServer: OAuthLoopbackCallbackServer,
    private val authorizationLauncher: OAuthAuthorizationLauncher,
    private val updateStatus: (Uuid, McpStatus) -> Unit,
) {
    private val authorizationJobs = ConcurrentHashMap<Uuid, Job>()
    private val refreshLocks = ConcurrentHashMap<Uuid, Mutex>()

    fun startAuthorization(config: McpServerConfig, context: Context) {
        authorizationJobs.remove(config.id)?.cancel()
        val job = appScope.launch {
            updateStatus(config.id, McpStatus.Authorizing)
            try {
                authorize(config, context.applicationContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "OAuth authorization failed for ${config.commonOptions.name}", e)
                updateStatus(config.id, McpStatus.Error.from(e, fallbackMessage = "OAuth authorization failed"))
            }
        }
        authorizationJobs[config.id] = job
        job.invokeOnCompletion { authorizationJobs.remove(config.id, job) }
    }

    fun cancelAuthorization(configId: Uuid) {
        authorizationJobs.remove(configId)?.cancel()
        updateStatus(configId, McpStatus.NeedsAuthorization)
    }

    fun forget(configId: Uuid) {
        authorizationJobs.remove(configId)?.cancel()
        refreshLocks.remove(configId)
    }

    suspend fun clearAuthorization(config: McpServerConfig): McpServerConfig {
        persistOAuthState(config.id, null)
        return settingsStore.settingsFlow.value.mcpServers.find { it.id == config.id }
            ?: config.clone(commonOptions = config.commonOptions.copy(oauth = null))
    }

    /**
     */
    suspend fun ensureFreshToken(configInput: McpServerConfig): McpServerConfig {
        val lock = refreshLocks.computeIfAbsent(configInput.id) { Mutex() }
        return lock.withLock {
            val config = settingsStore.settingsFlow.value.mcpServers.find { it.id == configInput.id }
                ?: configInput
            val oauth = config.commonOptions.oauth ?: return@withLock config
            if (!oauth.enabled || oauth.refreshToken.isNullOrBlank()) return@withLock config

            val expired = oauth.expiresAt > 0 &&
                System.currentTimeMillis() >= oauth.expiresAt - TOKEN_REFRESH_LEEWAY_MS
            if (!oauth.accessToken.isNullOrBlank() && !expired) return@withLock config

            val tokenEndpoint = oauth.tokenEndpoint ?: return@withLock config
            val clientId = oauth.clientId ?: return@withLock config
            runCatching {
                val token = oauthClient.refreshToken(
                    OAuthHttpClient.RefreshTokenRequest(
                        tokenEndpoint = tokenEndpoint,
                        clientId = clientId,
                        clientSecret = oauth.clientSecret,
                        refreshToken = oauth.refreshToken,
                        resources = listOf(McpOAuthDiscoveryClient.canonicalResource(config.serverUrl)),
                        scope = oauth.scope,
                    )
                )
                val updated = oauth.copy(
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken ?: oauth.refreshToken,
                    expiresAt = computeExpiry(token.expiresIn),
                    scope = token.scope ?: oauth.scope,
                )
                persistOAuthState(config.id, updated)
                config.clone(commonOptions = config.commonOptions.copy(oauth = updated))
            }.getOrElse {
                Log.w(TAG, "Token refresh failed for ${config.commonOptions.name}: ${it.message}")
                config
            }
        }
    }

    suspend fun needsAuthorization(config: McpServerConfig, error: Throwable): Boolean {
        if (looksUnauthorized(error) && config.commonOptions.oauth?.enabled == true) return true
        if (config.commonOptions.headers.any { it.first.equals("Authorization", ignoreCase = true) }) {
            return false
        }
        return runCatching { discoveryClient.discoverProtectedResource(config.serverUrl) }
            .onFailure {
                Log.i(TAG, "OAuth probe failed for ${config.commonOptions.name}: ${it.message}")
            }
            .isSuccess
    }

    private suspend fun authorize(config: McpServerConfig, context: Context) = withContext(Dispatchers.IO) {
        val serverUrl = config.serverUrl
        require(serverUrl.isNotBlank()) { "Server URL is empty, cannot authorize" }

        val protectedResource = discoveryClient.discoverProtectedResource(serverUrl)
        val issuer = protectedResource.authorizationServers.firstOrNull()
            ?: error("Protected resource does not declare an authorization server")
        val metadata = discoveryClient.discoverAuthorizationServer(issuer)
        val authorizationEndpoint = metadata.authorizationEndpoint
            ?: error("Authorization server is missing authorization_endpoint")
        val tokenEndpoint = metadata.tokenEndpoint
            ?: error("Authorization server is missing token_endpoint")
        val scope = config.commonOptions.oauth?.scope
            ?: protectedResource.scopesSupported?.joinToString(" ")
            ?: metadata.scopesSupported?.joinToString(" ")

        val pkce = oauthClient.generatePkce()
        val state = oauthClient.generateState()
        val resource = McpOAuthDiscoveryClient.canonicalResource(serverUrl)
        val callbackSession = callbackServer.openSession(context, state)
        try {
            val redirectUri = callbackSession.redirectUri
            check(redirectUri == MCP_OAUTH_REDIRECT_URI) {
                "OAuth callback server address mismatch: $redirectUri"
            }
            val existing = config.commonOptions.oauth
            val canReuseClient = existing?.redirectUri == redirectUri && !existing.clientId.isNullOrBlank()
            var clientId = existing?.clientId.takeIf { canReuseClient }
            var clientSecret = existing?.clientSecret.takeIf { canReuseClient }
            if (clientId.isNullOrBlank()) {
                val registrationEndpoint = metadata.registrationEndpoint
                    ?: error("Authorization server does not support dynamic registration and no client_id is preconfigured")
                val registration = oauthClient.registerClient(
                    registrationEndpoint = registrationEndpoint,
                    request = OAuthHttpClient.ClientRegistrationRequest(
                        clientName = config.commonOptions.name.ifBlank { "ORCHORDS AI" },
                        redirectUris = listOf(redirectUri),
                        scope = scope,
                    ),
                )
                clientId = registration.clientId
                clientSecret = registration.clientSecret
            }

            persistOAuthState(
                config.id,
                (existing ?: McpOAuthState()).copy(
                    enabled = true,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    authorizationEndpoint = authorizationEndpoint,
                    tokenEndpoint = tokenEndpoint,
                    registrationEndpoint = metadata.registrationEndpoint,
                    redirectUri = redirectUri,
                    scope = scope,
                )
            )

            val authorizationUrl = oauthClient.buildAuthorizationUrl(
                OAuthHttpClient.AuthorizationRequest(
                    authorizationEndpoint = authorizationEndpoint,
                    clientId = clientId,
                    redirectUri = redirectUri,
                    pkce = pkce,
                    state = state,
                    scope = scope,
                    resources = listOf(resource),
                )
            )
            withContext(Dispatchers.Main) {
                authorizationLauncher.launch(context, authorizationUrl)
            }

            val callback = callbackSession.awaitCallback(OAUTH_CALLBACK_TIMEOUT)
                ?: error("Authorization timed out")
            callback.error?.let { error(buildAuthorizationError(it, callback.errorDescription)) }
            val code = callback.code ?: error("Authorization failed: no authorization code returned")

            val token = oauthClient.exchangeAuthorizationCode(
                OAuthHttpClient.AuthorizationCodeTokenRequest(
                    tokenEndpoint = tokenEndpoint,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    code = code,
                    codeVerifier = pkce.verifier,
                    redirectUri = redirectUri,
                    resources = listOf(resource),
                )
            )
            persistOAuthState(
                config.id,
                McpOAuthState(
                    enabled = true,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    authorizationEndpoint = authorizationEndpoint,
                    tokenEndpoint = tokenEndpoint,
                    registrationEndpoint = metadata.registrationEndpoint,
                    redirectUri = redirectUri,
                    scope = token.scope ?: scope,
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken,
                    expiresAt = computeExpiry(token.expiresIn),
                )
            )
        } finally {
            withContext(NonCancellable) {
                callbackSession.close()
            }
        }
    }

    private fun buildAuthorizationError(error: String, description: String?): String =
        if (description.isNullOrBlank()) "Authorization failed: $error" else "Authorization failed: $error ($description)"

    private suspend fun persistOAuthState(configId: Uuid, oauth: McpOAuthState?) {
        settingsStore.update { old ->
            old.copy(
                mcpServers = old.mcpServers.map { server ->
                    if (server.id != configId) server
                    else server.clone(commonOptions = server.commonOptions.copy(oauth = oauth))
                }
            )
        }
    }

    private fun computeExpiry(expiresIn: Long?): Long =
        if (expiresIn != null && expiresIn > 0) {
            System.currentTimeMillis() + expiresIn * 1000
        } else {
            0L
        }

    private fun looksUnauthorized(error: Throwable): Boolean {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        return message.contains("401") ||
            message.contains("unauthorized") ||
            message.contains("invalid_token") ||
            message.contains("invalid access token") ||
            message.contains("missing or invalid")
    }
}
