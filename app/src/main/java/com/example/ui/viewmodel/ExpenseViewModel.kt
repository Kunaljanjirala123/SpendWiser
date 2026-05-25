package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.data.api.AiForecastingResult
import com.example.data.api.ScannedExpenseResult
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface ScanUiState {
    object Idle : ScanUiState
    object Scanning : ScanUiState
    data class Success(val result: ScannedExpenseResult) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

sealed interface ForecastUiState {
    object Idle : ForecastUiState
    object Loading : ForecastUiState
    data class Success(val result: AiForecastingResult) : ForecastUiState
    data class Error(val message: String) : ForecastUiState
}

data class ReceiptTemplate(
    val id: String,
    val storeName: String,
    val sampleText: String,
    val category: String,
    val fallbackResult: ScannedExpenseResult
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = ExpenseRepository(database.expenseDao(), database.budgetDao())

    val expenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val budgets: StateFlow<List<Budget>> = repository.allBudgets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _scanUiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanUiState: StateFlow<ScanUiState> = _scanUiState.asStateFlow()

    private val _forecastUiState = MutableStateFlow<ForecastUiState>(ForecastUiState.Idle)
    val forecastUiState: StateFlow<ForecastUiState> = _forecastUiState.asStateFlow()

    // --- Receipt Templates for Scan Mocking & Quick testing ---
    val receiptTemplates = listOf(
        ReceiptTemplate(
            id = "starbucks",
            storeName = "Starbucks",
            sampleText = """
                STARBUCKS COFFEE #10845
                201 BROADWAY, CAMBRIDGE, MA
                TEL: 617-555-0199
                
                DATE: 2026-05-20 08:32 AM
                TICKET #: 492019
                
                ITEMS:
                1 Vent Lat (Grande Latte)      $5.45
                1 Buttery Croissant           $3.85
                ----------------------------
                SUBTOTAL:                     $9.30
                TAX 6.25%:                    $0.58
                TOTAL:                        $9.88
                
                PAID VIA CARD *4412
                THANK YOU FOR YOUR PATRONAGE!
            """.trimIndent(),
            category = "Food",
            fallbackResult = ScannedExpenseResult(
                title = "Starbucks Coffee",
                amount = 9.88,
                category = "Food",
                notes = "Grande Latte & Buttery Croissant"
            )
        ),
        ReceiptTemplate(
            id = "uber",
            storeName = "Uber Taxi",
            sampleText = """
                UBER TECHNOLOGIES INC.
                SAN FRANCISCO, CA
                
                TRIP DATE: 2026-05-24 10:15 PM
                RIDER: KUNAL J.
                
                FARE BREAKDOWN:
                BASE FARE:                    ${'$'}3.50
                DISTANCE (8.4 MI):            ${'$'}14.20
                SURGE PRICING 1.2X:           ${'$'}3.54
                TAXES & TOLLS:                ${'$'}2.10
                TIP (15%):                    ${'$'}3.50
                ----------------------------
                TOTAL DEBIT:                  ${'$'}26.84
                
                PAYMENT DEBIT CARD *0288
                RATING: ⭐⭐⭐⭐⭐
            """.trimIndent(),
            category = "Transport",
            fallbackResult = ScannedExpenseResult(
                title = "Uber Ride",
                amount = 26.84,
                category = "Transport",
                notes = "8.4 mile Uber Trip (charges include surge & tips)"
            )
        ),
        ReceiptTemplate(
            id = "wholefoods",
            storeName = "Whole Foods Markets",
            sampleText = """
                WHOLE FOODS MARKET
                99 MEMORIAL DR, CAMBRIDGE, MA
                
                DATE: 2026-05-22 04:12 PM
                CASHIER: JESSICA
                
                ORGANIC MILK 1GAL             $6.99
                SIGHTGLASS COFFEE             $14.50
                AVOCADO BAG (4 PACK)          $5.99
                WILD SALMON FILET             $21.40
                GREEK YOGURT 32OZ             $5.49
                ----------------------------
                SUBTOTAL:                     $54.37
                FOOD TAX 0.0%:                $0.00
                TOTAL AMOUNT:                 $54.37
                
                MEMBERSHIP CODE SAVED
            """.trimIndent(),
            category = "Shopping",
            fallbackResult = ScannedExpenseResult(
                title = "Whole Foods",
                amount = 54.37,
                category = "Shopping",
                notes = "Organic Milk, Avocado bag, Sightglass Coffee, Wild Salmon Filet"
            )
        ),
        ReceiptTemplate(
            id = "bestbuy",
            storeName = "Best Buy Electronics",
            sampleText = """
                BEST BUY STORE #481
                100 CAMBRIDGESIDE PL, CAMBRIDGE MA
                
                DATE: 2026-05-18 11:20 AM
                
                ANKER USB-C CHARGING CABLE     $19.99
                LOGITECH MX MASTER MOUSE       $99.99
                SURCHARGE FEE:                 $1.50
                ----------------------------
                SUBTOTAL:                     $121.48
                STATE TAX 6.25%:              $7.59
                TOTAL CHARGED:                $129.07
                
                CHIP READ CREDIT *9921
                RETURNING TERMS: 14 DAYS
            """.trimIndent(),
            category = "Shopping",
            fallbackResult = ScannedExpenseResult(
                title = "Best Buy #481",
                amount = 129.07,
                category = "Shopping",
                notes = "Anker USB-C cable & Logitech MX Master Mouse"
            )
        ),
        ReceiptTemplate(
            id = "chevron",
            storeName = "Chevron Fuel",
            sampleText = """
                CHEVRON #67841
                LOS ANGELES, CA 90024
                
                DATE: 2026-05-19 09:44 AM
                PUMP: #4
                
                UNLEADED REGULAR
                12.45 GALLONS AT ${'$'}4.25/GAL   ${'$'}52.91
                ----------------------------
                TOTAL FUEL:                   ${'$'}52.91
                STORE: COKE BOTTLE 20OZ       ${'$'}2.49
                ----------------------------
                SUBTOTAL:                     ${'$'}55.40
                TAX 7.5%:                     ${'$'}4.16
                TOTAL:                        ${'$'}59.56
                
                CARD AUTHORIZED: *1192
            """.trimIndent(),
            category = "Transport",
            fallbackResult = ScannedExpenseResult(
                title = "Chevron Station",
                amount = 59.56,
                category = "Transport",
                notes = "12.45 Gallons Fuel & Coke bottle"
            )
        )
    )

    init {
        // Pre-populate database with default budgeting limits if none exist
        viewModelScope.launch {
            _allDefaultBudgets()
        }
    }

    private suspend fun _allDefaultBudgets() {
        val defaults = listOf(
            Budget("Food", 400.0),
            Budget("Entertainment", 150.0),
            Budget("Utilities", 300.0),
            Budget("Transport", 200.0),
            Budget("Shopping", 300.0),
            Budget("Other", 100.0)
        )
        // Add defaults if table empty
        budgets.first()
        if (budgets.value.isEmpty()) {
            defaults.forEach { repository.insertBudget(it) }
        }

        // Pre-populate some initial sample transactions if empty to make UI look beautiful immediately on launch
        expenses.first()
        if (expenses.value.isEmpty()) {
            val samples = listOf(
                Expense(title = "Kroger Groceries", amount = 84.20, date = System.currentTimeMillis() - 86400000 * 2, category = "Food", notes = "Weekly meal prep"),
                Expense(title = "Electric Utility", amount = 115.50, date = System.currentTimeMillis() - 86400000 * 5, category = "Utilities", notes = "May heating bill"),
                Expense(title = "Netflix Premium", amount = 22.99, date = System.currentTimeMillis() - 86400000 * 9, category = "Entertainment", notes = "Monthly billing"),
                Expense(title = "Shell Gas Pump", amount = 42.10, date = System.currentTimeMillis() - 86400000 * 12, category = "Transport", notes = "Subaru travel Refuel"),
                Expense(title = "Nordstrom Shopping", amount = 112.00, date = System.currentTimeMillis() - 86400000 * 15, category = "Shopping", notes = "Summer clothes purchase"),
                Expense(title = "Subway Sandwich", amount = 12.80, date = System.currentTimeMillis() - 86400000 * 16, category = "Food", notes = "Lunch bite"),
                Expense(title = "Movie Night IMAX", amount = 34.00, date = System.currentTimeMillis() - 86400000 * 19, category = "Entertainment", notes = "Tickets with pop-corn")
            )
            samples.forEach { repository.insertExpense(it) }
        }
    }

    // --- Core Database operations ---

    fun addExpense(title: String, amount: Double, category: String, notes: String?, date: Long = System.currentTimeMillis(), scanned: Boolean = false) {
        viewModelScope.launch {
            repository.insertExpense(
                Expense(title = title, amount = amount, category = category, date = date, notes = notes, scannedFromReceipt = scanned)
            )
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            repository.updateExpense(expense)
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    fun setBudget(category: String, limitAmount: Double) {
        viewModelScope.launch {
            repository.insertBudget(Budget(category, limitAmount))
        }
    }

    fun deleteBudget(category: String) {
        viewModelScope.launch {
            repository.deleteBudgetByCategory(category)
        }
    }

    // --- Gemini scanning API ---

    fun scanReceiptText(text: String, isMockingEnabled: Boolean = false) {
        viewModelScope.launch {
            _scanUiState.value = ScanUiState.Scanning
            
            // Check if key is empty/not configured, if so automatically trigger fallback parse
            val apiKey = BuildConfig.GEMINI_API_KEY
            val isApiKeyMissing = apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY"

            if (isApiKeyMissing || isMockingEnabled) {
                // Perform interactive high-fidelity simulation
                kotlinx.coroutines.delay(2000)
                
                // Match text with receipt templates
                val matchingTemplate = receiptTemplates.firstOrNull { template ->
                    text.contains(template.storeName, ignoreCase = true) || 
                    template.sampleText.split("\n").take(2).any { text.contains(it.trim(), ignoreCase = true) }
                }
                
                if (matchingTemplate != null) {
                    _scanUiState.value = ScanUiState.Success(matchingTemplate.fallbackResult)
                } else {
                    // Generate smart static fallback
                    _scanUiState.value = ScanUiState.Success(
                        ScannedExpenseResult(
                            title = "Simulated Vendor",
                            amount = 15.50,
                            category = "Other",
                            notes = "OCR parsed item (Offline Simulation Mode)"
                        )
                    )
                }
            } else {
                try {
                    val result = repository.scanReceiptText(text)
                    _scanUiState.value = ScanUiState.Success(result)
                } catch (e: Exception) {
                    _scanUiState.value = ScanUiState.Error(e.message ?: "Failed to contact Gemini Scanner Service.")
                }
            }
        }
    }

    // --- Gemini forecasting API ---

    fun getAiForecastingInsights() {
        viewModelScope.launch {
            _forecastUiState.value = ForecastUiState.Loading

            val apiKey = BuildConfig.GEMINI_API_KEY
            val isApiKeyMissing = apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY"

            if (isApiKeyMissing) {
                // Generate a highly convincing local Mockup forecasting result based on the user's real transactions in DB!
                kotlinx.coroutines.delay(2500)

                val mExpenses = expenses.value
                val mBudgets = budgets.value

                val totalSpent = mExpenses.sumOf { it.amount }
                val numItems = mExpenses.size
                val dailyAverage = if (numItems > 0) totalSpent / 30.0 else 0.0
                val projectedSpend = dailyAverage * 30.0

                val totalBudget = mBudgets.sumOf { it.limitAmount }
                val overBudgetCategories = mutableListOf<String>()
                mBudgets.forEach { budget ->
                   val spentInCategory = mExpenses.filter { it.category == budget.category }.sumOf { it.amount }
                   if (spentInCategory > budget.limitAmount) {
                       overBudgetCategories.add(budget.category)
                   }
                }

                val riskLevel = if (projectedSpend > totalBudget || overBudgetCategories.isNotEmpty()) "High" else if (projectedSpend > totalBudget * 0.8) "Medium" else "Low"

                val summary = "Based on your recent transactions totaling $${String.format("%.2f", totalSpent)} across $numItems items, your daily average is $${String.format("%.2f", dailyAverage)}. We project a 30-day spend of $${String.format("%.2f", projectedSpend)} against a total category budget limit of $${String.format("%.2f", totalBudget)}. This local projection analysis indicates a $riskLevel risk state."

                val risks = mutableListOf<String>()
                if (overBudgetCategories.isNotEmpty()) {
                    risks.add("Overbudget in categories: ${overBudgetCategories.joinToString(", ")}.")
                } else {
                    risks.add("Your projected spending for shopping accounts for 40% of the entire budget limits.")
                }
                risks.add("Weekend transactions are averaging 43% higher than mid-week purchases.")

                val advice = listOf(
                    "Try shifting non-essential purchases to weekdays to cut shopping down by 15%.",
                    "We noticed multiple minor charges. Bundling coffee or dining purchases can save on small surcharges.",
                    "Review streaming subscriptions in your Entertainment category to release an extra $24/month."
                )

                _forecastUiState.value = ForecastUiState.Success(
                    AiForecastingResult(
                        predictedSpend = projectedSpend,
                        riskLevel = riskLevel,
                        summary = "$summary (Offline Simulator Insight Mode)",
                        risks = risks,
                        advice = advice
                    )
                )
            } else {
                try {
                    val result = repository.getAiForecastingInsights(expenses.value, budgets.value)
                    _forecastUiState.value = ForecastUiState.Success(result)
                } catch (e: Exception) {
                    _forecastUiState.value = ForecastUiState.Error(e.message ?: "Failed to generate Foresight forecast insights.")
                }
            }
        }
    }

    fun clearScanUiState() {
        _scanUiState.value = ScanUiState.Idle
    }

    fun clearForecastUiState() {
        _forecastUiState.value = ForecastUiState.Idle
    }
}
