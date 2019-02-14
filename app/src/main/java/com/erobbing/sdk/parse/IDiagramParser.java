package com.erobbing.sdk.parse;

/**
 * Created by quhuabo on 2017/10/17 0017.
 */

public interface IDiagramParser {
    void parse(byte[] ABuffer) throws ParseException;

    void setListener(ParserListener mListener);

    void setSyncListener(ParserListener syncListener);
}
