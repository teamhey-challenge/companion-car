package ai.hey.designsystem.gallery

internal object GalleryData {
    const val DESTINATION_KO = "인천국제공항 제2여객터미널 3층 출국장"
    const val ADDRESS_KO = "인천광역시 중구 제2터미널대로 446 (운서동)"
    const val NAME_LOCAL = "Incheon International Airport Terminal 2"
    const val LONG_ADDRESS_KO =
        "서울특별시 영등포구 국제금융로 10 서울국제금융센터 지하 3층 주차장 B구역 동쪽 엘리베이터 홀 앞 승강장"
    const val LONG_KO =
        "승객이 짐이 많아서 입구 바로 앞에 세워 달라고 했어요. 건물 정면은 정차가 어려우니 옆 출입구 쪽으로 이동할 수 있는지 확인해 주세요."

    const val LONG_JA =
        "羽田空港第三ターミナル国際線到着ロビーから新宿区西新宿にあるホテルまでお願いします。荷物が多いのでできるだけ入口の近くで降ろしていただけると助かります。" +
            "エレベーターの近くだとさらにありがたいです。支払いはクレジットカードでお願いしたいのですが、使えますか。領収書もいただけると嬉しいです。"
    const val LONG_ZH =
        "请把我们从仁川国际机场第二航站楼送到首尔市中区的酒店，行李比较多，所以希望能尽量停在入口附近，最好是电梯旁边的位置，另外我想用信用卡付款，" +
            "请问可以吗，下车时也需要一张收据，谢谢您的帮助，辛苦了，非常感谢您。"

    val reasons = listOf("정면은 정차가 어려워요", "옆 입구가 더 가까워요", "짐 내리기 편해요", "주차장 입구예요", "건너편이 더 안전해요", "공사 중이에요")
    val greetings = listOf(
        "QR을 찍어 주세요",
        "Scan to ride with HEY",
        "QRコードを読み取ってください",
        "请扫描二维码",
    )
    const val QR_URL = "https://hey.example/c/K7M3Q9XD"
}
