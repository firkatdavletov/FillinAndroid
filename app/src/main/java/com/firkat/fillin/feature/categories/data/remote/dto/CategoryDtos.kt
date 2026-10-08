package com.firkat.fillin.feature.categories.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CategoriesResponseDto(
    val items: List<CategoryDto>,
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val kind: String,
    val icon: CategoryIconDto,
)

@Serializable
data class CategoryIconDto(
    val code: String,
    val emoji: String,
)

@Serializable
data class CreateCategoryRequestDto(
    val name: String,
    val kind: String,
    val icon: String,
)

@Serializable
data class UpdateCategoryRequestDto(
    val name: String? = null,
    val icon: String? = null,
)
