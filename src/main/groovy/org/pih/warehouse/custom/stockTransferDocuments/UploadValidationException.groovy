package org.pih.warehouse.custom.stockTransferDocuments

class UploadValidationException extends RuntimeException {

    final String messageCode
    final Object[] messageArgs

    UploadValidationException(String messageCode, String defaultMessage) {
        this(messageCode, defaultMessage, null)
    }

    UploadValidationException(String messageCode, String defaultMessage, Object[] messageArgs) {
        super(defaultMessage)
        this.messageCode = messageCode
        this.messageArgs = messageArgs
    }
}
