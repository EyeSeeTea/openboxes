package org.pih.warehouse.custom.dhis2auth

import grails.converters.JSON
import grails.core.GrailsApplication
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpUriRequest
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils

import java.security.MessageDigest
import java.security.SecureRandom

class Dhis2OAuthService {

    static transactional = false

    private static final String CHARSET = 'UTF-8'
    private static final String US_ASCII = 'US-ASCII'
    private static final String HEADER_AUTHORIZATION = 'Authorization'
    private static final String HEADER_ACCEPT = 'Accept'
    private static final String CONTENT_TYPE_JSON = 'application/json'
    private static final String GRANT_TYPE_AUTH_CODE = 'authorization_code'
    private static final String PROFILE_V42 = 'v42'
    private static final String DEFAULT_PROFILE = 'v40'
    private static final String CLIENT_AUTH_POST = 'post'
    private static final String DEFAULT_CLIENT_AUTH = 'basic'
    private static final String DEFAULT_SCOPES_V40 = 'ALL'
    private static final String DEFAULT_SCOPES_V42 = 'openid username'
    private static final String CODE_CHALLENGE_METHOD = 'S256'
    private static final int PKCE_VERIFIER_BYTES = 32
    private static final int HTTP_OK = 200

    // Reason: SecureRandom is thread-safe; shared instance avoids per-request entropy cost
    private static final SecureRandom SECURE_RANDOM = new SecureRandom()
    private static final Base64.Encoder URL_ENCODER = Base64.urlEncoder.withoutPadding()

    GrailsApplication grailsApplication
    private volatile CloseableHttpClient httpClient
    private volatile PoolingHttpClientConnectionManager connectionManager

    @PostConstruct
    void init() {
        connectionManager = new PoolingHttpClientConnectionManager()
        httpClient = HttpClients.custom().setConnectionManager(connectionManager).build()
    }

    @PreDestroy
    void destroy() {
        httpClient?.close()
        connectionManager?.close()
    }

    AuthorizeRequest prepareAuthorize(String state) {
        String codeVerifier = isV42() ? generateCodeVerifier() : null
        String challenge = codeVerifier ? codeChallengeFor(codeVerifier) : null
        new AuthorizeRequest(url: buildAuthorizeUrl(state, challenge), codeVerifier: codeVerifier)
    }

    String buildAuthorizeUrl(String state, String codeChallenge = null) {
        boolean v42 = isV42()
        String defaultScopes = v42 ? DEFAULT_SCOPES_V42 : DEFAULT_SCOPES_V40
        String scopes = config.scopes ?: defaultScopes
        String url = "${config.authorizeUrl}?response_type=code" +
            "&client_id=${encode(config.clientId)}" +
            "&redirect_uri=${encode(config.redirectUri)}" +
            "&scope=${encode(scopes)}" +
            "&state=${encode(state)}"
        if (v42 && codeChallenge) {
            url += "&code_challenge=${encode(codeChallenge)}&code_challenge_method=${CODE_CHALLENGE_METHOD}"
        }
        url
    }

    AccessToken exchangeCode(String code, String codeVerifier = null) {
        boolean v42 = isV42()
        String clientAuth = config.clientAuth ?: DEFAULT_CLIENT_AUTH
        boolean clientSecretPost = v42 && clientAuth == CLIENT_AUTH_POST

        List<NameValuePair> form = [
            new BasicNameValuePair('grant_type', GRANT_TYPE_AUTH_CODE),
            new BasicNameValuePair('code', code),
            new BasicNameValuePair('redirect_uri', config.redirectUri as String),
        ]
        if (v42 && codeVerifier) {
            form << new BasicNameValuePair('code_verifier', codeVerifier)
        }
        if (clientSecretPost) {
            // Reason: clientSecret rides in the form body here — keep org.apache.http.wire above TRACE in prod
            form << new BasicNameValuePair('client_id', config.clientId as String)
            form << new BasicNameValuePair('client_secret', config.clientSecret as String)
        }

        HttpPost post = new HttpPost(config.tokenUrl as String)
        if (!clientSecretPost) {
            post.addHeader(HEADER_AUTHORIZATION, basicAuth(config.clientId as String, config.clientSecret as String))
        }
        post.addHeader(HEADER_ACCEPT, CONTENT_TYPE_JSON)
        post.setEntity(new UrlEncodedFormEntity(form, CHARSET))

        Map json = executeJsonRequest(post, 'Token exchange failed')
        new AccessToken(accessToken: json.access_token as String, idToken: json.id_token as String)
    }

    Dhis2User resolveIdentity(AccessToken token) {
        isV42() ? identityFromIdToken(token.idToken) : fetchMe(token.accessToken)
    }

    private Dhis2User identityFromIdToken(String idToken) {
        String username = subjectClaim(idToken)
        if (!username) {
            throw new Dhis2OAuthException('User info fetch failed: id_token missing sub claim')
        }
        new Dhis2User(uid: null, username: username, displayName: null, email: null)
    }

    // Reason: the id_token arrives over the TLS back channel straight from the token
    // endpoint, so the sub claim is read without signature verification.
    private static String subjectClaim(String idToken) {
        String[] parts = idToken?.split('\\.')
        if (!parts || parts.length < 2) {
            return null
        }
        try {
            String payload = new String(Base64.urlDecoder.decode(padBase64(parts[1])), CHARSET)
            (JSON.parse(payload) as Map).sub as String
        } catch (Exception e) {
            throw new Dhis2OAuthException("User info fetch failed: malformed id_token — ${e.message}", e)
        }
    }

    private static String padBase64(String value) {
        int remainder = value.length() % 4
        remainder == 0 ? value : value + ('=' * (4 - remainder))
    }

    String generateCodeVerifier() {
        byte[] bytes = new byte[PKCE_VERIFIER_BYTES]
        SECURE_RANDOM.nextBytes(bytes)
        base64UrlNoPad(bytes)
    }

    String codeChallengeFor(String verifier) {
        byte[] digest = MessageDigest.getInstance('SHA-256').digest(verifier.getBytes(US_ASCII))
        base64UrlNoPad(digest)
    }

    Dhis2User fetchMe(String accessToken) {
        HttpGet get = new HttpGet(config.userUrl as String)
        get.addHeader(HEADER_AUTHORIZATION, "Bearer ${accessToken}")
        get.addHeader(HEADER_ACCEPT, CONTENT_TYPE_JSON)

        Map json = executeJsonRequest(get, 'User info fetch failed')
        new Dhis2User(
            uid: json.id as String,
            username: json.username as String,
            displayName: json.displayName as String,
            email: json.email as String,
        )
    }

    private Map executeJsonRequest(HttpUriRequest request, String errorContext) {
        CloseableHttpResponse response = httpClient.execute(request)
        try {
            int status = response.statusLine.statusCode
            String body = response.entity ? EntityUtils.toString(response.entity) : ''
            if (status != HTTP_OK) {
                throw new Dhis2OAuthException("${errorContext}: HTTP ${status} — ${body}")
            }
            JSON.parse(body) as Map
        } finally {
            response.close()
        }
    }

    private ConfigObject getConfig() {
        grailsApplication.config.openboxes.custom.dhis2.oauth
    }

    private boolean isV42() {
        (config.profile ?: DEFAULT_PROFILE) == PROFILE_V42
    }

    private static String base64UrlNoPad(byte[] bytes) {
        URL_ENCODER.encodeToString(bytes)
    }

    private static String basicAuth(String clientId, String clientSecret) {
        "Basic ${"${clientId}:${clientSecret}".bytes.encodeBase64()}"
    }

    private static String encode(Object value) {
        URLEncoder.encode(value as String, CHARSET)
    }

    static class AuthorizeRequest {
        String url
        String codeVerifier
    }

    static class AccessToken {
        String accessToken
        String idToken
    }

    static class Dhis2User {
        String uid
        String username
        String displayName
        String email
    }

    static class Dhis2OAuthException extends RuntimeException {
        Dhis2OAuthException(String message) { super(message) }
        Dhis2OAuthException(String message, Throwable cause) { super(message, cause) }
    }
}
