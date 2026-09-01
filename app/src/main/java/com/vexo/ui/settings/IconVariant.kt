package com.vexo.ui.settings

/**
 * Available app icon variants that users can choose from.
 * Each variant corresponds to an activity-alias in AndroidManifest.xml.
 */
enum class IconVariant(
    val displayName: String,
    val description: String,
    val aliasClassName: String,
) {
    LIQUID_GOLD(
        displayName = "Liquid Gold",
        description = "Luxury, sophisticated",
        aliasClassName = "com.vexo.MainActivityLiquidGold",
    ),
    ELECTRIC_BLUE(
        displayName = "Electric Blue",
        description = "Modern, tech-focused",
        aliasClassName = "com.vexo.MainActivityElectricBlue",
    ),
    TITANIUM_MINIMAL(
        displayName = "Titanium Minimal",
        description = "Ultra-minimal, professional",
        aliasClassName = "com.vexo.MainActivityTitaniumMinimal",
    );

    companion object {
        fun fromAliasClassName(className: String): IconVariant {
            return values().find { it.aliasClassName == className } ?: LIQUID_GOLD
        }
    }
}
