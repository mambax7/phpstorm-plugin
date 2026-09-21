package org.xoops.support;

import org.junit.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class PluginDescriptorTest {

    @Test
    public void everyLocalInspectionHasADescriptionFile() throws Exception {
        try (InputStream descriptor = getClass().getResourceAsStream("/META-INF/plugin.xml")) {
            assertNotNull("plugin.xml must be on the test classpath", descriptor);
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(descriptor);
            var inspections = document.getElementsByTagName("localInspection");
            assertTrue("expected the XOOPS inspection pack", inspections.getLength() >= 8);
            for (int i = 0; i < inspections.getLength(); i++) {
                Element inspection = (Element) inspections.item(i);
                String shortName = inspection.getAttribute("shortName");
                assertFalse("shortName", shortName.isBlank());
                assertNotNull(
                        "missing inspectionDescriptions/" + shortName + ".html",
                        getClass().getResource("/inspectionDescriptions/" + shortName + ".html")
                );
            }
        }
    }
}
