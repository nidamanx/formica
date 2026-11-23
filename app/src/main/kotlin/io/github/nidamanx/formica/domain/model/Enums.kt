// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain.model

// Named MeasureUnit, not Unit, to avoid shadowing kotlin.Unit
enum class MeasureUnit { PIECE, GRAM, KILOGRAM, MILLILITER, LITER, PACKAGE, JAR, BOTTLE }

/** Pieces, packages, jars and bottles are counted one by one; weights and volumes are measured. */
val MeasureUnit.isCountable: Boolean
    get() = this == MeasureUnit.PIECE || this == MeasureUnit.PACKAGE ||
        this == MeasureUnit.JAR || this == MeasureUnit.BOTTLE

enum class StoreType { SUPERMARKET, SHOP, MARKET, PRODUCER }

enum class Origin { PURCHASED, HOMEMADE }

enum class MovementType { PURCHASE, PREPARATION, CONSUMPTION, CORRECTION, DISCARD, TRANSFER }

enum class ShoppingStatus { TO_BUY, BOUGHT }

enum class ImportMode { MERGE, NEW_PRODUCTS_ONLY, REPLACE_ALL }
