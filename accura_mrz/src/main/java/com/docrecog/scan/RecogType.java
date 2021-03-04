package com.docrecog.scan;

import android.content.Intent;

enum RecogType {
        MRZ, NONE;

        private static final String recogType = RecogType.class.getName();

        public void attachTo(Intent intent) {
            intent.putExtra(recogType, ordinal());
        }

        public static RecogType detachFrom(Intent intent) {
            if (!intent.hasExtra(recogType)) throw new IllegalStateException();
            return values()[intent.getIntExtra(recogType, -1)];
        }
    }