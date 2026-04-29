package org.pih.warehouse.custom.stockTransferDocuments

import spock.lang.Specification
import spock.lang.Unroll

class UploadConstraintsSpec extends Specification {

    @Unroll
    def "sanitizeFilename returns #expected for #input"() {
        expect:
        UploadConstraints.sanitizeFilename(input) == expected

        where:
        input                          || expected
        null                           || null
        ''                             || null
        '   '                          || null
        '.'                            || null
        '..'                           || null
        'cert.pdf'                     || 'cert.pdf'
        '../etc/passwd'                || 'passwd'
        '..\\windows\\boot.ini'        || 'boot.ini'
        '/var/log/secret.pdf'          || 'secret.pdf'
        'file<bad>.pdf'                || 'file_bad_.pdf'
        'a"b|c?d*.pdf'                 || 'a_b_c_d_.pdf'
    }

    @Unroll
    def "isContentTypeAllowed = #expected for #contentType"() {
        expect:
        UploadConstraints.isContentTypeAllowed(contentType) == expected

        where:
        contentType                    || expected
        null                           || false
        ''                             || false
        'application/pdf'              || true
        'image/png'                    || true
        'text/csv'                     || true
        'application/zip'              || true
        'application/x-msdownload'     || false
        'application/javascript'       || false
    }

    @Unroll
    def "isExtensionAllowed = #expected for #filename"() {
        expect:
        UploadConstraints.isExtensionAllowed(filename) == expected

        where:
        filename             || expected
        'cert.pdf'           || true
        'photo.PNG'          || true
        'sheet.xlsx'         || true
        'archive.zip'        || true
        'binary.exe'         || false
        'noextension'        || false
        ''                   || false
    }

    def "DEFAULT_MAX_BYTES matches the upstream Document.fileContents GORM cap"() {
        expect:
        UploadConstraints.DEFAULT_MAX_BYTES == 10L * 1024L * 1024L
    }
}
