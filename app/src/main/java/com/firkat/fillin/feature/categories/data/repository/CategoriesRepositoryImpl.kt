package com.firkat.fillin.feature.categories.data.repository

import com.firkat.fillin.feature.categories.data.remote.CategoriesApi
import com.firkat.fillin.feature.categories.data.remote.dto.CategoryDto
import com.firkat.fillin.feature.categories.data.remote.dto.CreateCategoryRequestDto
import com.firkat.fillin.feature.categories.data.remote.dto.UpdateCategoryRequestDto
import com.firkat.fillin.feature.categories.domain.model.Category
import com.firkat.fillin.feature.categories.domain.model.CategoryIcon
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import com.firkat.fillin.feature.categories.domain.repository.CategoriesRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

class CategoriesRepositoryImpl(private val api: CategoriesApi, private val apiToken: String) : CategoriesRepository {
    override suspend fun getCategories(): List<Category> = execute {
        api.getCategories(
            authorization = authorization
        ).items.map(CategoryDto::toDomain)
    }

    override suspend fun createCategory(
        name: String,
        kind: CategoryKind,
        iconCode: String
    ): Category = execute {
        api.createCategory(
            authorization = authorization,
            request = CreateCategoryRequestDto(
                name = name,
                kind = kind.apiValue,
                icon = iconCode
            )
        ).toDomain()
    }

    override suspend fun updateCategory(
        id: String,
        name: String?,
        iconCode: String?
    ): Category = execute {
        api.updateCategory(
            authorization = authorization,
            categoryId = id,
            request = UpdateCategoryRequestDto(
                name = name,
                icon = iconCode
            )
        ).toDomain()
    }

    override suspend fun deleteCategory(
        id: String
    ): Unit = execute {
        api.deleteCategory(
            authorization = authorization,
            categoryId = id
        )
    }

    private suspend fun <T> execute(
        block: suspend RequestContext.() -> T
    ): T {
        if (apiToken.isBlank()) throw CategoriesException.MissingApiToken

        return try {
            RequestContext("Bearer $apiToken").block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            throw when (exception.code()) {
                400, 422 -> CategoriesException.InvalidRequest
                401 -> CategoriesException.Unauthorized
                403 -> CategoriesException.Forbidden
                404 -> CategoriesException.NotFound
                409 -> CategoriesException.Conflict
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

    private data class RequestContext(val authorization: String)
}

private val CategoryKind.apiValue: String
    get() = when (this) {
        CategoryKind.Expense -> "EXPENSE"
        CategoryKind.Income -> "INCOME"
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
        emoji = icon.emoji
    )
)
