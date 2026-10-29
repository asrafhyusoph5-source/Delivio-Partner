package com.example.util

import com.example.data.model.BusinessType

val FOOD_CATEGORIES: List<String> = listOf(
    "Dessert",
    "Burgers",
    "Pizza",
    "Chicken",
    "Drinks"
)

val CONVENIENCE_CATEGORIES: List<String> = listOf(
    "Grocery Stores",
    "Pharmacies",
    "Convenience Stores",
    "School Supplies",
    "Flowers",
    "Pet Supplies",
    "Beauty",
    "Fresh & Frozen",
    "Electronics"
)

fun getAllowedCategoriesForBusinessType(type: BusinessType): List<String> {
    return when (type) {
        BusinessType.FOOD -> FOOD_CATEGORIES
        BusinessType.CONVENIENCE -> CONVENIENCE_CATEGORIES
    }
}

fun getAllowedCategoriesForTypeString(typeStr: String?): List<String> {
    val bType = BusinessType.fromString(typeStr)
    return getAllowedCategoriesForBusinessType(bType)
}
