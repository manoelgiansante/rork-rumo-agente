package com.rumoagente.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rumoagente.ui.theme.RumoAgenteTheme
import com.rumoagente.ui.theme.RumoColors

data class PlanOption(
    val name: String,
    val price: String,
    val period: String,
    val credits: String,
    val features: List<String>,
    val isPopular: Boolean = false,
    val accentColor: Color = RumoColors.Accent
)

data class CreditPack(
    val credits: Int,
    val price: String,
    val perCredit: String
)

@Composable
fun SubscriptionScreen(
    onDismiss: () -> Unit = {}
) {
    var selectedPlanIndex by remember { mutableIntStateOf(1) }

    val plans = listOf(
        PlanOption(
            name = "Free",
            price = "R\$ 0",
            period = "/mes",
            credits = "10 creditos/mes",
            features = listOf("Chat basico", "1 app", "Suporte comunidade"),
            accentColor = RumoColors.SubtleText
        ),
        PlanOption(
            name = "Starter",
            price = "R\$ 49",
            period = "/mes",
            credits = "50 creditos/mes",
            features = listOf("Chat avancado", "5 apps", "Visualizacao de tela", "Suporte email"),
            isPopular = true,
            accentColor = RumoColors.Accent
        ),
        PlanOption(
            name = "Pro",
            price = "R\$ 149",
            period = "/mes",
            credits = "200 creditos/mes",
            features = listOf("Tudo do Starter", "Apps ilimitados", "Controle remoto", "API access", "Suporte prioritario"),
            accentColor = RumoColors.AccentBlue
        ),
        PlanOption(
            name = "Enterprise",
            price = "R\$ 499",
            period = "/mes",
            credits = "Creditos ilimitados",
            features = listOf("Tudo do Pro", "Multi-agente", "SLA dedicado", "Onboarding", "Suporte 24/7"),
            accentColor = RumoColors.Purple
        )
    )

    val creditPacks = listOf(
        CreditPack(50, "R\$ 29", "R\$ 0,58"),
        CreditPack(200, "R\$ 99", "R\$ 0,50"),
        CreditPack(500, "R\$ 199", "R\$ 0,40")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RumoColors.DarkBg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color.White
                )
            }
            Text(
                text = "Planos",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        // Title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Escolha seu plano",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
            Text(
                text = "Desbloqueie todo o potencial do Rumo Agente",
                color = RumoColors.SubtleText,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Plan cards
        plans.forEachIndexed { index, plan ->
            PlanCard(
                plan = plan,
                isSelected = selectedPlanIndex == index,
                onSelect = { selectedPlanIndex = index },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Subscribe button
        Button(
            onClick = { /* TODO: handle subscription */ },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = plans[selectedPlanIndex].accentColor
            )
        ) {
            Text(
                text = "Assinar ${plans[selectedPlanIndex].name}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = if (selectedPlanIndex == 0) Color.White else Color.Black
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Credit packs section
        HorizontalDivider(
            color = RumoColors.Divider,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Pacotes de creditos avulsos",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Text(
            text = "Compre creditos extras a qualquer momento",
            color = RumoColors.SubtleText,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            creditPacks.forEach { pack ->
                CreditPackCard(
                    pack = pack,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PlanCard(
    plan: PlanOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) plan.accentColor.copy(alpha = 0.5f)
    else RumoColors.CardBorder

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) plan.accentColor.copy(alpha = 0.06f)
            else RumoColors.CardBg
        ),
        onClick = onSelect
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isSelected,
                        onClick = onSelect,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = plan.accentColor,
                            unselectedColor = RumoColors.SubtleText
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = plan.name,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            if (plan.isPopular) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = plan.accentColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Popular",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = plan.accentColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Text(
                            text = plan.credits,
                            color = RumoColors.SubtleText,
                            fontSize = 12.sp
                        )
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = plan.price,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = plan.period,
                        color = RumoColors.SubtleText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            if (isSelected) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = RumoColors.Divider)
                Spacer(modifier = Modifier.height(10.dp))

                plan.features.forEach { feature ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = plan.accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = feature,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditPackCard(
    pack: CreditPack,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(1.dp, RumoColors.CardBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = RumoColors.CardBg),
        onClick = { }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${pack.credits}",
                color = RumoColors.AccentBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Text(
                text = "creditos",
                color = RumoColors.SubtleText,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = pack.price,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = "${pack.perCredit}/un",
                color = RumoColors.SubtleText,
                fontSize = 10.sp
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun SubscriptionScreenPreview() {
    RumoAgenteTheme {
        SubscriptionScreen()
    }
}
