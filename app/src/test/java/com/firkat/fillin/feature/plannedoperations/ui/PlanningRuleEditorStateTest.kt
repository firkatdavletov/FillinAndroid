package com.firkat.fillin.feature.plannedoperations.ui

import com.firkat.fillin.feature.categories.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PlanningRuleEditorStateTest {
    private val category = Category("food", "Продукты", CategoryKind.Expense, CategoryIcon("HOME"))
    @Test fun `new form is empty and unchanged`() {
        val state = RuleEditorState(loading = false)
        assertFalse(state.dirty)
        assertFalse(state.canSave)
        assertEquals("", state.draft.categoryId)
    }
    @Test fun `valid draft accepts decimal comma and rejects invalid money`() {
        val initial = RuleDraft()
        val state = RuleEditorState(initial = initial, draft = initial.copy(name = "Еда", categoryId = "food", amount = "123,45"), categories = listOf(category), loading = false)
        assertTrue(state.canSave)
        listOf("0", "-1", "1.234", "1e2", "abc").forEach { assertFalse(state.copy(draft = state.draft.copy(amount = it)).canSave) }
        assertFalse(state.copy(draft = state.draft.copy(name = " ")).canSave)
        assertFalse(state.copy(saving = true).canSave)
        assertFalse(state.copy(draft = state.draft.copy(date = initial.date.minusDays(1))).canSave)
    }
}
