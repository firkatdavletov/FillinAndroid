package com.firkat.fillin.ui.component

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.firkat.fillin.ui.theme.FillinTheme

@Preview(name = "Продукты")
@Preview(name = "Продукты dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconGroceriesPreview() {
    FillinTheme { Surface { CategoryIconImage("GROCERIES") } }
}

@Preview(name = "Кафе и рестораны")
@Preview(name = "Кафе и рестораны dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconCafeRestaurantsPreview() {
    FillinTheme { Surface { CategoryIconImage("CAFE_RESTAURANTS") } }
}

@Preview(name = "Автомобиль")
@Preview(name = "Автомобиль dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconCarPreview() {
    FillinTheme { Surface { CategoryIconImage("CAR") } }
}

@Preview(name = "Велосипед")
@Preview(name = "Велосипед dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconBicyclePreview() {
    FillinTheme { Surface { CategoryIconImage("BICYCLE") } }
}

@Preview(name = "Дом")
@Preview(name = "Дом dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconHomePreview() {
    FillinTheme { Surface { CategoryIconImage("HOME") } }
}

@Preview(name = "Ипотека")
@Preview(name = "Ипотека dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconMortgagePreview() {
    FillinTheme { Surface { CategoryIconImage("MORTGAGE") } }
}

@Preview(name = "Товары для дома")
@Preview(name = "Товары для дома dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconHouseholdGoodsPreview() {
    FillinTheme { Surface { CategoryIconImage("HOUSEHOLD_GOODS") } }
}

@Preview(name = "Одежда")
@Preview(name = "Одежда dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconClothingPreview() {
    FillinTheme { Surface { CategoryIconImage("CLOTHING") } }
}

@Preview(name = "Здоровье")
@Preview(name = "Здоровье dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconHealthPreview() {
    FillinTheme { Surface { CategoryIconImage("HEALTH") } }
}

@Preview(name = "Уход за собой")
@Preview(name = "Уход за собой dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconPersonalCarePreview() {
    FillinTheme { Surface { CategoryIconImage("PERSONAL_CARE") } }
}

@Preview(name = "Дети")
@Preview(name = "Дети dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconChildrenPreview() {
    FillinTheme { Surface { CategoryIconImage("CHILDREN") } }
}

@Preview(name = "Питомцы")
@Preview(name = "Питомцы dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconPetsPreview() {
    FillinTheme { Surface { CategoryIconImage("PETS") } }
}

@Preview(name = "Книги и образование")
@Preview(name = "Книги и образование dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconBooksEducationPreview() {
    FillinTheme { Surface { CategoryIconImage("BOOKS_EDUCATION") } }
}

@Preview(name = "Развлечения")
@Preview(name = "Развлечения dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconEntertainmentPreview() {
    FillinTheme { Surface { CategoryIconImage("ENTERTAINMENT") } }
}

@Preview(name = "Спорт")
@Preview(name = "Спорт dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconSportPreview() {
    FillinTheme { Surface { CategoryIconImage("SPORT") } }
}

@Preview(name = "Связь")
@Preview(name = "Связь dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconCommunicationPreview() {
    FillinTheme { Surface { CategoryIconImage("COMMUNICATION") } }
}

@Preview(name = "Подписки")
@Preview(name = "Подписки dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconSubscriptionsPreview() {
    FillinTheme { Surface { CategoryIconImage("SUBSCRIPTIONS") } }
}

@Preview(name = "Подарки")
@Preview(name = "Подарки dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconGiftPreview() {
    FillinTheme { Surface { CategoryIconImage("GIFT") } }
}

@Preview(name = "Кредит")
@Preview(name = "Кредит dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconLoanPreview() {
    FillinTheme { Surface { CategoryIconImage("LOAN") } }
}

@Preview(name = "Комиссии")
@Preview(name = "Комиссии dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconServiceFeesPreview() {
    FillinTheme { Surface { CategoryIconImage("SERVICE_FEES") } }
}

@Preview(name = "Зарплата")
@Preview(name = "Зарплата dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconSalaryPreview() {
    FillinTheme { Surface { CategoryIconImage("SALARY") } }
}

@Preview(name = "Премия")
@Preview(name = "Премия dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconBonusPreview() {
    FillinTheme { Surface { CategoryIconImage("BONUS") } }
}

@Preview(name = "Социальные выплаты")
@Preview(name = "Социальные выплаты dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconSocialBenefitsPreview() {
    FillinTheme { Surface { CategoryIconImage("SOCIAL_BENEFITS") } }
}

@Preview(name = "Unknown code")
@Preview(name = "Unknown code dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconCustomIconV2Preview() {
    FillinTheme { Surface { CategoryIconImage("custom.icon-v2") } }
}

@Preview(name = "Legacy code")
@Preview(name = "Legacy code dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIconFoodPreview() {
    FillinTheme { Surface { CategoryIconImage("FOOD") } }
}

