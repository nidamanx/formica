// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

import io.github.nidamanx.formica.domain.model.Batch
import io.github.nidamanx.formica.domain.model.Product

/** Sections of the Expiries screen, in display order. */
enum class ExpirySection { EXPIRED, EXPIRING, PREPARING, LATER }

/** One group of batches on the Expiries screen. */
data class ExpiryEntry(
    val product: Product,
    val group: BatchGroup,
    val section: ExpirySection,
    /** Effective expiry of the group (epoch day); null only in [ExpirySection.PREPARING]. */
    val expiry: Long?,
)

data class ExpiryOverview(
    val expired: List<ExpiryEntry>,
    val expiring: List<ExpiryEntry>,
    val preparing: List<ExpiryEntry>,
    val later: List<ExpiryEntry>,
) {
    val isEmpty: Boolean get() = expired.isEmpty() && expiring.isEmpty() && preparing.isEmpty() && later.isEmpty()
    val all: List<ExpiryEntry> get() = expired + expiring + preparing + later
}

/**
 * Groups with stock of the products that are not deleted, by section. A group goes to the
 * first section that applies: expired, expiring (within the product's warning days or
 * [defaultWarningDays]), preparing (ready today or later), later (any other expiry).
 * Groups without expiry and already ready are not shown.
 *
 * @param compareNames locale-aware name order (a Collator in the app)
 */
fun expiryOverview(
    products: List<Product>,
    batches: List<Batch>,
    quantities: Map<String, Long>,
    today: Long,
    defaultWarningDays: Int,
    compareNames: Comparator<String>,
): ExpiryOverview {
    val batchesByProduct = batches.groupBy { it.productId }
    val entries = products.filter { it.deletedAt == null }.flatMap { product ->
        val warningDays = product.expiryWarningDays ?: defaultWarningDays
        val groups = groupBatches(batchesByProduct[product.id].orEmpty(), quantities, product.daysAfterOpening)
        visibleGroups(groups).mapNotNull { group ->
            val expiry = effectiveExpiry(group.batches.first(), product.daysAfterOpening)
            val readyFrom = group.key.readyFrom
            val section = when {
                expiry != null && expiry < today -> ExpirySection.EXPIRED
                expiry != null && expiry <= today + warningDays -> ExpirySection.EXPIRING
                readyFrom != null && readyFrom >= today -> ExpirySection.PREPARING
                expiry != null -> ExpirySection.LATER
                else -> return@mapNotNull null
            }
            ExpiryEntry(product, group, section, expiry)
        }
    }
    val byName = compareBy<ExpiryEntry, String>(compareNames) { it.product.name }
    val byExpiry = compareBy<ExpiryEntry, Long?>(nullsLast<Long>()) { it.expiry }.then(byName)
    val byReady = compareBy<ExpiryEntry, Long?>(nullsLast<Long>()) { it.group.key.readyFrom }.then(byName)
    val bySection = entries.groupBy { it.section }
    fun section(s: ExpirySection, order: Comparator<ExpiryEntry>) = bySection[s].orEmpty().sortedWith(order)
    return ExpiryOverview(
        expired = section(ExpirySection.EXPIRED, byExpiry),
        expiring = section(ExpirySection.EXPIRING, byExpiry),
        preparing = section(ExpirySection.PREPARING, byReady),
        later = section(ExpirySection.LATER, byExpiry),
    )
}

enum class DigestKind { EXPIRED, EXPIRING, READY }

/** One line of the expanded notification: one per product and kind. */
data class DigestLine(
    val productName: String,
    val kind: DigestKind,
    /** Days from today to the nearest expiry (negative when expired); 0 for [DigestKind.READY]. */
    val days: Long,
)

/** Content of the daily notification (SPEC §9); counts are of products, not batches. */
data class DailyDigest(
    val expiringProducts: Int,
    /** The farthest of the nearest expiries of the expiring products, in days from today. */
    val expiringWithinDays: Long,
    val expiredProducts: Int,
    val readyProducts: Int,
    /** Expired, then expiring by days, then ready; names in [compareNames] order within the same day. */
    val lines: List<DigestLine>,
)

/**
 * What the daily notification reports, or null when there is nothing to report: batches with
 * stock whose effective expiry has passed or is within the warning days, and batches that
 * become ready today. A product with batches of different kinds is counted in each kind.
 */
fun dailyDigest(overview: ExpiryOverview, today: Long, compareNames: Comparator<String>): DailyDigest? {
    fun nearest(entries: List<ExpiryEntry>, kind: DigestKind) = entries
        .groupBy { it.product.id }
        .values
        .map { list -> DigestLine(list.first().product.name, kind, list.minOf { requireNotNull(it.expiry) } - today) }
    val expired = nearest(overview.expired, DigestKind.EXPIRED)
    val expiring = nearest(overview.expiring, DigestKind.EXPIRING)
    val ready = overview.all
        .filter { it.group.key.readyFrom == today }
        .distinctBy { it.product.id }
        .map { DigestLine(it.product.name, DigestKind.READY, 0) }
    if (expired.isEmpty() && expiring.isEmpty() && ready.isEmpty()) return null
    val order = compareBy<DigestLine> { it.days }.thenBy(compareNames) { it.productName }
    return DailyDigest(
        expiringProducts = expiring.size,
        expiringWithinDays = expiring.maxOfOrNull { it.days } ?: 0L,
        expiredProducts = expired.size,
        readyProducts = ready.size,
        lines = expired.sortedWith(order) + expiring.sortedWith(order) + ready.sortedWith(order),
    )
}
