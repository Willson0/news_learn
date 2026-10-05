package app.karta.likvidnosti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import app.karta.likvidnosti.data.Session
import app.karta.likvidnosti.ui.components.Screen
import app.karta.likvidnosti.ui.theme.T

@Composable
fun ProfileEditScreen(onDone: () -> Unit) {
    var name by remember { mutableStateOf(Session.user?.name ?: "Артем") }
    var tag by remember { mutableStateOf(Session.user?.username ?: "@Artemis") }

    Screen(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .clip(T.ShapePill)
                    .background(T.SurfaceMuted)
                    .border(1.dp, Color(0x26FFFFFF), T.ShapePill)
                    .clickable { onDone() }
                    .padding(horizontal = 22.dp, vertical = 11.dp),
            ) { Text("Готово", color = T.Text, fontSize = T.FsBase) }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(modifier = Modifier.size(148.dp).clip(CircleShape).background(Color.White))
            Text("Изменить фотографию", color = T.Text, fontSize = T.FsMd, modifier = Modifier.clickable { })
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledInput("Имя", name) { name = it }
            LabeledInput("Тег", tag) { tag = it }
        }
    }
}

@Composable
fun LabeledInput(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(T.ShapeInput)
            .background(T.SurfaceMuted)
            .border(1.dp, Color(0x14FFFFFF), T.ShapeInput)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = T.TextSecondary, fontSize = T.FsSm)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = T.Text, fontSize = T.FsMd),
            cursorBrush = SolidColor(T.Text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
