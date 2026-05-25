package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.ui.viewmodel.ForecastUiState

@Composable
fun ForecastingScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val expenses by viewModel.expenses.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val forecastUiState by viewModel.forecastUiState.collectAsState()

    val totalSpent = remember(expenses) { expenses.sumOf { it.amount } }
    val totalBudget = remember(budgets) { budgets.sumOf { it.limitAmount } }

    // Proportional formula trend calculation
    val numDays = 30
    val dailyAverageSpend = remember(expenses) {
        if (expenses.isNotEmpty()) totalSpent / 15.0 else 0.0 // assume we span 15 days of data
    }
    val projectedSpend30Days = remember(dailyAverageSpend) {
        dailyAverageSpend * 30.0
    }

    val isBreachingTotal = projectedSpend30Days > totalBudget
    val budgetCollisionDays = remember(dailyAverageSpend, totalBudget, totalSpent) {
        if (dailyAverageSpend > 0 && totalBudget > totalSpent) {
            ((totalBudget - totalSpent) / dailyAverageSpend).toInt().coerceAtLeast(1)
        } else {
            0
        }
    }

    val apiKey = BuildConfig.GEMINI_API_KEY
    val isApiKeyMissing = apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY"

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Column {
                Text(
                    text = "Spending Foresight",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Model-based mathematical forecasting + Gemini AI consultations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- SECTION 1: Mathematical Linear Projection ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Linear Spending Projection",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current Daily Average",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$${String.format("%.2f", dailyAverageSpend)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Projected 30-Day Spend",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$${String.format("%.2f", projectedSpend30Days)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isBreachingTotal) MaterialTheme.colorScheme.error else getCategoryColor("food"),
                                modifier = Modifier.testTag("projected_30_day_spend")
                            )
                        }
                    }

                    // Collision alerts
                    if (isBreachingTotal && budgetCollisionDays > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "At current velocity, you will breach total category budgets in $budgetCollisionDays days!",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    } else if (!isBreachingTotal) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(getCategoryColor("food").copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = getCategoryColor("food"),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Spending stable. You are on track to stay within budget targets this month!",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = getCategoryColor("food")
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- SECTION 2: Projection Trend Line Chart Canvas ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Forecast Trajectory Graph",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Comparing current accrued expenses vs linear future projection against thresholds.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val gridColor = MaterialTheme.colorScheme.surfaceVariant
                    val lineAccruedColor = MaterialTheme.colorScheme.primary
                    val lineProjectedColor = MaterialTheme.colorScheme.error
                    val budgetLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 10.dp)
                        ) {
                            val w = size.width
                            val h = size.height
                            
                            // Y-axis guidelines
                            drawLine(gridColor, Offset(0f, h*0.2f), Offset(w, h*0.2f), strokeWidth = 1f)
                            drawLine(gridColor, Offset(0f, h*0.5f), Offset(w, h*0.5f), strokeWidth = 1f)
                            drawLine(gridColor, Offset(0f, h*0.8f), Offset(w, h*0.8f), strokeWidth = 1f)
                            
                            // X-axis divider
                            drawLine(gridColor, Offset(w*0.5f, 0f), Offset(w*0.5f, h), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f,5f)))

                            // Draw horizontal budget line (Y position around 30%)
                            val budgetY = h * 0.3f
                            drawLine(budgetLineColor, Offset(0f, budgetY), Offset(w, budgetY), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f,10f)))

                            // Draw historic accrued line (days 1-15: Left side to Center)
                            val startX = 0f
                            val midX = w * 0.5f
                            val startY = h * 0.9f
                            val midY = h * 0.6f
                            drawLine(lineAccruedColor, Offset(startX, startY), Offset(midX, midY), strokeWidth = 5f)

                            // Draw projected trend line (days 16-30: Center to Right side)
                            val endY = h * if (isBreachingTotal) 0.15f else 0.45f // if breaching, line goes higher than budget line
                            drawLine(lineProjectedColor, Offset(midX, midY), Offset(w, endY), strokeWidth = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f,8f)))
                        }
                    }

                    // Legend indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(lineAccruedColor, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Accrued Spend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(lineProjectedColor, RoundedCornerShape(2.dp)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Projected Forecast", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color.Gray, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Budget Threshold", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // --- SECTION 3: Gemini AI Foresight Consultant Trigger ---
        item {
            Button(
                onClick = { viewModel.getAiForecastingInsights() },
                enabled = forecastUiState !is ForecastUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("request_ai_forecast_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (forecastUiState is ForecastUiState.Loading) "Consulting Financial Analyst Model..." else "Request Gemini AI Projections",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- SECTION 4: AI Projections Display State ---
        when (val state = forecastUiState) {
            is ForecastUiState.Loading -> {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Isolating seasonal trends & forecasting saving paths...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            is ForecastUiState.Success -> {
                item {
                    AiInsightsResultCard(
                        result = state.result,
                        onClear = { viewModel.clearForecastUiState() }
                    )
                }
            }
            is ForecastUiState.Error -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Analysis Integration Interrupted",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(onClick = { viewModel.clearForecastUiState() }) {
                                Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
fun AiInsightsResultCard(
    result: com.example.data.api.AiForecastingResult,
    onClear: () -> Unit
) {
    val riskColor = when (result.riskLevel.lowercase()) {
        "high" -> MaterialTheme.colorScheme.error
        "medium" -> getCategoryColor("utilities")
        else -> getCategoryColor("food")
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, riskColor.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_forecast_result_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with AI Tag & Risk Badge
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Gemini Pro Analyst Insights",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Box(
                    modifier = Modifier
                        .background(riskColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "RISK: ${result.riskLevel}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = riskColor
                    )
                }
            }

            // Spending Projections Panel
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "AI Projected Spending",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$${String.format("%.2f", result.predictedSpend)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("ai_projected_spend")
                )
            }

            // Summary Text
            Text(
                text = result.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )

            // Alert risks bullet points
            if (result.risks.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Target Warnings Detected",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    result.risks.forEach { risk ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = risk,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Advice / Actions to Save
            if (result.advice.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Recommended Saving Measures",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = getCategoryColor("food")
                    )
                    result.advice.forEach { adviceText ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = getCategoryColor("food"),
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = adviceText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Dismiss Insights")
            }
        }
    }
}
