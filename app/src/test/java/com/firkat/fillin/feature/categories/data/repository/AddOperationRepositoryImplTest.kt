package com.firkat.fillin.feature.categories.data.repository

import com.firkat.fillin.feature.categories.data.remote.CategoriesApi
import com.firkat.fillin.feature.categories.data.remote.dto.CategoriesResponseDto
import com.firkat.fillin.feature.categories.data.remote.dto.CategoryDto
import com.firkat.fillin.feature.categories.data.remote.dto.CategoryIconDto
import com.firkat.fillin.feature.categories.data.remote.dto.CreateCategoryRequestDto
import com.firkat.fillin.feature.categories.data.remote.dto.UpdateCategoryRequestDto
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class AddOperationRepositoryImplTest {
    @Test
    fun `maps categories and preserves api name order`() = runBlocking {
        val api = RecordingCategoriesApi(
            response = CategoriesResponseDto(
                items = listOf(
                    category(
                        id = "groceries",
                        name = "Продукты",
                        kind = "EXPENSE",
                        iconCode = "FOOD",
                        emoji = "🍔"
                    ),
                    category(
                        id = "salary",
                        name = "Зарплата",
                        kind = "INCOME",
                        iconCode = "SALARY",
                        emoji = "💰"
                    )
                )
            )
        )
        val repository = CategoriesRepositoryImpl(
            api = api,
            apiToken = "test-token"
        )

        val result = repository.getCategories()

        assertEquals("Bearer test-token", api.authorization)
        assertEquals(listOf("Продукты", "Зарплата"), result.map { it.name })
        assertEquals(CategoryKind.Expense, result[0].kind)
        assertEquals("FOOD", result[0].icon.code)
        assertEquals("🍔", result[0].icon.emoji)
        assertEquals(CategoryKind.Income, result[1].kind)
    }

    @Test
    fun `rejects missing api token before network request`() {
        val api = RecordingCategoriesApi(CategoriesResponseDto(emptyList()))
        val repository = CategoriesRepositoryImpl(api = api, apiToken = "")

        val exception = assertThrows(CategoriesException.MissingApiToken::class.java) {
            runBlocking { repository.getCategories() }
        }

        assertSame(CategoriesException.MissingApiToken, exception)
        assertEquals(0, api.callCount)
    }

    @Test
    fun `rejects unsupported category kind`() {
        val repository = CategoriesRepositoryImpl(
            api = RecordingCategoriesApi(
                CategoriesResponseDto(
                    listOf(
                        category(
                            id = "category",
                            name = "Категория",
                            kind = "TRANSFER",
                            iconCode = "OTHER",
                            emoji = "📦"
                        )
                    )
                )
            ),
            apiToken = "test-token"
        )

        val exception = assertThrows(CategoriesException.InvalidResponse::class.java) {
            runBlocking { repository.getCategories() }
        }

        assertSame(CategoriesException.InvalidResponse, exception)
    }

    @Test
    fun `creates category with owner token and api enum values`() = runBlocking {
        val api = RecordingCategoriesApi(CategoriesResponseDto(emptyList()))
        val repository = CategoriesRepositoryImpl(api = api, apiToken = "test-token")

        val result = repository.createCategory(
            name = "Кафе",
            kind = CategoryKind.Expense,
            iconCode = "FOOD"
        )

        assertEquals("Bearer test-token", api.authorization)
        assertEquals(
            CreateCategoryRequestDto(
                name = "Кафе",
                kind = "EXPENSE",
                icon = "FOOD"
            ),
            api.createRequest
        )
        assertEquals("Кафе", result.name)
    }

    @Test
    fun `updates only supplied mutable category fields`() = runBlocking {
        val api = RecordingCategoriesApi(CategoriesResponseDto(emptyList()))
        val repository = CategoriesRepositoryImpl(api = api, apiToken = "test-token")

        repository.updateCategory(
            id = "category-id",
            name = "Рестораны",
            iconCode = null
        )

        assertEquals("category-id", api.categoryId)
        assertEquals(
            UpdateCategoryRequestDto(name = "Рестораны"),
            api.updateRequest
        )
    }

    @Test
    fun `deletes category by id with owner token`() = runBlocking {
        val api = RecordingCategoriesApi(CategoriesResponseDto(emptyList()))
        val repository = CategoriesRepositoryImpl(api = api, apiToken = "test-token")

        repository.deleteCategory("category-id")

        assertEquals("Bearer test-token", api.authorization)
        assertEquals("category-id", api.categoryId)
        assertEquals(1, api.deleteCallCount)
    }

    private fun category(
        id: String,
        name: String,
        kind: String,
        iconCode: String,
        emoji: String
    ) = CategoryDto(
        id = id,
        name = name,
        kind = kind,
        icon = CategoryIconDto(code = iconCode, emoji = emoji)
    )
}

private class RecordingCategoriesApi(private val response: CategoriesResponseDto) : CategoriesApi {
    var authorization: String? = null
    var callCount: Int = 0
    var createRequest: CreateCategoryRequestDto? = null
    var updateRequest: UpdateCategoryRequestDto? = null
    var categoryId: String? = null
    var deleteCallCount: Int = 0

    override suspend fun getCategories(
        authorization: String
    ): CategoriesResponseDto {
        callCount += 1
        this.authorization = authorization
        return response
    }

    override suspend fun createCategory(
        authorization: String,
        request: CreateCategoryRequestDto
    ): CategoryDto {
        this.authorization = authorization
        createRequest = request
        return category(
            id = "created-id",
            name = request.name,
            kind = request.kind,
            iconCode = request.icon,
            emoji = "🍔"
        )
    }

    override suspend fun updateCategory(
        authorization: String,
        categoryId: String,
        request: UpdateCategoryRequestDto
    ): CategoryDto {
        this.authorization = authorization
        this.categoryId = categoryId
        updateRequest = request
        return category(
            id = categoryId,
            name = request.name ?: "Категория",
            kind = "EXPENSE",
            iconCode = request.icon ?: "FOOD",
            emoji = "🍔"
        )
    }

    override suspend fun deleteCategory(
        authorization: String,
        categoryId: String
    ) {
        this.authorization = authorization
        this.categoryId = categoryId
        deleteCallCount += 1
    }

    private fun category(
        id: String,
        name: String,
        kind: String,
        iconCode: String,
        emoji: String
    ) = CategoryDto(
        id = id,
        name = name,
        kind = kind,
        icon = CategoryIconDto(code = iconCode, emoji = emoji)
    )
}
