databaseChangeLog = {
    changeSet(id: '2026-05-22-01-create-custom-notification', author: 'eyeseetea') {
        createTable(tableName: 'custom_notification') {
            column(name: 'id', type: 'varchar(255)') {
                constraints(nullable: false, primaryKey: true, primaryKeyName: 'custom_notificationPK')
            }
            column(name: 'version', type: 'bigint') {
                constraints(nullable: false)
            }
            column(name: 'user_id', type: 'varchar(255)') {
                constraints(nullable: false)
            }
            column(name: 'notification_type', type: 'varchar(64)') {
                constraints(nullable: false)
            }
            column(name: 'title', type: 'varchar(255)') {
                constraints(nullable: false)
            }
            column(name: 'body', type: 'text') {
                constraints(nullable: true)
            }
            column(name: 'is_read', type: 'boolean', defaultValueBoolean: false) {
                constraints(nullable: false)
            }
            column(name: 'read_at', type: 'datetime') {
                constraints(nullable: true)
            }
            column(name: 'date_created', type: 'datetime') {
                constraints(nullable: false)
            }
            column(name: 'last_updated', type: 'datetime') {
                constraints(nullable: false)
            }
        }
        addForeignKeyConstraint(
            baseTableName: 'custom_notification',
            baseColumnNames: 'user_id',
            referencedTableName: 'user',
            referencedColumnNames: 'id',
            constraintName: 'fk_custom_notification_user'
        )
        createIndex(indexName: 'idx_custom_notification_user_unread', tableName: 'custom_notification') {
            column(name: 'user_id')
            column(name: 'is_read')
            column(name: 'date_created')
        }
        createIndex(indexName: 'idx_custom_notification_user_created', tableName: 'custom_notification') {
            column(name: 'user_id')
            column(name: 'date_created')
        }
        createIndex(indexName: 'idx_custom_notification_user_updated', tableName: 'custom_notification') {
            column(name: 'user_id')
            column(name: 'last_updated')
        }
        rollback {
            dropTable(tableName: 'custom_notification')
        }
    }
}
