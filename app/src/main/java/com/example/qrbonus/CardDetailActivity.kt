package com.example.qrbonus

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qrbonus.ui.theme.QRBonusTheme
import com.example.qrbonus.ui.theme.BlackBackground
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.oned.Code128Writer
import androidx.core.graphics.set
import androidx.core.graphics.createBitmap

class CardDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val cardName = intent.getStringExtra("card_name") ?: ""
        val cardCode = intent.getStringExtra("card_code") ?: ""
        
        setContent {
            var showDeleteDialog by remember { mutableStateOf(false) }
            
            QRBonusTheme(darkTheme = true) {
                CardDetailScreen(
                    cardName = cardName,
                    cardCode = cardCode,
                    onBack = { finish() },
                    onDelete = { showDeleteDialog = true }
                )
                
                // Диалог подтверждения удаления
                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("Удалить карту?") },
                        text = { Text("Карта \"$cardName\" будет удалена безвозвратно.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    deleteCard(this@CardDetailActivity, cardName, cardCode)
                                    setResult(RESULT_OK)
                                    finish()
                                }
                            ) {
                                Text("Удалить", color = Color.Red)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text("Отмена")
                            }
                        },
                        containerColor = Color(0xFF2A2A2A),
                        titleContentColor = Color.White,
                        textContentColor = Color.White
                    )
                }
            }
        }
    }
    
    private fun deleteCard(context: android.content.Context, name: String, code: String) {
        val prefs = context.getSharedPreferences("cards", android.content.Context.MODE_PRIVATE)
        val cards = prefs.getStringSet("card_list", setOf())?.toMutableSet() ?: mutableSetOf()
        cards.remove("$name|$code")
        prefs.edit().putStringSet("card_list", cards).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    cardName: String,
    cardCode: String,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    var barcodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(cardCode) {
        barcodeBitmap = generateBarcode(cardCode)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackBackground)
    ) {
        Column {
            TopAppBar(
                title = { Text(cardName, color = Color.White, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить карту", tint = Color.Red)
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BlackBackground)
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Код карты",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Text(
                    text = cardCode,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                
                if (barcodeBitmap != null) {
                    Text(
                        text = "Штрихкод",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Image(
                        bitmap = barcodeBitmap!!.asImageBitmap(),
                        contentDescription = "Штрихкод",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

private fun generateBarcode(content: String): Bitmap? {
    return try {
        val writer = Code128Writer()
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 0
        
        val bitMatrix: BitMatrix = writer.encode(
            content,
            BarcodeFormat.CODE_128,
            800,
            200,
            hints
        )
        
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = createBitmap(width, height)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap[x, y] = if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        
        bitmap
    } catch (e: WriterException) {
        e.printStackTrace()
        null
    }
} 