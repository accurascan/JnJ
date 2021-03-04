package com.docrecog.scan;

import android.content.Intent;

public enum MRZDocumentType {
    NONE(0), PASSPORT_MRZ(1), ID_CARD_MRZ(2), VISA_MRZ(3);

    private static final String name = MRZDocumentType.class.getName();
    public final int value;

    MRZDocumentType(int i) {
        this.value = i;
    }

    public void attachTo(Intent intent) {
        intent.putExtra(name, ordinal());
    }

    public static MRZDocumentType detachFrom(Intent intent) {
        if (!intent.hasExtra(name)) throw new IllegalStateException();
        return values()[intent.getIntExtra(name, -1)];
    }
}
