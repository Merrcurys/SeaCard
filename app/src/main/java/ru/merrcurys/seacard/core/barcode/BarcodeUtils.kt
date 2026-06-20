package ru.merrcurys.seacard.core.barcode

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.datamatrix.encoder.SymbolShapeHint
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.nio.charset.Charset

data class BarcodeTypeOption(val key: String, val label: String)

val BARCODE_TYPE_OPTIONS = listOf(
    BarcodeTypeOption("none", "Нет штрих-кода"),
    BarcodeTypeOption("aztec", "Aztec"),
    BarcodeTypeOption("code39", "Code 39"),
    BarcodeTypeOption("code93", "Code 93"),
    BarcodeTypeOption("code128", "Code 128"),
    BarcodeTypeOption("codabar", "Codabar"),
    BarcodeTypeOption("datamatrix", "Data Matrix"),
    BarcodeTypeOption("ean8", "EAN 8"),
    BarcodeTypeOption("ean13", "EAN 13"),
    BarcodeTypeOption("itf", "ITF"),
    BarcodeTypeOption("pdf417", "PDF 417"),
    BarcodeTypeOption("qr", "QR Code"),
    BarcodeTypeOption("upca", "UPC A"),
    BarcodeTypeOption("upce", "UPC E")
)

val BARCODE_ENCODINGS = listOf("ISO-8859-1", "UTF-8")

/** Типы штрих-кодов, для которых кодировка не влияет на результат. */
val ENCODING_INDEPENDENT_BARCODE_TYPE_KEYS = setOf(
    "code39", "code93", "codabar", "itf", "ean8", "ean13", "upca", "upce"
)

fun isEncodingIndependentBarcodeType(type: String): Boolean =
    type.lowercase() in ENCODING_INDEPENDENT_BARCODE_TYPE_KEYS

fun effectiveBarcodeEncoding(type: String, encoding: String): String =
    if (isEncodingIndependentBarcodeType(type)) "UTF-8" else encoding

/** Типы штрих-кодов для экрана ручного выбора (без «Нет штрих-кода»). */
val MANUAL_BARCODE_PREVIEW_TYPES = BARCODE_TYPE_OPTIONS.filter { it.key != "none" }

val MANUAL_BARCODE_ENCODING_DEPENDENT_TYPES =
    MANUAL_BARCODE_PREVIEW_TYPES.filter { !isEncodingIndependentBarcodeType(it.key) }

val MANUAL_BARCODE_ENCODING_INDEPENDENT_TYPES =
    MANUAL_BARCODE_PREVIEW_TYPES.filter { isEncodingIndependentBarcodeType(it.key) }

fun barcodePreviewCacheKey(typeKey: String, encoding: String): String = "$typeKey|$encoding"

fun barcodeIndependentPreviewCacheKey(typeKey: String): String = "${typeKey}|single"

/** Можно ли сгенерировать предпросмотр для данного номера, типа и кодировки. */
fun canGenerateBarcodePreview(code: String, type: String, encoding: String): Boolean {
    if (code.isBlank() || type == "none") return false
    if (!isEncodableInCharset(code, encoding)) return false
    return generateBarcodePreviewBitmap(code, type, encoding) != null
}

fun barcodeTypeLabel(key: String): String =
    BARCODE_TYPE_OPTIONS.firstOrNull { it.key == key }?.label ?: key

fun isEncodableInCharset(text: String, encoding: String): Boolean {
    if (encoding == "UTF-8") return true
    return try {
        val charset = Charset.forName(encoding)
        charset.newEncoder().canEncode(text)
    } catch (_: Exception) {
        false
    }
}

fun isValidBarcodeWithChecksum(code: String, codeType: String): Boolean {
    fun ean8Checksum(s: String): Int {
        val sum = s.take(7).mapIndexed { i, c ->
            val n = c.digitToInt()
            if (i % 2 == 0) n * 3 else n
        }.sum()
        return (10 - (sum % 10)) % 10
    }
    fun ean13Checksum(s: String): Int {
        val sum = s.take(12).mapIndexed { i, c ->
            val n = c.digitToInt()
            if (i % 2 == 0) n else n * 3
        }.sum()
        return (10 - (sum % 10)) % 10
    }
    fun upcaChecksum(s: String): Int {
        val sum = s.take(11).mapIndexed { i, c ->
            val n = c.digitToInt()
            if (i % 2 == 0) n * 3 else n
        }.sum()
        return (10 - (sum % 10)) % 10
    }
    return when (codeType.lowercase()) {
        "ean8" -> code.length == 8 && code.all { it.isDigit() } && code.last().digitToInt() == ean8Checksum(code)
        "ean13" -> code.length == 13 && code.all { it.isDigit() } && code.last().digitToInt() == ean13Checksum(code)
        "upca" -> code.length == 12 && code.all { it.isDigit() } && code.last().digitToInt() == upcaChecksum(code)
        else -> true
    }
}

/** Возвращает текст ошибки валидации или null, если код допустим. */
fun validateBarcodeCode(code: String, type: String, encoding: String): String? {
    if (type == "none") return null
    if (code.isBlank()) return "Введите номер карты"
    if (!isEncodableInCharset(code, encoding)) {
        return "Номер нельзя закодировать в $encoding"
    }
    return when (type) {
        "ean13" -> when {
            !code.all { it.isDigit() } -> "EAN 13: только цифры"
            code.length != 13 -> "EAN 13: 13 цифр"
            !isValidBarcodeWithChecksum(code, type) -> "EAN 13: неверная контрольная сумма"
            else -> null
        }
        "upca" -> when {
            !code.all { it.isDigit() } -> "UPC A: только цифры"
            code.length != 12 -> "UPC A: 12 цифр"
            !isValidBarcodeWithChecksum(code, type) -> "UPC A: неверная контрольная сумма"
            else -> null
        }
        "ean8" -> when {
            !code.all { it.isDigit() } -> "EAN 8: только цифры"
            code.length != 8 -> "EAN 8: 8 цифр"
            !isValidBarcodeWithChecksum(code, type) -> "EAN 8: неверная контрольная сумма"
            else -> null
        }
        "upce" -> when {
            !code.all { it.isDigit() } -> "UPC E: только цифры"
            code.length != 8 -> "UPC E: 8 цифр"
            else -> tryGenerateError(code, type, encoding)
        }
        "code39" -> if (!code.all { it.isUpperCase() || it.isDigit() || it in " -.$/+%" }) {
            "Code 39: латиница, цифры, пробел и - . $ / + %"
        } else tryGenerateError(code, type, encoding)
        "code93" -> if (!code.all { it.code in 0..127 }) {
            "Code 93: допустимы только ASCII-символы"
        } else tryGenerateError(code, type, encoding)
        "codabar" -> if (!code.matches(Regex("[A-Da-d][0-9\\-$:/.+]+[A-Da-d]"))) {
            "Codabar: начинается и заканчивается A–D, внутри цифры и - $ : / . +"
        } else tryGenerateError(code, type, encoding)
        "itf" -> when {
            !code.all { it.isDigit() } -> "ITF: только цифры"
            code.length % 2 != 0 -> "ITF: чётное количество цифр"
            else -> tryGenerateError(code, type, encoding)
        }
        "code128", "qr", "pdf417", "datamatrix", "aztec" -> tryGenerateError(code, type, encoding)
        else -> tryGenerateError(code, type, encoding)
    }
}

private fun tryGenerateError(code: String, type: String, encoding: String): String? =
    if (generateBarcodeBitmap(code, type, encoding) == null) {
        "Невозможно создать штрих-код с этими параметрами"
    } else {
        null
    }

fun generateBarcodeBitmap(content: String, codeType: String, encoding: String = "UTF-8"): Bitmap? {
    if (codeType == "none" || content.isBlank()) return null
    return if (codeType == "qr") {
        generateQRCode(content, encoding, 600, 600)
    } else {
        generateLinearOr2DBarcode(content, codeType, encoding, fullSize = true)
    }
}

/** Компактный bitmap для экрана выбора штрих-кода (меньше нагрузка на UI). */
fun generateBarcodePreviewBitmap(content: String, codeType: String, encoding: String): Bitmap? {
    if (codeType == "none" || content.isBlank()) return null
    return if (codeType == "qr") {
        generateQRCode(content, encoding, 180, 180)
    } else {
        generateLinearOr2DBarcode(content, codeType, encoding, fullSize = false)
    }
}

private fun generateQRCode(content: String, encoding: String, width: Int, height: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 0
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
        hints[EncodeHintType.CHARACTER_SET] = encoding
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints)
        bitMatrixToBitmap(bitMatrix)
    } catch (_: Exception) {
        null
    }
}

private fun generateLinearOr2DBarcode(
    content: String,
    codeType: String,
    encoding: String,
    fullSize: Boolean
): Bitmap? {
    return try {
        val writer = when (codeType.lowercase()) {
            "ean13" -> com.google.zxing.oned.EAN13Writer()
            "upca" -> com.google.zxing.oned.UPCAWriter()
            "code128" -> com.google.zxing.oned.Code128Writer()
            "code39" -> com.google.zxing.oned.Code39Writer()
            "code93" -> com.google.zxing.oned.Code93Writer()
            "codabar" -> com.google.zxing.oned.CodaBarWriter()
            "ean8" -> com.google.zxing.oned.EAN8Writer()
            "itf" -> com.google.zxing.oned.ITFWriter()
            "upce" -> com.google.zxing.oned.UPCEWriter()
            "datamatrix" -> com.google.zxing.datamatrix.DataMatrixWriter()
            "pdf417" -> com.google.zxing.pdf417.PDF417Writer()
            "aztec" -> com.google.zxing.aztec.AztecWriter()
            else -> com.google.zxing.oned.Code128Writer()
        }
        val format = when (codeType.lowercase()) {
            "ean13" -> BarcodeFormat.EAN_13
            "upca" -> BarcodeFormat.UPC_A
            "code128" -> BarcodeFormat.CODE_128
            "code39" -> BarcodeFormat.CODE_39
            "code93" -> BarcodeFormat.CODE_93
            "codabar" -> BarcodeFormat.CODABAR
            "ean8" -> BarcodeFormat.EAN_8
            "itf" -> BarcodeFormat.ITF
            "upce" -> BarcodeFormat.UPC_E
            "datamatrix" -> BarcodeFormat.DATA_MATRIX
            "pdf417" -> BarcodeFormat.PDF_417
            "aztec" -> BarcodeFormat.AZTEC
            else -> BarcodeFormat.CODE_128
        }
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 0
        hints[EncodeHintType.CHARACTER_SET] = encoding
        if (codeType.lowercase() == "datamatrix") {
            hints[EncodeHintType.DATA_MATRIX_SHAPE] = SymbolShapeHint.FORCE_SQUARE
        }
        val isSquare = codeType.lowercase() in listOf("datamatrix", "aztec", "pdf417")
        val encodeWidth = when {
            fullSize && isSquare -> 600
            fullSize -> 800
            isSquare -> 180
            else -> 320
        }
        val encodeHeight = when {
            fullSize && isSquare -> 600
            fullSize -> 200
            isSquare -> 180
            else -> 100
        }
        val bitMatrix = writer.encode(
            content,
            format,
            encodeWidth,
            encodeHeight,
            hints
        )
        bitMatrixToBitmap(bitMatrix)
    } catch (_: Exception) {
        null
    }
}

private fun bitMatrixToBitmap(bitMatrix: BitMatrix): Bitmap {
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = createBitmap(width, height)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap[x, y] = if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    return bitmap
}

fun formatBarcodeForStandard(code: String, codeType: String): String {
    return when (codeType.lowercase()) {
        "ean13" -> code.chunked(1)
            .let { if (it.size >= 13) it[0] + " " + it.subList(1, 7).joinToString("") + " " + it.subList(7, 13).joinToString("") else code }
        "upca" -> code.chunked(1)
            .let { if (it.size >= 12) it[0] + " " + it.subList(1, 6).joinToString("") + " " + it.subList(6, 11).joinToString("") + " " + it[11] else code }
        "ean8" -> code.chunked(1)
            .let { if (it.size >= 8) it.subList(0, 4).joinToString("") + " " + it.subList(4, 8).joinToString("") else code }
        else -> code
    }
}
