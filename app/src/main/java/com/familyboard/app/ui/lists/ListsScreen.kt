package com.familyboard.app.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyboard.app.data.model.BoardType
import com.familyboard.app.ui.AppViewModel
import com.familyboard.app.ui.theme.ShoppingBlue
import com.familyboard.app.ui.theme.TodoGreen

@Composable
fun ListsScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    onOpenBoard: (String) -> Unit,
) {
    val shopping by vm.shoppingItems.collectAsStateWithLifecycle()
    val todo by vm.todoItems.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
    ) {
        Text("리스트", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BoardCard(
                title = BoardType.SHOPPING.title,
                count = shopping.size,
                done = shopping.count { it.checked },
                color = ShoppingBlue,
                icon = Icons.Default.ShoppingCart,
                modifier = Modifier.weight(1f),
            ) { onOpenBoard(BoardType.SHOPPING.key) }
            BoardCard(
                title = BoardType.TODO.title,
                count = todo.size,
                done = todo.count { it.checked },
                color = TodoGreen,
                icon = Icons.Default.CheckCircle,
                modifier = Modifier.weight(1f),
            ) { onOpenBoard(BoardType.TODO.key) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardCard(
    title: String,
    count: Int,
    done: Int,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(0.95f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                "${count}개 항목" + if (count > 0) " · $done 완료" else "",
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                Icon(icon, null, tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.height(64.dp))
            }
        }
    }
}
