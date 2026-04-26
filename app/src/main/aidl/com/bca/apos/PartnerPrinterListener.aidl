// PartnerPrinterListener.aidl
package com.bca.apos;

interface PartnerPrinterListener {
    void onFinish();

    /**
     * @param error - the error code
            ERROR_BMBLACK(248),
            ERROR_BUFOVERFLOW(245),
            ERROR_BUSY(247),
            ERROR_COMMERR(229),
            ERROR_CUTPOSITIONERR(226),
            ERROR_HARDERR(242),
            ERROR_LIFTHEAD(224),
            ERROR_LOWTEMP(227),
            ERROR_LOWVOL(225),
            ERROR_MOTORERR(251),
            ERROR_NOBM(246),
            ERROR_NOT_INIT(40961),
            ERROR_OVERHEAT(243),
            ERROR_PAPERENDED(240),
            ERROR_PAPERENDING(244),
            ERROR_PAPERJAM(238),
            ERROR_PARAM(40962),
            ERROR_PENOFOUND(252),
            ERROR_WORKON(230),
            UNKNOWN_ERROR(-1);
     */

    void onError(int code, String message);
}