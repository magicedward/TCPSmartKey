package com.erobbing.sdk.parse;

import com.erobbing.sdk.model.DiagControl;

public interface ParserListener {
    void onGotPackage(final DiagControl dc);
}
