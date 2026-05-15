package org.pih.warehouse.custom.stockAlertRestore

import grails.gorm.transactions.Transactional
import grails.util.Holders
import groovy.sql.Sql
import org.pih.warehouse.inventory.RefreshProductAvailabilityEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.Ordered

import javax.sql.DataSource

/**
 * Restores Phase 2 of OBPIH-3280 — copies product_availability rows into inventory_snapshot
 * with date = current_date + 1, so the stock-alert view chain (which still reads from
 * inventory_snapshot) has data to report on.
 *
 * Upstream PR #2018 deleted RefreshInventorySnapshotAfterTransactionJob with the explicit
 * deferred follow-up "need to ETL product availability records to inventory snapshot" — never
 * delivered. Issue openboxes/openboxes#4742 tracks the symptom.
 */
@Transactional
class InventorySnapshotEtlService implements ApplicationListener<RefreshProductAvailabilityEvent>, Ordered {

    def productAvailabilityService
    DataSource dataSource

    @Override
    int getOrder() {
        return Ordered.LOWEST_PRECEDENCE
    }

    void onApplicationEvent(RefreshProductAvailabilityEvent event) {
        if (!isEnabled()) {
            return
        }
        if (event?.disableRefresh) {
            return
        }
        if (!event?.locationId) {
            return
        }

        log.info "InventorySnapshotEtlService received event locationId=${event.locationId} productIds=${event.productIds?.size() ?: 0}"

        // The upstream RefreshProductAvailabilityEventService may have triggered an async
        // refresh job. Force a synchronous refresh here so the data we read is current.
        productAvailabilityService.refreshProductsAvailability(
                event.locationId, event.productIds, event.forceRefresh ?: Boolean.FALSE)

        copySnapshotFromProductAvailability(event.locationId, event.productIds)
    }

    /**
     * Enabled only when openboxes.jobs.refreshInventorySnapshotAfterTransactionJob.enabled
     * is explicitly true. Mirrors the truthy-check the deleted
     * RefreshInventorySnapshotAfterTransactionJob used (`if (enabled) { ... }`).
     *
     * Upstream application.yml still ships this key at true, so default behavior is on.
     * Per-client deploys (e.g. docker/openboxes.yml) should set it explicitly so the
     * feature survives an upstream housekeeping pass that drops the orphan default.
     */
    private boolean isEnabled() {
        return Holders.config.openboxes.jobs.refreshInventorySnapshotAfterTransactionJob.enabled == true
    }

    private void copySnapshotFromProductAvailability(String locationId, List productIds) {
        Date tomorrow = new Date() + 1
        tomorrow.clearTime()
        String tomorrowString = tomorrow.format("yyyy-MM-dd HH:mm:ss")

        boolean filterByProducts = productIds && !productIds.isEmpty()
        String productFilter = filterByProducts
                ? "AND pa.product_id IN (${productIds.collect { "'${it}'" }.join(',')})"
                : ""

        String sqlText = """
            INSERT INTO inventory_snapshot
                (id, version, date, location_id, product_id, product_code,
                 inventory_item_id, lot_number, expiration_date,
                 bin_location_id, bin_location_name,
                 quantity_on_hand, date_created, last_updated)
            SELECT UUID(), 0, '${tomorrowString}',
                   pa.location_id, pa.product_id, pa.product_code,
                   pa.inventory_item_id,
                   COALESCE(pa.lot_number, 'DEFAULT'),
                   ii.expiration_date,
                   pa.bin_location_id,
                   COALESCE(pa.bin_location_name, 'DEFAULT'),
                   pa.quantity_on_hand,
                   NOW(), NOW()
            FROM product_availability pa
            LEFT JOIN inventory_item ii ON ii.id = pa.inventory_item_id
            WHERE pa.location_id = '${locationId}'
              ${productFilter}
            ON DUPLICATE KEY UPDATE
                quantity_on_hand = VALUES(quantity_on_hand),
                version = inventory_snapshot.version + 1,
                last_updated = NOW();
        """

        Sql sql = new Sql(dataSource)
        try {
            int rows = sql.executeUpdate(sqlText)
            log.info "InventorySnapshotEtlService wrote/updated ${rows} inventory_snapshot rows for location ${locationId} dated ${tomorrowString}"
        } catch (Exception e) {
            log.error "InventorySnapshotEtlService failed to copy snapshot rows for location ${locationId}: ${e.message}", e
        } finally {
            sql.close()
        }
    }
}
