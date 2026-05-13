<g:if test="${orderInstance?.documents}">
    <table class="table table-bordered table-condensed">
        <thead>
            <tr>
                <th><warehouse:message code="document.name.label" default="Name"/></th>
                <th><warehouse:message code="document.contentType.label" default="Type"/></th>
                <th><warehouse:message code="default.dateCreated.label" default="Date"/></th>
            </tr>
        </thead>
        <tbody>
            <g:each var="document" in="${orderInstance.documents.sort { it.dateCreated }}">
                <tr>
                    <td>
                        <a href="${createLink(controller: 'document', action: 'download', id: document.id)}"
                           target="_blank">${document.name}</a>
                    </td>
                    <td>${document.contentType}</td>
                    <td><format:date obj="${document.dateCreated}"/></td>
                </tr>
            </g:each>
        </tbody>
    </table>
</g:if>
<g:else>
    <div class="empty-section center">
        <warehouse:message code="customStockTransferDocument.noDocuments.label" default="No supporting documents"/>
    </div>
</g:else>
