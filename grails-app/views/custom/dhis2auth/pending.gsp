<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="layout" content="custom"/>
    <title><warehouse:message code="dhis2auth.pending.title" default="Access Pending"/></title>
</head>
<body>
    <div class="body">
        <div id="loginContainer">
            <div id="loginForm">
                <div id="loginBox" class="box">
                    <h2><warehouse:message code="dhis2auth.pending.heading" default="Access Pending"/></h2>
                    <p>
                        <warehouse:message code="dhis2auth.pending.message"
                            default="Your DHIS2 account has been registered but is awaiting access approval from an administrator. You will be able to log in once your account has been activated and roles have been assigned."/>
                    </p>
                    <p>
                        <g:link controller="auth" action="logout">
                            <warehouse:message code="auth.logout.label" default="Logout"/>
                        </g:link>
                    </p>
                </div>
            </div>
        </div>
    </div>
</body>
</html>
