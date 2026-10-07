package com.firkat.fillin.feature.categories.data.repository

import com.firkat.fillin.feature.categories.data.remote.CategoriesApi
import com.firkat.fillin.feature.categories.data.remote.dto.CategoryDto
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

class CategoriesRepositoryImpl(
    private val api: CategoriesApi,
    private val apiToken: String,
) : CategoriesRepository {
    override suspend fun getCategories(): List<Category> {
        if (apiToken.isBlank()) throw CategoriesException.MissingApiToken

        return try {
            api.getCategories(
                authorization = "Bearer $apiToken",
            ).items.map(CategoryDto::toDomain)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            throw when (exception.code()) {
                401 -> CategoriesException.Unauthorized
                403 -> CategoriesException.Forbidden
                else -> CategoriesException.Unknown
            }
        } catch (exception: IOException) {
            throw CategoriesException.Network
        } catch (exception: IllegalArgumentException) {
            throw CategoriesException.InvalidResponse
        } catch (exception: SerializationException) {
            throw CategoriesException.InvalidResponse
        }
    }
}

private fun CategoryDto.toDomain(): Category = Category(
    id = id,
    name = name,
    kind = when (kind) {
        "EXPENSE" -> CategoryKind.Expense
        "INCOME" -> CategoryKind.Income
        else -> throw IllegalArgumentException("Unsupported category kind")
    },
    icon = CategoryIcon(
        code = icon.code,
        emoji = icon.emoji,
    ),
)
