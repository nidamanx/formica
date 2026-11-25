// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain

/**
 * What an operation needs from the outside world, passed in so the domain stays pure
 * and deterministic in tests.
 *
 * @param now current time, epoch millis
 * @param today current local date, epoch day
 * @param deviceId id of this device, written into modifiedBy and Movement.deviceId
 * @param newId generator of new UUIDs
 */
class OperationContext(
    val now: Long,
    val today: Long,
    val deviceId: String,
    val newId: () -> String,
)

/** modifiedAt must grow at every change of a record, even if the device clock goes back. */
fun nextModifiedAt(now: Long, previous: Long?): Long =
    if (previous == null) now else maxOf(now, previous + 1)
