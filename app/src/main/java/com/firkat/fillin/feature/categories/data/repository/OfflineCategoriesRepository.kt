package com.firkat.fillin.feature.categories.data.repository

import com.firkat.fillin.feature.categories.domain.model.*
import com.firkat.fillin.feature.categories.domain.repository.*
import com.firkat.fillin.feature.sync.data.BudgetLocalFailure
import com.firkat.fillin.feature.sync.data.BudgetStore
import kotlinx.coroutines.flow.first

class OfflineCategoriesRepository(private val store: BudgetStore) : CategoriesRepository {

    override fun observeCategories() = store.observeCategories()

    override fun requestSync() = store.requestSync()

    override suspend fun getCategories() = observeCategories().first()

    override suspend fun createCategory(
        name: String,
        kind: CategoryKind,
        iconCode: String
    ): Category = execute {
        val id =
            store.enqueue("CREATE_CATEGORY") {
                it.copy(
                    name = name,
                    kind =
                    if (kind ==
                        CategoryKind.Income
                    ) {
                        "INCOME"
                    } else {
                        "EXPENSE"
                    },
                    icon = iconCode
                )
            }
        store.snapshot().categoryList().first { it.id == id }
    }

    override suspend fun updateCategory(
        id: String,
        name: String?,
        iconCode: String?
    ): Category = execute {
        store.enqueue("UPDATE_CATEGORY", id) { it.copy(name = name, icon = iconCode) }
        store.snapshot().categoryList().first { it.id == id }
    }

    override suspend fun deleteCategory(
        id: String
    ) {
        execute { store.enqueue("DELETE_CATEGORY", id) }
    }

    override suspend fun updateCategory(
        category: Category,
        name: String?,
        iconCode: String?
    ): Category = execute {
        store.enqueue("UPDATE_CATEGORY", category.id, observedCategoryRevision = category.revision) {
            it.copy(name = name, icon = iconCode)
        }
        store.snapshot().categoryList().first { it.id == category.id }
    }

    override suspend fun deleteCategory(
        category: Category
    ) {
        execute { store.enqueue("DELETE_CATEGORY", category.id, observedCategoryRevision = category.revision) }
    }

    private suspend fun <T> execute(
        block: suspend () -> T
    ): T = try {
        block()
    } catch (e: BudgetLocalFailure) {
        throw when (e.code) {
            "forbidden" -> CategoriesException.Forbidden
            "conflict" -> CategoriesException.Conflict
            "not_found" -> CategoriesException.NotFound
            else -> CategoriesException.InvalidRequest
        }
    } catch (_: IllegalArgumentException) {
        throw CategoriesException.InvalidRequest
    }
}
