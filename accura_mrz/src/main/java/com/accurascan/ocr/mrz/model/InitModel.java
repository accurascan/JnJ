package com.accurascan.ocr.mrz.model;

import androidx.annotation.Keep;

@Keep
public class InitModel {

    private InitData data;
    private Integer responseCode;
    private String responseMessage;

    public InitData getInitData() {
        return data;
    }

    public void setInitData(InitData data) {
        this.data = data;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(Integer responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }

    @Keep
    public class InitData {
        private Integer cameraHeight;
        private Integer cameraWidth;
        public float borderRatio;
        private String cardName;
        private Boolean isbothavailable;
        private String cardside = "";
        private Boolean isMRZEnable;

        public Integer getCameraHeight() {
            return cameraHeight;
        }

        public void setCameraHeight(Integer cameraHeight) {
            this.cameraHeight = cameraHeight;
        }

        public Integer getCameraWidth() {
            return cameraWidth;
        }

        public void setCameraWidth(Integer cameraWidth) {
            this.cameraWidth = cameraWidth;
        }

        public String getCardName() {
            return cardName;
        }

        public void setCardName(String cardName) {
            this.cardName = cardName;
        }

        public Boolean getIsbothavailable() {
            return isbothavailable;
        }

        public void setIsbothavailable(Boolean isbothavailable) {
            this.isbothavailable = isbothavailable;
        }

        public String getCardside() {
            return cardside;
        }

        public void setCardside(String cardside) {
            this.cardside = cardside;
        }

        public Boolean getMRZEnable() {
            return isMRZEnable;
        }

        public void setMRZEnable(Boolean MRZEnable) {
            isMRZEnable = MRZEnable;
        }
    }
}