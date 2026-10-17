package com.example.widgetmemo

/** 홈 화면 위젯 목록에 소형/대형이 따로 보이도록 하는 진입점. 동작은 중형과 동일하며 크기는 manifest 의 설정 파일이 다르다. */
class MemoWidgetSmallProvider : MemoWidgetProvider()

class MemoWidgetLargeProvider : MemoWidgetProvider()
