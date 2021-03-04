package com.accurascan.ocr.mrz.interfaces;

import com.accurascan.ocr.mrz.model.RecogResult;

public interface OcrCallback {

    /**
     * Call this method to set border frame which is used in center of the device.
     * position your country id card to the frame.
     * width and height are according to card ratio.
     *
     * @param width
     * @param height
     */
    void onUpdateLayout(int width, int height);

    /**
     * call this method after scan complete
     *
     * @param result is scanned card data
     *  result instance of {@link RecogResult}
     *
     */
    void onScannedComplete(RecogResult result);

    /**
     * To get update message for user interaction which is called continuously
     * @param titleCode to display scan card message ontop of border Frame
     *
     * @param errorMessage to display process message.
     *                is null if message is not available
     * @param isFlip to set your customize animation after complete front scan
     *               and then scan back side. true if front and back side available in cards.
     */
    void onProcessUpdate(int titleCode, String errorMessage, boolean isFlip);

    /**
     * call this method if error on getting data from sdk
     * @param errorMessage
     */
    void onError(String errorMessage);

}