package com.example.alakey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class MarketItem(val title: String, val author: String, val type: String, val query: String)

@Composable
fun GlassMarketplace(
    onSubscribe: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Kenya, 1 = USA
    var subscribingQuery by remember { mutableStateOf<String?>(null) }
    val subscribedQueries = remember { mutableStateListOf<String>() }
    
    val kenyaItems = listOf(
        MarketItem("The 97s Podcast", "3MenArmy", "Podcast", "The 97s Podcast"),
        MarketItem("So This Is Love", "Julia Gaitho", "Podcast", "So This Is Love Julia Gaitho"),
        MarketItem("The Mkurugenzi Podcast", "Abel Mutua", "Podcast", "The Mkurugenzi Podcast"),
        MarketItem("Mic Cheque Podcast", "Chaxy, Mariah, Mwass", "Podcast", "Mic Cheque Podcast"),
        MarketItem("The Messy Inbetween", "TMI", "Podcast", "The Messy Inbetween"),
        MarketItem("It's Related, I Promise", "IRIP", "Podcast", "It's Related I Promise"),
        MarketItem("The Kenyan Bookmaker", "Michelle C. Korir", "Substack", "The Kenyan Bookmaker Substack"),
        MarketItem("Built In Kenya", "Dan Mbure", "Substack", "Built In Kenya Substack")
    )

    val usaItems = listOf(
        MarketItem("Pivot", "Kara Swisher & Scott Galloway", "Podcast", "Pivot Podcast"),
        MarketItem("a16z Podcast", "Andreessen Horowitz", "Podcast", "a16z Podcast"),
        MarketItem("How I Built This", "Guy Raz", "Podcast", "How I Built This"),
        MarketItem("All-In", "Chamath, Jason, Sacks, Friedberg", "Podcast", "All-In Podcast"),
        MarketItem("Not Boring", "Packy McCormick", "Substack", "Not Boring Substack"),
        MarketItem("Pragmatic Engineer", "Gergely Orosz", "Substack", "Pragmatic Engineer Substack"),
        MarketItem("Lenny's Newsletter", "Lenny Rachitsky", "Substack", "Lenny's Newsletter")
    )

    val recommendedItems = listOf(
        MarketItem("Founders", "David Senra", "Podcast", "Founders Podcast"),
        MarketItem("Infinite Loops", "Jim O'Shaughnessy", "Podcast", "Infinite Loops Podcast"),
        MarketItem("Capital FM Kenya", "Capital FM", "Podcast", "Capital FM Kenya"),
        MarketItem("We Are Not Saved", "Jeremiah Johnson", "Podcast", "We Are Not Saved Podcast"),
        MarketItem("Astral Codex Ten", "Scott Alexander", "Podcast", "Astral Codex Ten Podcast"),
        MarketItem("Berkshire Hathaway", "Buffett & Munger", "Podcast", "Berkshire Hathaway Shareholder Meetings"),
        MarketItem("Naval", "Naval Ravikant", "Podcast", "Naval Podcast"),
        MarketItem("InvestED", "Phil & Danielle Town", "Podcast", "InvestED Podcast"),
        MarketItem("Acquired", "Ben & David", "Podcast", "Acquired Podcast"),
        MarketItem("My First Million", "Shaan & Sam", "Podcast", "My First Million Podcast"),
        MarketItem("Westenberg", "Westenberg", "Podcast", "Westenberg Podcast")
    )

    val currentItems = when (selectedTab) {
        0 -> kenyaItems
        1 -> usaItems
        else -> recommendedItems
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Color.Cyan
                )
            }
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("🇰🇪 Kenya") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("🇺🇸 USA") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("⭐ Recs") })
        }
        
        Spacer(Modifier.height(16.dp))
        
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(currentItems) { item ->
                PrismaticGlass(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).height(80.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            NebulaText(item.title, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${item.type} • ${item.author}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.6f)
                            )
                        }
                        val isSyncing = subscribingQuery == item.query
                        val isSubscribed = item.query in subscribedQueries
                        IconButton(onClick = {
                            subscribingQuery = item.query
                            subscribedQueries.add(item.query)
                            onSubscribe(item.query)
                        }) {
                            when {
                                isSyncing -> CircularProgressIndicator(color = Color.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                                isSubscribed -> Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF00E676))
                                else -> Icon(Icons.Rounded.AddCircle, null, tint = Color.Cyan)
                            }
                        }
                    }
                }
            }
        }
    }
}
