package com.example.data

import com.example.BuildConfig
import com.example.data.api.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import java.io.IOException

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val budgetDao: BudgetDao,
    private val apiService: GeminiApiService = RetrofitClient.service
) {
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()
    val allBudgets: Flow<List<Budget>> = budgetDao.getAllBudgets()

    suspend fun insertExpense(expense: Expense) = expenseDao.insertExpense(expense)
    suspend fun updateExpense(expense: Expense) = expenseDao.updateExpense(expense)
    suspend fun deleteExpense(expense: Expense) = expenseDao.deleteExpense(expense)
    suspend fun deleteExpenseById(id: Int) = expenseDao.deleteExpenseById(id)

    suspend fun insertBudget(budget: Budget) = budgetDao.insertBudget(budget)
    suspend fun deleteBudgetByCategory(category: String) = budgetDao.deleteBudgetByCategory(category)

    // --- Gemini AIService Integration ---

    suspend fun scanReceiptText(receiptText: String): ScannedExpenseResult {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API key is not configured. Please add your key in the AI Studio Secrets panel.")
        }

        // Setup Schema
        val expenseSchema = ResponseSchema(
            type = "OBJECT",
            properties = mapOf(
                "title" to SchemaProperty(type = "STRING", description = "Vendor name, e.g. Starbucks or Target"),
                "amount" to SchemaProperty(type = "NUMBER", description = "Grand total amount from invoice"),
                "category" to SchemaProperty(type = "STRING", description = "Must compile exactly to one of: Food, Entertainment, Utilities, Transport, Shopping, Other"),
                "notes" to SchemaProperty(type = "STRING", description = "Briefly summarize what was purchased, e.g. Coffee and pastries"),
                "items" to SchemaProperty(
                    type = "ARRAY",
                    description = "Optional breakdowns of purchased items",
                    items = ResponseSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "name" to SchemaProperty(type = "STRING", description = "Item name"),
                            "price" to SchemaProperty(type = "NUMBER", description = "Item price")
                        ),
                        required = listOf("name", "price")
                    )
                )
            ),
            required = listOf("title", "amount", "category")
        )

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = "Analyze this scanned text from a receipt and identify the store title, total list price, main category, brief notes, and itemized breakdown:\n\n$receiptText")
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f,
                responseSchema = expenseSchema
            ),
            systemInstruction = Content(
                parts = listOf(
                    Part(text = "You are a professional financial scanner tool. Parse the receipt text and always output valid structured JSON matching the requested schema. Categorize accurately into Food, Entertainment, Utilities, Transport, Shopping, or Other.")
                )
            )
        )

        val response = apiService.generateContent(apiKey, request)
        val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IOException("No parsing response retrieved from AI Model.")

        // Parse with Moshi
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(ScannedExpenseResult::class.java)
        return adapter.fromJson(textResponse) ?: throw IOException("Failed to deserealize AI response.")
    }

    suspend fun getAiForecastingInsights(expenses: List<Expense>, budgets: List<Budget>): AiForecastingResult {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API key is not configured. Please add your key in the AI Studio Secrets panel.")
        }

        // Format expenses for the AI
        val expenseSummary = expenses.take(30).joinToString("\n") {
            "- ${it.title} (${it.category}): $${it.amount} on Date timestamp ${it.date}"
        }

        val budgetSummary = budgets.joinToString("\n") {
            "- ${it.category} Budget limit: $${it.limitAmount}"
        }

        val forecastSchema = ResponseSchema(
            type = "OBJECT",
            properties = mapOf(
                "predictedSpend" to SchemaProperty(type = "NUMBER", description = "Expected projected total spending over next 30 days based on historical trend"),
                "riskLevel" to SchemaProperty(type = "STRING", description = "Budget compliance risk level: Low, Medium, High"),
                "summary" to SchemaProperty(type = "STRING", description = "A detailed, structured paragraph evaluating overall spending and budget performance"),
                "risks" to SchemaProperty(
                    type = "ARRAY",
                    description = "Key overspending risk alerts (up to 3 items)",
                    items = ResponseSchema(type = "STRING")
                ),
                "advice" to SchemaProperty(
                    type = "ARRAY",
                    description = "Actionable, highly clever ways to save and optimize margins",
                    items = ResponseSchema(type = "STRING")
                )
            ),
            required = listOf("predictedSpend", "riskLevel", "summary", "risks", "advice")
        )

        val prompt = """
            Here is my recent transaction history:
            $expenseSummary
            
            Here are my category budget limits:
            $budgetSummary
            
            Please perform a forecast analysis of my spending patterns for the next 30 days. Detect any overspending risk, categories approaching limits, anomalies, and provide custom savings advice.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = prompt))
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.5f,
                responseSchema = forecastSchema
            ),
            systemInstruction = Content(
                parts = listOf(
                    Part(text = "You are a professional senior financial analyst assistant. Analyze the logs against budgets and project future spending details. Return a high-quality analysis output complying exactly with the JSON schema.")
                )
            )
        )

        val response = apiService.generateContent(apiKey, request)
        val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IOException("No forecasting response retrieved from AI Model.")

        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(AiForecastingResult::class.java)
        return adapter.fromJson(textResponse) ?: throw IOException("Failed to deserialize AI forecasting response.")
    }
}
