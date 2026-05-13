package org.pih.warehouse.custom.dhis2auth

import grails.core.GrailsApplication
import grails.gorm.transactions.NotTransactional
import groovy.json.JsonSlurper
import org.apache.http.HttpEntity
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils

class Dhis2OAuthClient {

    GrailsApplication grailsApplication

    @NotTransactional
    String buildAuthorizeUrl(String state) {
        String clientId = config.clientId
        String redirectUri = config.redirectUri
        String scopes = config.scopes ?: 'ALL'

        "${config.authorizeUrl}?response_type=code&client_id=${encode(clientId)}&redirect_uri=${encode(redirectUri)}&scope=${encode(scopes)}&state=${encode(state)}"
    }

    @NotTransactional
    AccessToken exchangeCode(String code) {
        CloseableHttpClient client = HttpClients.createDefault()
        try {
            HttpPost post = new HttpPost(config.tokenUrl as String)
            post.addHeader('Authorization', basicAuth(config.clientId as String, config.clientSecret as String))
            post.addHeader('Accept', 'application/json')

            List<NameValuePair> form = [
                new BasicNameValuePair('grant_type', 'authorization_code'),
                new BasicNameValuePair('code', code),
                new BasicNameValuePair('redirect_uri', config.redirectUri as String),
            ]
            post.setEntity(new UrlEncodedFormEntity(form, 'UTF-8'))

            CloseableHttpResponse response = client.execute(post)
            try {
                int status = response.statusLine.statusCode
                HttpEntity entity = response.entity
                String body = entity ? EntityUtils.toString(entity) : ''
                if (status != 200) {
                    throw new Dhis2OAuthException("Token exchange failed: HTTP $status — $body")
                }
                Map json = new JsonSlurper().parseText(body) as Map
                return new AccessToken(accessToken: json.access_token as String)
            } finally {
                response.close()
            }
        } finally {
            client.close()
        }
    }

    @NotTransactional
    Dhis2User fetchMe(String accessToken) {
        CloseableHttpClient client = HttpClients.createDefault()
        try {
            HttpGet get = new HttpGet(config.userUrl as String)
            get.addHeader('Authorization', "Bearer ${accessToken}")
            get.addHeader('Accept', 'application/json')

            CloseableHttpResponse response = client.execute(get)
            try {
                int status = response.statusLine.statusCode
                HttpEntity entity = response.entity
                String body = entity ? EntityUtils.toString(entity) : ''
                if (status != 200) {
                    throw new Dhis2OAuthException("User info fetch failed: HTTP $status — $body")
                }
                Map json = new JsonSlurper().parseText(body) as Map
                return new Dhis2User(
                    uid: json.id as String,
                    username: json.username as String,
                    displayName: json.displayName as String,
                    email: json.email as String,
                )
            } finally {
                response.close()
            }
        } finally {
            client.close()
        }
    }

    private ConfigObject getConfig() {
        grailsApplication.config.openboxes.dhis2.oauth
    }

    private static String basicAuth(String clientId, String clientSecret) {
        "Basic ${"${clientId}:${clientSecret}".bytes.encodeBase64()}"
    }

    private static String encode(Object value) {
        URLEncoder.encode(value as String, 'UTF-8')
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
