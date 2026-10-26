package id.pemilah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.pemilah.Palette
import id.pemilah.R

/** Nada status; warna tidak pernah menjadi satu-satunya pembeda (Design.md §2). */
enum class StatusTone { SUCCESS, WARNING, DANGER, MUTED, ACCENT, NEUTRAL }

internal fun StatusTone.color(): Color = when (this) {
    StatusTone.SUCCESS -> Palette.success
    StatusTone.WARNING -> Palette.warning
    StatusTone.DANGER -> Palette.danger
    StatusTone.MUTED -> Palette.onSurfaceMuted
    StatusTone.ACCENT -> Palette.accent
    StatusTone.NEUTRAL -> Palette.neutralClass
}

/**
 * Ikon status sederhana (bentuk + warna). Setiap status wajib disertai teks;
 * ikon ini pendamping visual, bukan pembawa makna tunggal (Design.md §7).
 */
@Composable
fun StatusIcon(tone: StatusTone, modifier: Modifier = Modifier) {
    val shape = when (tone) {
        StatusTone.DANGER -> RoundedCornerShape(2.dp)
        StatusTone.WARNING -> RoundedCornerShape(0.dp)
        else -> CircleShape
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .background(tone.color(), shape),
    )
}

/** Pasangan ikon + label untuk status, sesuai `statusEncoding` D-UI-01. */
@Composable
fun StatusChip(
    label: String,
    value: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusIcon(tone)
        Text(
            text = label,
            color = Palette.onSurfaceMuted,
            fontSize = 12.sp,
        )
        Text(
            text = value,
            color = if (tone == StatusTone.MUTED) Palette.onSurfaceMuted else tone.color(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Kartu ringkas status header: satu permukaan membulat yang mengelompokkan
 * beberapa [StatusInfoField]. Menggantikan deretan chip sebaris agar label dan
 * nilai tidak lagi bercampur dalam satu baris teks (`OperationScreen.xml`
 * `statusHeader`).
 */
@Composable
fun StatusInfoCard(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .background(Palette.surfaceRaised, RoundedCornerShape(10.dp))
            .border(1.dp, Palette.onSurfaceMuted.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * Satu kolom status di dalam [StatusInfoCard]: ikon + label di baris atas, nilai
 * di baris bawah. Label di atas nilai membuat makna tiap status terbaca tanpa
 * bergantung pada warna atau posisi (Design.md §7).
 */
@Composable
fun StatusInfoField(
    label: String,
    value: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.statusDescription("$label: $value"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusIcon(tone)
            Text(
                text = label,
                color = Palette.onSurfaceMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = value,
            color = if (tone == StatusTone.MUTED) Palette.onSurfaceMuted else tone.color(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Pemisah vertikal tipis antar kolom status di dalam [StatusInfoCard]. */
@Composable
fun StatusInfoDivider(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .width(1.dp)
            .height(28.dp)
            .background(Palette.onSurfaceMuted.copy(alpha = 0.16f)),
    )
}

/** Kartu panel mesin: judul + isi, sudut membulat dengan pemisah tipis. */
@Composable
fun MachinePanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.surfaceRaised, RoundedCornerShape(12.dp))
            .border(1.dp, Palette.surfaceRaised.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            color = Palette.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

/** Baris label/nilai pada panel; nilai kosong ditulis sebagai teks kosong eksplisit. */
@Composable
fun PanelField(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = Palette.onSurfaceMuted,
            fontSize = 12.sp,
            modifier = Modifier.widthIn(min = 84.dp),
        )
        Text(
            text = value,
            color = Palette.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Teks keadaan jujur: "Belum ada ..." / "Belum terintegrasi ..." */
@Composable
fun PlaceholderText(text: String, modifier: Modifier = Modifier, tone: StatusTone = StatusTone.MUTED) {
    Text(
        text = text,
        color = if (tone == StatusTone.MUTED) Palette.onSurfaceMuted else tone.color(),
        fontSize = 12.sp,
        modifier = modifier,
    )
}

/** Angka ringkasan sesi: 32 sp / 700 (Design.md §3). */
@Composable
fun SummaryMetric(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = Palette.onSurfaceMuted,
            fontSize = 12.sp,
        )
        StatusIcon(tone)
    }
}

@Composable
fun SecondaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (enabled) Palette.accent else Palette.onSurfaceMuted,
        ),
    ) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: StatusTone = StatusTone.ACCENT,
) {
    val container = when (tone) {
        StatusTone.DANGER -> Color.Transparent
        else -> Palette.accent
    }
    if (tone == StatusTone.DANGER) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (enabled) Palette.danger else Palette.onSurfaceMuted,
            ),
        ) {
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        return
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container),
    ) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Pemisah tipis antar blok di dalam kartu. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Palette.onSurfaceMuted.copy(alpha = 0.16f)),
    )
}

internal fun Modifier.statusDescription(description: String): Modifier =
    this.semantics(mergeDescendants = true) { contentDescription = description }

internal val PanelGap: Dp = 12.dp
