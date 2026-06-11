package org.pih.warehouse.custom.dhis2auth

import grails.testing.services.ServiceUnitTest
import org.apache.http.HttpEntity
import org.apache.http.StatusLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.util.EntityUtils
import spock.lang.Specification
import spock.lang.Unroll

class Dhis2OAuthServiceSpec extends Specification implements ServiceUnitTest<Dhis2OAuthService> {

    // RFC 7636 appendix-B test vector.
    private static final String RFC7636_VERIFIER = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'
    private static final String RFC7636_CHALLENGE = 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM'

    // Captured by stubTokenResponse() when exchangeCode posts to the token endpoint.
    private HttpPost capturedRequest

    private void configureClient(String authorizeUrl) {
        grailsApplication.config.openboxes.custom.dhis2.oauth.authorizeUrl = authorizeUrl
        grailsApplication.config.openboxes.custom.dhis2.oauth.clientId = 'my client'
        grailsApplication.config.openboxes.custom.dhis2.oauth.redirectUri = 'https://ob.example.com/oauth/dhis2/callback'
    }

    @Unroll
    void "v40 buildAuthorizeUrl builds an exact URL when scopes=#scopes and state=#state"() {
        given:
        configureClient('https://dhis2.example.com/uaa/oauth/authorize')
        grailsApplication.config.openboxes.custom.dhis2.oauth.scopes = scopes

        when:
        String url = service.buildAuthorizeUrl(state)

        then:
        url == expected

        where:
        scopes      | state            | expected
        'ALL'       | 'test-state-123' | 'https://dhis2.example.com/uaa/oauth/authorize?response_type=code&client_id=my+client&redirect_uri=https%3A%2F%2Fob.example.com%2Foauth%2Fdhis2%2Fcallback&scope=ALL&state=test-state-123'
        'read user' | 'state with sp'  | 'https://dhis2.example.com/uaa/oauth/authorize?response_type=code&client_id=my+client&redirect_uri=https%3A%2F%2Fob.example.com%2Foauth%2Fdhis2%2Fcallback&scope=read+user&state=state+with+sp'
        null        | 'abc'            | 'https://dhis2.example.com/uaa/oauth/authorize?response_type=code&client_id=my+client&redirect_uri=https%3A%2F%2Fob.example.com%2Foauth%2Fdhis2%2Fcallback&scope=ALL&state=abc'
    }

    void "v40 buildAuthorizeUrl carries no PKCE params even if a challenge is passed"() {
        given:
        configureClient('https://dhis2.example.com/uaa/oauth/authorize')

        when:
        String url = service.buildAuthorizeUrl('st', RFC7636_CHALLENGE)

        then:
        url == 'https://dhis2.example.com/uaa/oauth/authorize?response_type=code&client_id=my+client&redirect_uri=https%3A%2F%2Fob.example.com%2Foauth%2Fdhis2%2Fcallback&scope=ALL&state=st'
    }

    void "v42 buildAuthorizeUrl defaults scopes to 'openid username' and appends the PKCE challenge"() {
        given:
        configureClient('https://dhis2.example.com/oauth2/authorize')
        grailsApplication.config.openboxes.custom.dhis2.oauth.profile = 'v42'

        when:
        String url = service.buildAuthorizeUrl('st-42', RFC7636_CHALLENGE)

        then:
        url == 'https://dhis2.example.com/oauth2/authorize?response_type=code&client_id=my+client&redirect_uri=https%3A%2F%2Fob.example.com%2Foauth%2Fdhis2%2Fcallback' +
            "&scope=openid+username&state=st-42&code_challenge=${RFC7636_CHALLENGE}&code_challenge_method=S256"
    }

    void "v42 buildAuthorizeUrl honours configured scopes over the default"() {
        given:
        configureClient('https://dhis2.example.com/oauth2/authorize')
        grailsApplication.config.openboxes.custom.dhis2.oauth.profile = 'v42'
        grailsApplication.config.openboxes.custom.dhis2.oauth.scopes = 'openid username email'

        when:
        String url = service.buildAuthorizeUrl('s', RFC7636_CHALLENGE)

        then:
        url == 'https://dhis2.example.com/oauth2/authorize?response_type=code&client_id=my+client&redirect_uri=https%3A%2F%2Fob.example.com%2Foauth%2Fdhis2%2Fcallback' +
            "&scope=openid+username+email&state=s&code_challenge=${RFC7636_CHALLENGE}&code_challenge_method=S256"
    }

    void "v42 buildAuthorizeUrl omits the PKCE challenge when none is supplied"() {
        given:
        configureClient('https://dhis2.example.com/oauth2/authorize')
        grailsApplication.config.openboxes.custom.dhis2.oauth.profile = 'v42'

        when:
        String url = service.buildAuthorizeUrl('s')

        then:
        !url.contains('code_challenge')
    }

    void "v42 buildAuthorizeUrl appends prompt=none only when silent is requested"() {
        given:
        configureClient('https://dhis2.example.com/oauth2/authorize')
        grailsApplication.config.openboxes.custom.dhis2.oauth.profile = 'v42'

        expect:
        service.buildAuthorizeUrl('s', RFC7636_CHALLENGE, true).endsWith('&prompt=none')
        !service.buildAuthorizeUrl('s', RFC7636_CHALLENGE, false).contains('prompt')
    }

    void "v40 buildAuthorizeUrl never appends prompt=none even when silent is requested"() {
        given:
        configureClient('https://dhis2.example.com/uaa/oauth/authorize')
        grailsApplication.config.openboxes.custom.dhis2.oauth.profile = 'v40'

        expect:
        !service.buildAuthorizeUrl('s', RFC7636_CHALLENGE, true).contains('prompt')
    }

    @Unroll
    void "isSilentAuthSupported is #expected for profile=#profile"() {
        given:
        grailsApplication.config.openboxes.custom.dhis2.oauth.profile = profile

        expect:
        service.isSilentAuthSupported() == expected

        where:
        profile || expected
        'v42'    || true
        'v40'    || false
        null     || false
    }

    void "codeChallengeFor matches the RFC 7636 appendix-B test vector"() {
        expect:
        service.codeChallengeFor(RFC7636_VERIFIER) == RFC7636_CHALLENGE
    }

    void "generateCodeVerifier returns a 43-char base64url-no-pad string"() {
        when:
        String verifier = service.generateCodeVerifier()

        then:
        verifier.length() == 43
        verifier ==~ /^[A-Za-z0-9_-]+$/
    }

    void "prepareAuthorize for v42 returns a verifier and a challenge-bearing url"() {
        given:
        configureClient('https://dhis2.example.com/oauth2/authorize')
        grailsApplication.config.openboxes.custom.dhis2.oauth.profile = 'v42'

        when:
        Dhis2OAuthService.AuthorizeRequest req = service.prepareAuthorize('st-42')

        then:
        req.codeVerifier ==~ /^[A-Za-z0-9_-]{43}$/
        req.url.contains('&code_challenge=')
        req.url.contains('&code_challenge_method=S256')
    }

    void "prepareAuthorize for v40 returns no verifier and a url without PKCE"() {
        given:
        configureClient('https://dhis2.example.com/uaa/oauth/authorize')
        grailsApplication.config.openboxes.custom.dhis2.oauth.profile = 'v40'
        grailsApplication.config.openboxes.custom.dhis2.oauth.scopes = 'ALL'

        when:
        Dhis2OAuthService.AuthorizeRequest req = service.prepareAuthorize('st')

        then:
        req.codeVerifier == null
        !req.url.contains('code_challenge')
    }

    void "v40 exchangeCode sends a Basic header and no code_verifier"() {
        given:
        configureTokenEndpoint('v40', 'basic')
        stubTokenResponse()

        when:
        service.exchangeCode('the-code', 'ignored-verifier')

        then:
        capturedRequest.getFirstHeader('Authorization').value.startsWith('Basic ')
        formFields(capturedRequest) == [grant_type: 'authorization_code', code: 'the-code', redirect_uri: 'https://ob.example.com/oauth/dhis2/callback']
    }

    void "v42 exchangeCode with clientAuth=basic keeps the Basic header and adds code_verifier"() {
        given:
        configureTokenEndpoint('v42', 'basic')
        stubTokenResponse()

        when:
        service.exchangeCode('the-code', 'the-verifier')

        then:
        capturedRequest.getFirstHeader('Authorization').value.startsWith('Basic ')
        Map fields = formFields(capturedRequest)
        fields.code_verifier == 'the-verifier'
        fields.client_id == null
        fields.client_secret == null
    }

    void "v42 exchangeCode with clientAuth=post drops the Basic header and posts client credentials"() {
        given:
        configureTokenEndpoint('v42', 'post')
        stubTokenResponse()

        when:
        service.exchangeCode('the-code', 'the-verifier')

        then:
        capturedRequest.getFirstHeader('Authorization') == null
        Map fields = formFields(capturedRequest)
        fields.grant_type == 'authorization_code'
        fields.code == 'the-code'
        fields.code_verifier == 'the-verifier'
        fields.client_id == 'my client'
        fields.client_secret == 'the-secret'
    }

    void "exchangeCode captures the id_token from the token response"() {
        given:
        configureTokenEndpoint('v42', 'basic')
        stubTokenResponse('{"access_token":"tok","id_token":"id-tok"}')

        when:
        Dhis2OAuthService.AccessToken token = service.exchangeCode('the-code', 'the-verifier')

        then:
        token.accessToken == 'tok'
        token.idToken == 'id-tok'
    }

    void "v42 resolveIdentity reads the username from the id_token sub claim"() {
        given:
        grailsApplication.config.openboxes.custom.dhis2.oauth.profile = 'v42'

        when:
        Dhis2OAuthService.Dhis2User user = service.resolveIdentity(
            new Dhis2OAuthService.AccessToken(accessToken: 'tok', idToken: idTokenWithClaims('{"sub":"alice","aud":"ob"}')))

        then:
        user.uid == null
        user.username == 'alice'
        user.displayName == null
        user.email == null
    }

    void "v42 resolveIdentity rejects an id_token with no sub claim"() {
        given:
        grailsApplication.config.openboxes.custom.dhis2.oauth.profile = 'v42'

        when:
        service.resolveIdentity(
            new Dhis2OAuthService.AccessToken(accessToken: 'tok', idToken: idTokenWithClaims('{"aud":"ob"}')))

        then:
        thrown(Dhis2OAuthService.Dhis2OAuthException)
    }

    @Unroll
    void "v42 resolveIdentity raises Dhis2OAuthException for malformed id_token '#idToken'"() {
        given:
        grailsApplication.config.openboxes.custom.dhis2.oauth.profile = 'v42'

        when:
        service.resolveIdentity(new Dhis2OAuthService.AccessToken(accessToken: 'tok', idToken: idToken))

        then:
        thrown(Dhis2OAuthService.Dhis2OAuthException)

        where:
        idToken << ['not-a-jwt', 'header.@@@not-base64@@@.sig', 'header..sig', null]
    }

    private void configureTokenEndpoint(String profile, String clientAuth) {
        grailsApplication.config.openboxes.custom.dhis2.oauth.tokenUrl = 'https://dhis2.example.com/oauth2/token'
        grailsApplication.config.openboxes.custom.dhis2.oauth.clientId = 'my client'
        grailsApplication.config.openboxes.custom.dhis2.oauth.clientSecret = 'the-secret'
        grailsApplication.config.openboxes.custom.dhis2.oauth.redirectUri = 'https://ob.example.com/oauth/dhis2/callback'
        grailsApplication.config.openboxes.custom.dhis2.oauth.profile = profile
        grailsApplication.config.openboxes.custom.dhis2.oauth.clientAuth = clientAuth
    }

    // Replaces the service's HttpClient with a stub that captures the request into capturedRequest
    // and returns a 200 token JSON, so exchangeCode runs its real request-building path without
    // touching the network.
    private void stubTokenResponse(String body = '{"access_token":"tok"}') {
        byte[] bytes = body.getBytes('UTF-8')
        StatusLine statusLine = Stub(StatusLine) { getStatusCode() >> 200 }
        HttpEntity entity = Stub(HttpEntity) {
            getContent() >> { new ByteArrayInputStream(bytes) }
            getContentLength() >> (bytes.length as long)
        }
        CloseableHttpResponse httpResponse = Stub(CloseableHttpResponse) {
            getStatusLine() >> statusLine
            getEntity() >> entity
        }
        CloseableHttpClient client = Mock(CloseableHttpClient) {
            execute(_ as HttpUriRequest) >> { HttpUriRequest req -> capturedRequest = req as HttpPost; httpResponse }
        }
        service.httpClient = client
    }

    private static String idTokenWithClaims(String json) {
        String payload = Base64.urlEncoder.withoutPadding().encodeToString(json.getBytes('UTF-8'))
        "header.${payload}.signature"
    }

    private static Map formFields(HttpPost post) {
        String body = EntityUtils.toString(post.entity)
        body.split('&').collectEntries { pair ->
            List<String> kv = pair.split('=', 2) as List
            String value = kv.size() > 1 ? URLDecoder.decode(kv[1], 'UTF-8') : ''
            [(URLDecoder.decode(kv[0], 'UTF-8')): value]
        }
    }
}
