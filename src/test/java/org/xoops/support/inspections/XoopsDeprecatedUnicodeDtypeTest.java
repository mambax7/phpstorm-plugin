package org.xoops.support.inspections;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class XoopsDeprecatedUnicodeDtypeTest {

    @Test
    public void successorMapCoversTheSixCoreAliases() {
        assertEquals(6, XoopsDeprecatedUnicodeDtypeInspection.SUCCESSOR.size());
        assertEquals("XOBJ_DTYPE_TXTBOX", XoopsDeprecatedUnicodeDtypeInspection.SUCCESSOR.get("XOBJ_DTYPE_UNICODE_TXTBOX"));
        assertEquals("XOBJ_DTYPE_TXTAREA", XoopsDeprecatedUnicodeDtypeInspection.SUCCESSOR.get("XOBJ_DTYPE_UNICODE_TXTAREA"));
        assertEquals("XOBJ_DTYPE_URL", XoopsDeprecatedUnicodeDtypeInspection.SUCCESSOR.get("XOBJ_DTYPE_UNICODE_URL"));
        assertEquals("XOBJ_DTYPE_EMAIL", XoopsDeprecatedUnicodeDtypeInspection.SUCCESSOR.get("XOBJ_DTYPE_UNICODE_EMAIL"));
        assertEquals("XOBJ_DTYPE_ARRAY", XoopsDeprecatedUnicodeDtypeInspection.SUCCESSOR.get("XOBJ_DTYPE_UNICODE_ARRAY"));
        assertEquals("XOBJ_DTYPE_OTHER", XoopsDeprecatedUnicodeDtypeInspection.SUCCESSOR.get("XOBJ_DTYPE_UNICODE_OTHER"));
    }
}
