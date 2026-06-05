package org.pih.warehouse.custom.stockAlertRestore

import grails.gorm.transactions.Transactional
import grails.util.Holders
import groovy.sql.Sql
import org.pih.warehouse.inventory.RefreshProductAvailabilityEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.Ordered
import org.springframework.transaction.support.TransactionSynchronizationAdapter
import org.springframework.transaction.support.TransactionSynchronizationManager

import javax.sql.DataSource

/**
 * Restores Phase 2 of OBPIH-3280 — copies product_availability rows into inventory_snapshot
 * with date = current_date + 1, so the stock-alert view chain (which still reads from
 * inventory_snapshot) sees the current quantities a transaction just produced.
 *
 * Upstream PR #2018 deleted RefreshInventorySnapshotAfterTransactionJob with the explicit
 * deferred follow-up "need to ETL product availability records to inventory snapshot" —
 * never delivered. This service finishes that pivot rather than rolling it back: pa stays
 * the live source of truth; inventory_snapshot is a derived projection.
 *
 * Wired via RefreshProductAvailabilityEvent (Transaction.afterInsert / afterUpdate). Work
 * is registered inside an afterCommit synchronization so the originating DB transaction
 * has committed before pa is refreshed and copied — otherwise the recompute would race
 * the still-open transaction_entry insert and read stale state.
 *
 * Gated by openboxes.jobs.refreshInventorySnapshotAfterTransactionJob.enabled (reuses the
 * config key from the pre-2020 deleted job; already shipped at true in application.yml).
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

        String locationId = event.locationId
        List productIds = event.productIds ? new ArrayList(event.productIds) : []

        log.info "InventorySnapshotEtlService scheduled for locationId=${locationId} productIds=${productIds.size()}"

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                void afterCommit() {
                    runEtl(locationId, productIds)
                }
            })
        } else {
            runEtl(locationId, productIds)
        }
    }

    private void runEtl(String locationId, List productIds) {
        // forceRefresh = true so saveProductAvailability DELETEs stale pa rows for this
        // location/product before re-inserting — otherwise pa rows for vanished
        // inventoryItem/bin combinations would survive the upsert with their old QOH.
        productAvailabilityService.refreshProductsAvailability(locationId, productIds, Boolean.TRUE)
        copySnapshotFromProductAvailability(locationId, productIds)
    }

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
        String snapshotDeleteProductFilter = filterByProducts
                ? "AND product_id IN (${productIds.collect { "'${it}'" }.join(',')})"
                : ""

        // Wipe the target-date rows for the affected location/products before re-inserting.
        // The inventory_snapshot unique key is (date, location, product_code, lot_number,
        // bin_location_name); a plain ON DUPLICATE KEY UPDATE would leave stale rows whose
        // (lot, bin) combinations no longer appear in pa.
        String deleteSql = """
            DELETE FROM inventory_snapshot
            WHERE date = '${tomorrowString}'
              AND location_id = '${locationId}'
              ${snapshotDeleteProductFilter}
        """

        String insertSql = """
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
        """

        Sql sql = new Sql(dataSource)
        try {
            int deleted = sql.executeUpdate(deleteSql)
            int inserted = sql.executeUpdate(insertSql)
            log.info "InventorySnapshotEtlService wrote location=${locationId} date=${tomorrowString} deleted=${deleted} inserted=${inserted}"
        } finally {
            sql.close()
        }
    }
}
