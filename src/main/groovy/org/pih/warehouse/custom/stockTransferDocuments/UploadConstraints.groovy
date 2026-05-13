package org.pih.warehouse.custom.stockTransferDocuments

class UploadConstraints {

    static final long DEFAULT_MAX_BYTES = 10L * 1024L * 1024L
    static final int FILENAME_MAX_LENGTH = 255

    static final Set<String> ALLOWED_CONTENT_TYPES = [
            'application/pdf',
            'image/png',
            'image/jpeg',
            'image/gif',
            'image/webp',
            'application/msword',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'application/vnd.ms-excel',
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            'text/csv',
            'application/zip',
            'application/x-zip-compressed',
            'application/octet-stream',
    ].asImmutable() as Set

    static final Set<String> ALLOWED_EXTENSIONS = [
            'pdf',
            'png',
            'jpg',
            'jpeg',
            'gif',
            'webp',
            'doc',
            'docx',
            'xls',
            'xlsx',
            'csv',
            'zip',
    ].asImmutable() as Set

    static final String INVALID_TYPE_CODE = 'customStockTransferDocument.upload.invalidType.error'
    static final String INVALID_TYPE_DEFAULT = 'Unsupported file type. Allowed: PDF, image, Word, Excel, CSV, ZIP.'
    static final String TOO_LARGE_CODE = 'customStockTransferDocument.upload.tooLarge.error'
    static final String TOO_LARGE_DEFAULT = 'File is too large.'
    static final String INVALID_FILENAME_CODE = 'customStockTransferDocument.upload.invalidFilename.error'
    static final String INVALID_FILENAME_DEFAULT = 'File name is missing or invalid.'

    static String getExtension(String filename) {
        if (!filename) {
            return ''
        }
        int dot = filename.lastIndexOf('.')
        if (dot < 0 || dot == filename.length() - 1) {
            return ''
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT)
    }

    static boolean isContentTypeAllowed(String contentType) {
        return contentType && ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))
    }

    static boolean isExtensionAllowed(String filename) {
        return ALLOWED_EXTENSIONS.contains(getExtension(filename))
    }

    static String sanitizeFilename(String originalFilename) {
        if (!originalFilename) {
            return null
        }
        String basename = new File(originalFilename.replace('\\', '/')).name
        StringBuilder builder = new StringBuilder(basename.length())
        basename.each { String ch ->
            int code = ch.charAt(0) as int
            if (Character.isISOControl(code)) {
                return
            }
            if (ch in ['<', '>', ':', '"', '|', '?', '*']) {
                builder.append('_')
            } else {
                builder.append(ch)
            }
        }
        String stripped = builder.toString().trim()
        if (!stripped || stripped == '.' || stripped == '..') {
            return null
        }
        return stripped.length() > FILENAME_MAX_LENGTH
                ? stripped.substring(0, FILENAME_MAX_LENGTH)
                : stripped
    }
}
