package com.example.alakey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight

data class MarketItem(val title: String, val author: String, val type: String, val query: String)

private val kenyaItems = listOf(
    MarketItem("The 97s Podcast", "3MenArmy", "Podcast", "The 97s Podcast"),
    MarketItem("So This Is Love", "Julia Gaitho", "Podcast", "So This Is Love Julia Gaitho"),
    MarketItem("The Mkurugenzi Podcast", "Abel Mutua", "Podcast", "The Mkurugenzi Podcast"),
    MarketItem("Mic Cheque Podcast", "Chaxy, Mariah, Mwass", "Podcast", "Mic Cheque Podcast"),
    MarketItem("The Messy Inbetween", "TMI", "Podcast", "The Messy Inbetween"),
    MarketItem("It's Related, I Promise", "IRIP", "Podcast", "It's Related I Promise"),
    MarketItem("The Kenyan Bookmaker", "Michelle C. Korir", "Substack", "The Kenyan Bookmaker Substack"),
    MarketItem("Built In Kenya", "Dan Mbure", "Substack", "Built In Kenya Substack")
)
private val usaItems = listOf(
    MarketItem("Pivot", "Kara Swisher & Scott Galloway", "Podcast", "Pivot Podcast"),
    MarketItem("a16z Podcast", "Andreessen Horowitz", "Podcast", "a16z Podcast"),
    MarketItem("How I Built This", "Guy Raz", "Podcast", "How I Built This"),
    MarketItem("All-In", "Chamath, Jason, Sacks, Friedberg", "Podcast", "All-In Podcast"),
    MarketItem("Not Boring", "Packy McCormick", "Substack", "Not Boring Substack"),
    MarketItem("Pragmatic Engineer", "Gergely Orosz", "Substack", "Pragmatic Engineer Substack")
)
private val recommendedItems = listOf(
    MarketItem("Founders", "David Senra", "Podcast", "Founders Podcast"),
    MarketItem("Infinite Loops", "Jim O'Shaughnessy", "Podcast", "Infinite Loops Podcast"),
    MarketItem("Capital FM Kenya", "Capital FM", "Podcast", "Capital FM Kenya"),
    MarketItem("Naval", "Naval Ravikant", "Podcast", "Naval Podcast"),
    MarketItem("Acquired", "Ben & David", "Podcast", "Acquired Podcast")
)

@Composable
fun GlassMarketplace(ops: Map<String, AsyncOp>, onSubscribe: (String) -> Unit) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val items = when (selectedTab) { 0 -> kenyaItems; 1 -> usaItems; else -> recommendedItems }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, contentColor = Color.White, indicator = { positions ->
            TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[selectedTab]), color = Color.Cyan)
        }) {
            Tab(selectedTab == 0, { selectedTab = 0 }, text = { Text("🇰🇪 Kenya") })
            Tab(selectedTab == 1, { selectedTab = 1 }, text = { Text("🇺🇸 USA") })
            Tab(selectedTab == 2, { selectedTab = 2 }, text = { Text("⭐ Recs") })
        }
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(items, key = { it.query }) { item ->
                PrismaticGlass(Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(min = 80.dp), RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            NebulaText(item.title, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text("${item.type} • ${item.author}", color = Color.White.copy(.6f), style = MaterialTheme.typography.bodySmall)
                            (ops[item.query] as? AsyncOp.Failed)?.let { Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                        }
                        val op = ops[item.query] ?: AsyncOp.Idle
                        IconButton(onClick = { onSubscribe(item.query) }, enabled = op !is AsyncOp.InFlight, modifier = Modifier.size(48.dp)) {
                            when (op) {
                            AsyncOp.InFlight, is AsyncOp.Progress -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                                AsyncOp.Done -> Icon(Icons.Rounded.CheckCircle, "Subscribed to ${item.title}", tint = Color(0xFF00E676))
                                is AsyncOp.Failed -> Icon(Icons.Rounded.ErrorOutline, "Retry subscribing to ${item.title}", tint = MaterialTheme.colorScheme.error)
                                AsyncOp.Idle -> Icon(Icons.Rounded.AddCircle, "Subscribe to ${item.title}", tint = Color.Cyan)
                            }
                        }
                    }
                }
            }
        }
    }
}
