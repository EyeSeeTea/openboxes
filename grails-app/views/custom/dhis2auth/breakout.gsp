<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title><warehouse:message code="dhis2auth.breakout.title" default="Signing in…"/></title>
    <g:set var="interactiveUrl" value="${createLink(controller: 'dhis2OAuth', action: 'initiate')}"/>
    <script type="text/javascript">
        // Silent SSO needs interactive login/consent. When framed, navigate the TOP-LEVEL
        // window so DHIS2's own login page (which forbids being framed) renders outside the
        // iframe; a top-level visit just recovers in place.
        (function () {
            var target = '${interactiveUrl.encodeAsJavaScript()}';
            if (window.top !== window.self) {
                try { window.top.location.replace(target); }
                catch (e) { window.location.replace(target); }
            } else {
                window.location.replace(target);
            }
        })();
    </script>
</head>
<body>
    <p><warehouse:message code="dhis2auth.breakout.message" default="Redirecting to sign in…"/></p>
    <noscript>
        <a href="${createLink(controller: 'dhis2OAuth', action: 'initiate')}">
            <warehouse:message code="dhis2auth.breakout.link" default="Continue to sign in"/>
        </a>
    </noscript>
</body>
</html>
