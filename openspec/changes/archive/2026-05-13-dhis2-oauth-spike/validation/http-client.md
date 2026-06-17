## Verdict: Apache HttpComponents (httpclient) is on the classpath

No `groovyx.net.http.HTTPBuilder` found in the codebase or build.gradle.

### What IS on the classpath

From build.gradle:
```
implementation 'org.apache.httpcomponents:fluent-hc'
implementation 'org.apache.httpcomponents:httpclient'
```

`org.apache.httpcomponents:httpclient` and its `fluent-hc` facade are
explicitly declared runtime dependencies. These are the standard Apache
HttpComponents 4.x library — well-supported on JDK 8, widely used.

### Recommendation for dhis2-oauth-core

Use `org.apache.http.client.fluent.Request` (from fluent-hc) for the DHIS2
token exchange and `/api/me` call. Example:

```groovy
import org.apache.http.client.fluent.Request
import org.apache.http.message.BasicNameValuePair

String response = Request.Post("${dhis2BaseUrl}/uaa/oauth/token")
    .bodyForm(
        new BasicNameValuePair("grant_type", "authorization_code"),
        new BasicNameValuePair("code", authCode),
        new BasicNameValuePair("redirect_uri", redirectUri),
        new BasicNameValuePair("client_id", clientId),
        new BasicNameValuePair("client_secret", clientSecret)
    )
    .execute()
    .returnContent()
    .asString()
```

This is synchronous, JDK-8-compatible, and avoids adding new dependencies.
No JDK-native `HttpURLConnection` workaround needed.
