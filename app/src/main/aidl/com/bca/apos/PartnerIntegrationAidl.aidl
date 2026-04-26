// PartnerIntegrationAidl.aidl
package com.bca.apos;

import com.bca.apos.PartnerInquiryData;
import com.bca.apos.PartnerPrinterListener;
import com.bca.apos.NonBcaTransactionData;
import com.bca.apos.TerminalData;
import com.bca.apos.PartnerSettlementData;

interface PartnerIntegrationAidl {
    int getAposState(String featureType);
    @nullable PartnerInquiryData inquiry(String partnerRefId, String flag);
    void startPrint(String contentHtml, PartnerPrinterListener printerListener);
    @nullable String getSN();
    TerminalData getTerminalData();
    @nullable String getSettlementId();
    @nullable PartnerSettlementData inquirySettlement(String settlementId);
    String recordNonBcaTransaction(in NonBcaTransactionData transactionData);
}