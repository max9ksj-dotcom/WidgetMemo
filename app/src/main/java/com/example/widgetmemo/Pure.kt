package com.example.widgetmemo

/** ARGB 색에 불투명도(0~100%)를 곱한다. 색상(RGB)은 그대로 두므로 색상과 투명도를 따로 관리할 수 있다. */
fun withAlpha(argb: Int, pct: Int): Int {
    val a = (argb ushr 24) and 0xFF
    val na = a * pct.coerceIn(0, 100) / 100
    return (na shl 24) or (argb and 0x00FFFFFF)
}

/** 위젯 크기 구간 판정. 값이 0이면 크기를 모르는 상태이므로 보통 레이아웃을 쓴다. */
fun isCompactSize(widthDp: Int, heightDp: Int): Boolean =
    widthDp > 0 && heightDp > 0 && (heightDp < 120 || widthDp < 130)
