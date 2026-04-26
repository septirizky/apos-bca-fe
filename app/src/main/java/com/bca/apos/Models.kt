package com.bca.apos

enum class SettlementStatus(val value: String) {
    CREATED("created"),
    SUCCESS("success"),
    PARTIAL_SUCCESS("partial_success"),
    NOT_FOUND("not_found"),
    UNKNOWN("unknown");

    companion object {
        fun from(value: String): SettlementStatus =
            values().firstOrNull { it.value == value } ?: UNKNOWN
    }
}

enum class TransactionStatus(val value: String) {
    CREATED("created"),
    FAILED("failed"),
    SUCCESS("success"),
    VOID("void"),
    REVERSAL("pending_success"),
    REVERSAL_VOID_SUCCESS("success_rv"),
    PENDING_VOID("pending_void"),
    PENDING("pending"),
    NOT_FOUND("not_found"),
    NOT_ALLOWED("not_allowed"),
    UNKNOWN("unknown");

    companion object {
        fun from(value: String): TransactionStatus =
            values().firstOrNull { it.value == value } ?: UNKNOWN
    }
}
enum class FeatureType(val uriSuffix: String, val title: String) {
    CARD("card", "Kartu"),
    QRIS("qris", "QRIS"),
    FLAZZ("flazz", "Flazz"),
    INSTALLMENT("installment", "Cicilan"),
    VOID("void", "Void"),
    VOID_V2("void-v2", "Void Version 2"),
    CARDVER("cardver", "Cardver"),
    OFFLINE("offline", "Offline"),
    REFUND("refund", "Refund"),
    QRIS_REFUND("qris-refund", "QRIS Refund"),
    ADJUSTMENT("adjustment", "Adjustment"),
    ADJUSTMENT_V2("adjustment-v2", "Adjustment Version 2"),
    SETTLEMENT("settlement", "Settlement"),
    SETTLEMENT_V2("settlement-v2", "Settlement Version 2"),
    HISTORY("history", "Riwayat Transaksi"),
    INQUIRY("inquiry", "Inquiry Transaksi"),
    INQUIRY_VOID("inquiry-void", "Inquiry Void"),

    NON_BCA_TRANSACTION("non_bca_transaction","Non BCA Transaction"),

    CUSTOM_TRANSACTION("custom_transaction","Custom Transaction"),
    MANUAL_INQUIRY("manual_inquiry","Manual Inquiry"),
    PRINT("print","Print"),
    GET_SN("get_sn","Get SN"),
    GET_SETTLEMENT_ID("get_settlement_id","Get Settlement ID"),
    GET_TERMINAL_DATA("get_terminal_data","Terminal Data"),
    GET_X_EXTERNAL_ID("get_x_external_id", "Get X-EXTERNAL-ID"),
    UNKNOWN("unknown", "Unknown");

    companion object{
        fun from(value: String?): FeatureType =
            values()
                .firstOrNull { value.equals(it.uriSuffix, true) } ?: UNKNOWN

    }
}

enum class InquiryFlag(val value: String) {
    SINGLE("SINGLE"),
    SINGLE_VOID("SINGLE_VOID"),
    TRANSACTION("TRANSACTION"),
    VOID("VOID"),
    ADJUSTMENT("ADJUSTMENT")
}