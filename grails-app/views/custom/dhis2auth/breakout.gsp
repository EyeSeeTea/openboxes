<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title><warehouse:message code="dhis2auth.breakout.title" default="Signing in…"/></title>
    <g:set var="interactiveUrl" value="${createLink(controller: 'dhis2OAuth', action: 'initiate')}"/>
    <script type="text/javascript">
        // Silent SSO needs interactive login/consent. Navigate the TOP-LEVEL window so DHIS2's
        // own login page (which forbids being framed) renders outside the iframe. Assigning
        // window.top.location is permitted cross-origin and works whether or not OB is framed.
        (function () {
            var target = '${interactiveUrl}';
            try { window.top.location.replace(target); }
            catch (e) { window.location.replace(target); }
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
