## Verdict: (a) — AuthController blocks login for active=false users outright

### Evidence from AuthController.handleLogin (lines 87–91)

```groovy
// Check if user is active -- redirect back to login page
if (!userInstance?.active) {
    flash.message = "${warehouse.message(code: 'auth.accountRequestUnderReview.message')}"
    redirect(controller: 'auth', action: 'login')
    return
}
```

The check happens BEFORE password verification. An inactive user never gets
a session established — the controller short-circuits and returns the user to
the login page with "Your account request is under review."

### Evidence from SecurityInterceptor (lines 98–108)

```groovy
// When a user has been authenticated, we want to check if they have an active account
if (session?.user && !session?.user?.active) {
    session.user = null

    if (RequestUtil.isAjax(request)) {
        redirect(controller: "errors", action: "handleUnauthorized")
        return false
    }

    redirect(controller: 'auth', action: 'login')
    return false
}
```

A belt-and-suspenders check: even if `session.user` is set for an inactive
user (which cannot happen via the normal login path), SecurityInterceptor
clears it and redirects to login on every request. This is defence-in-depth.

### Implication for dhis2-oauth-core D3

The OAuth callback for a first-time DHIS2 user (active=false) MUST follow
outcome branch (a) from the design:

> Our OAuth callback must NOT call the standard session-setup code.
> Instead, it sets a session marker ("pending DHIS2 user") that
> SecurityInterceptor reads to route everything to the pending-access page.
> Logout clears the marker.

### Session-setup code (from AuthController.handleLogin lines 103–117)

The inline session-setup block (no reusable method exists today):

```groovy
session.user = userInstance
session.userName = userInstance?.username

// PIMS-782 Force the user to select a warehouse each time
if (userInstance?.warehouse && userInstance?.rememberLastLocation) {
    session.warehouse = userInstance.warehouse
}

if (session?.targetUri) {
    redirect(uri: session.targetUri)
    session.targetUri = null
    return
}

redirect(controller: 'dashboard', action: 'index')
```

**Recommendation**: Extract a `Dhis2SessionService.establishSession(User, HttpSession)`
that encapsulates these five statements and handles the `targetUri` redirect.
Both `AuthController.handleLogin` and the new OAuth `callback` action call it.
This keeps the touch to `AuthController` surgical (replace inline block with
a service call), and the OAuth callback never duplicates session-management
logic.
