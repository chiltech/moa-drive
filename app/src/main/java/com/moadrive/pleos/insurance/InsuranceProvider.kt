package com.moadrive.pleos.insurance

/** Official public insurance pages; no partner API or contract lookup. */
enum class InsuranceProvider(
    val title: String, val phone: String, val mobileUrl: String, val temporaryDriverUrl: String,
) {
    SAMSUNG("삼성화재", "1588-5114", "https://mdirect.samsungfire.com/app/index.html",
        "https://direct.samsungfire.com/ria/fw/index.html?state=Front&product=endo&endotypecd=E10"),
    HYUNDAI("현대해상", "1588-5656", "https://mplatform.hi.co.kr/",
        "https://www.hi.co.kr/serviceAction.do?menuId=100212"),
    DB("DB손해보험", "1588-0100", "https://m.idbins.com/FMCLAV5412.do",
        "https://idbins.com/FMMYCV0104.do"),
    KB("KB손해보험", "1544-0114", "https://m.kbinsure.co.kr/", "https://m.kbinsure.co.kr/"),
    MERITZ("메리츠화재", "1566-7711", "https://mstore.meritzfire.com/", "https://mstore.meritzfire.com/"),
}

enum class RoadsideService(val title: String, val description: String) {
    ROADSIDE("긴급출동", "차량 고장 도움"),
    BATTERY("배터리 지원", "방전·시동 도움"),
    TOW("견인 서비스", "운행 불가 차량 이동"),
    TIRE("타이어 수리", "펑크 수리·교체 도움"),
}
