package com.firkat.fillin.feature.categories.data.repository

import com.firkat.fillin.feature.categories.data.remote.CategoriesApi
import com.firkat.fillin.feature.categories.data.remote.dto.CategoriesResponseDto
import com.firkat.fillin.feature.categories.data.remote.dto.CategoryDto
import com.firkat.fillin.feature.categories.data.remote.dto.CategoryIconDto
import com.firkat.fillin.feature.categories.domain.model.CategoryKind
import com.firkat.fillin.feature.categories.domain.repository.CategoriesException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class CategoriesRepositoryImplTest {
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
                        emoji = "🍔",
                    ),
                    category(
                        id = "salary",
                        name = "Зарплата",
                        kind = "INCOME",
                        iconCode = "SALARY",
                        emoji = "💰",
                    ),
                ),
            ),
        )
        val repository = CategoriesRepositoryImpl(
            api = api,
            apiToken = "test-token",
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
                            emoji = "📦",
                        ),
                    ),
                ),
            ),
            apiToken = "test-token",
        )

        val exception = assertThrows(CategoriesException.InvalidResponse::class.java) {
            runBlocking { repository.getCategories() }
        }

        assertSame(CategoriesException.InvalidResponse, exception)
    }

    private fun category(
        id: String,
        name: String,
        kind: String,
        iconCode: String,
        emoji: String,
    ) = CategoryDto(
        id = id,
        name = name,
        kind = kind,
        icon = CategoryIconDto(code = iconCode, emoji = emoji),
    )
}

private class RecordingCategoriesApi(
    private val response: CategoriesResponseDto,
) : CategoriesApi {
    var authorization: String? = null
    var callCount: Int = 0

    override suspend fun getCategories(
        authorization: String,
    ): CategoriesResponseDto {
        callCount += 1
        this.authorization = authorization
        return response
    }
}
