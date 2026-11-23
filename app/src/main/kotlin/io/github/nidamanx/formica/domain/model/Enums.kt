// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.domain.model

// Named MeasureUnit, not Unit, to avoid shadowing kotlin.Unit
enum class MeasureUnit { PIECE, GRAM, KILOGRAM, MILLILITER, LITER, PACKAGE, JAR, BOTTLE }

enum class StoreType { SUPERMARKET, SHOP, MARKET, PRODUCER }

enum class Origin { PURCHASED, HOMEMADE }

enum class MovementType { PURCHASE, PREPARATION, CONSUMPTION, CORRECTION, DISCARD, TRANSFER }

enum class ShoppingStatus { TO_BUY, BOUGHT }
