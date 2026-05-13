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

class Dhis2OAuthService {

    static transactional = false

    private static final String CHARSET = 'UTF-8'
    private static final String HEADER_AUTHORIZATION = 'Authorization'
    private static final String HEADER_ACCEPT = 'Accept'
    private static final String CONTENT_TYPE_JSON = 'application/json'
    private static final String GRANT_TYPE_AUTH_CODE = 'authorization_code'
    private static final int HTTP_OK = 200

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

    String buildAuthorizeUrl(String state) {
        String scopes = config.scopes ?: 'ALL'
        "${config.authorizeUrl}?response_type=code" +
            "&client_id=${encode(config.clientId)}" +
            "&redirect_uri=${encode(config.redirectUri)}" +
            "&scope=${encode(scopes)}" +
            "&state=${encode(state)}"
    }

    AccessToken exchangeCode(String code) {
        List<NameValuePair> form = [
            new BasicNameValuePair('grant_type', GRANT_TYPE_AUTH_CODE),
            new BasicNameValuePair('code', code),
            new BasicNameValuePair('redirect_uri', config.redirectUri as String),
        ]
        HttpPost post = new HttpPost(config.tokenUrl as String)
        post.addHeader(HEADER_AUTHORIZATION, basicAuth(config.clientId as String, config.clientSecret as String))
        post.addHeader(HEADER_ACCEPT, CONTENT_TYPE_JSON)
        post.setEntity(new UrlEncodedFormEntity(form, CHARSET))

        Map json = executeJsonRequest(post, 'Token exchange failed')
        new AccessToken(accessToken: json.access_token as String)
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
        grailsApplication.config.openboxes.dhis2.oauth
    }

    private static String basicAuth(String clientId, String clientSecret) {
        "Basic ${"${clientId}:${clientSecret}".bytes.encodeBase64()}"
    }

    private static String encode(Object value) {
        URLEncoder.encode(value as String, CHARSET)
    }

    static class AccessToken {
        String accessToken
    }

    static class Dhis2User {
        String uid
        String username
        String displayName
        String email
    }

    static class Dhis2OAuthException extends RuntimeException {
        Dhis2OAuthException(String message) { super(message) }
    }
}
